package com.veles.caloriediary

object BackupPolicy {

    fun keepLatest(names: List<String>, max: Int): List<String> =
        names.sortedDescending().take(max.coerceAtLeast(0))
}
