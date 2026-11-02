"""Run the production v9->v10 SQL against exported Room schemas and DAO queries."""
import json
import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/mioo/dao"
SCHEMAS = ROOT / "app/schemas/com.mioo.dao.data.local.AppDatabase"


def create_schema(connection, version):
    schema = json.loads((SCHEMAS / f"{version}.json").read_text())["database"]
    for entity in schema["entities"]:
        connection.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for index in entity.get("indices", []):
            connection.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    return schema


def queries(file):
    source = (SOURCE / f"data/local/{file}.kt").read_text(encoding="utf-8")
    result = {}
    for match in re.finditer(r'@Query\((""".*?"""|"[^"\n]*")\)', source, re.S):
        name = re.search(r"fun\s+(\w+)", source[match.end():]).group(1)
        result[name] = match.group(1).strip('"')
    return result


db = sqlite3.connect(":memory:")
create_schema(db, 9)
db.execute("INSERT INTO history(id,fid,content,userid,now,title,name,timestamp) VALUES('123','4','原串正文','PO','now','主串',NULL,100)")
db.execute("INSERT INTO thread_progress(threadId,page,firstVisibleIndex,scrollOffset,anchorPostId,updatedAt) VALUES('123',3,8,42,'456',100)")
module = (SOURCE / "di/DatabaseModule.kt").read_text(encoding="utf-8")
block = module.split("private val MIGRATION_9_10", 1)[1].split("@Provides", 1)[0]
for match in re.finditer(r'db\.execSQL\((?:"""(.*?)"""|"([^"\n]*)")\)', block, re.S):
    db.execute(match.group(1) or match.group(2))

expected = sqlite3.connect(":memory:")
schema = create_schema(expected, 10)
for entity in schema["entities"]:
    table = entity["tableName"]
    assert db.execute(f"PRAGMA table_info({table})").fetchall() == expected.execute(f"PRAGMA table_info({table})").fetchall(), table
    assert sorted(row[1:3] for row in db.execute(f"PRAGMA index_list({table})")) == sorted(row[1:3] for row in expected.execute(f"PRAGMA index_list({table})")), table

history = queries("HistoryDao")
actions = queries("PostActionHistoryDao")
db.execute(history["fillForumName"], {"fid": "4", "name": "综合版1"})
rows = db.execute(history["observeBrowsing"], {"query": "综合"}).fetchall()
assert len(rows) == 1 and rows[0][-1] == 3
assert db.execute("SELECT scrollOffset,anchorPostId FROM thread_progress").fetchone() == (42, "456")
for kind, content in [("REPLY", "我的回复关键字"), ("NEW_THREAD", "我的新串关键字")]:
    db.execute("""INSERT INTO post_action_history(id,type,threadId,forumId,forumName,cookieHash,threadSummary,content,timestamp)
        VALUES(?,?, '123','4','综合版1','Ki6deii','原串正文',?,200)""", (kind, kind, content))
assert len(db.execute(actions["observe"], {"type": "REPLY", "query": "回复关键字"}).fetchall()) == 1
assert not db.execute(actions["observe"], {"type": "NEW_THREAD", "query": "回复关键字"}).fetchall()
db.execute(actions["clear"], {"type": "REPLY"})
assert db.execute("SELECT count(*) FROM history").fetchone()[0] == 1
assert db.execute("SELECT type FROM post_action_history").fetchall() == [("NEW_THREAD",)]
db.execute(history["deleteHistoryById"], {"id": "123"})
assert db.execute("SELECT count(*) FROM post_action_history").fetchone()[0] == 1
db.execute(actions["delete"], {"id": "NEW_THREAD"})
db.execute(actions["resolve"], {"id": "NEW_THREAD", "threadId": "999", "cookieHash": "Ki6deii", "imageUrl": None})
assert db.execute("SELECT count(*) FROM post_action_history").fetchone()[0] == 0
print("PASS: v9->v10 preserves existing data and matches Room schema; search, read page, isolated clear/delete and late resolution verified.")
