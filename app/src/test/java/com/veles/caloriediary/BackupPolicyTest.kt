package com.veles.caloriediary

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupPolicyTest {

    @Test
    fun keepLatestReturnsOnlyNewestCopies() {
        val names = listOf(
            "diary-20260101.db",
            "diary-20260201.db",
            "diary-20260301.db",
            "diary-20260401.db"
        )
        assertEquals(
            listOf("diary-20260401.db", "diary-20260301.db", "diary-20260201.db"),
            BackupPolicy.keepLatest(names, 3)
        )
    }
}
