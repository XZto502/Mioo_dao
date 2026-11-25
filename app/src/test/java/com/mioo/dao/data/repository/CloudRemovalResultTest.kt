package com.mioo.dao.data.repository

import com.mioo.dao.data.model.XdResponse
import org.junit.Assert.*
import org.junit.Test

class CloudRemovalResultTest {
    @Test fun onlyKnownWriteAcknowledgementsCountAsSuccess() {
        assertTrue(parseCloudFeedWrite("\"订阅大成功→_→\"", removing = false) is XdResponse.Success)
        assertTrue(parseCloudFeedWrite("取消订阅成功!", removing = true) is XdResponse.Success)
        for (body in listOf("", "<html>订阅成功</html>", "请求失败", "取消订阅成功!")) {
            assertTrue(parseCloudFeedWrite(body, removing = false) is XdResponse.Error)
        }
        assertNotNull((parseCloudFeedWrite("", removing = true) as XdResponse.Error).throwable)
        assertNull((parseCloudFeedWrite("该串不存在", removing = false) as XdResponse.Error).throwable)
    }
    @Test fun onlyExplicitMissingMembershipCountsAsAbsent() {
        assertTrue(isAbsentSubscription("您没有订阅该主题！"))
        assertTrue(isAbsentSubscription("未收藏"))
        assertFalse(isAbsentSubscription("没有订阅权限"))
        assertFalse(isAbsentSubscription("收藏夹不存在"))
        assertFalse(isAbsentSubscription("连接超时"))
    }

    @Test fun partialAndTotalFailureBothOfferRetry() {
        assertEquals("已从 1 个收藏夹移除，另有 1 个失败，可重试",
            CloudRemovalResult(1, 2, setOf("failed")).message())
        assertEquals("1 个收藏夹移除失败，可重试",
            CloudRemovalResult(0, 2, setOf("failed")).message())
        assertEquals("云端收藏夹中没有找到该串", CloudRemovalResult(0, 2, emptySet()).message())
    }
}
