package com.mioo.dao.data.repository

import com.mioo.dao.data.local.DraftEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries a POST draft from the 草稿箱 to the forum composer across screens.
 * Singleton: the forum tab's ViewModel may be recreated while the draft box
 * is on top, so the pending value must outlive it.
 */
@Singleton
class DraftPrefillBus @Inject constructor() {
    private val _pending = MutableStateFlow<DraftEntity?>(null)
    val pending: StateFlow<DraftEntity?> = _pending

    fun offer(draft: DraftEntity?) {
        _pending.value = draft
    }

    fun consume() {
        _pending.value = null
    }
}
