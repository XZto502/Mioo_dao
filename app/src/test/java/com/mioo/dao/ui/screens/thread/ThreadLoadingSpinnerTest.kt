package com.mioo.dao.ui.screens.thread

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadLoadingSpinnerTest {

    @Test
    fun cachedThreadOpenKeepsSpinnerContinuouslyVisibleUntilReadingListReady() {
        // Initial enter before cache emits
        assertTrue(
            shouldShowThreadLoadingSpinner(
                retireListRemoved = false,
                hasThread = false,
                isLoading = true,
                hasError = false,
                readingListReady = false
            )
        )
        // Cache arrives while page enter transition is still settling (!allowHeavyList -> ready=false)
        assertTrue(
            shouldShowThreadLoadingSpinner(
                retireListRemoved = false,
                hasThread = true,
                isLoading = true,
                hasError = false,
                readingListReady = false
            )
        )
        // Viewport staging in progress after network completes
        assertTrue(
            shouldShowThreadLoadingSpinner(
                retireListRemoved = false,
                hasThread = true,
                isLoading = false,
                hasError = false,
                readingListReady = false
            )
        )
        // Content ready: spinner hides once
        assertFalse(
            shouldShowThreadLoadingSpinner(
                retireListRemoved = false,
                hasThread = true,
                isLoading = false,
                hasError = false,
                readingListReady = true
            )
        )
    }

    @Test
    fun networkThreadOpenKeepsSpinnerContinuousAcrossLoadingAndStaging() {
        assertTrue(
            shouldShowThreadLoadingSpinner(
                retireListRemoved = false,
                hasThread = false,
                isLoading = true,
                hasError = false,
                readingListReady = false
            )
        )
        assertTrue(
            shouldShowThreadLoadingSpinner(
                retireListRemoved = false,
                hasThread = true,
                isLoading = false,
                hasError = false,
                readingListReady = false
            )
        )
        assertFalse(
            shouldShowThreadLoadingSpinner(
                retireListRemoved = false,
                hasThread = true,
                isLoading = false,
                hasError = false,
                readingListReady = true
            )
        )
    }

    @Test
    fun errorWithoutThreadAndRetiringHideSpinner() {
        assertFalse(
            shouldShowThreadLoadingSpinner(
                retireListRemoved = false,
                hasThread = false,
                isLoading = false,
                hasError = true,
                readingListReady = false
            )
        )
        assertFalse(
            shouldShowThreadLoadingSpinner(
                retireListRemoved = true,
                hasThread = true,
                isLoading = true,
                hasError = false,
                readingListReady = false
            )
        )
    }
}
