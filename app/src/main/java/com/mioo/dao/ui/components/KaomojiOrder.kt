package com.mioo.dao.ui.components

internal fun restoreKaomojiOrder(saved: List<String>, builtIn: List<String>): List<String> {
    val available = builtIn.toHashSet()
    return (saved.filter { it in available } + builtIn).distinct()
}

internal fun moveKaomoji(order: List<String>, from: String, to: String): List<String> {
    val source = order.indexOf(from)
    val target = order.indexOf(to)
    if (source < 0 || target < 0 || source == target) return order
    return order.toMutableList().apply { add(target, removeAt(source)) }
}
