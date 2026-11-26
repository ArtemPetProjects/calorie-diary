package com.veles.caloriediary.domain.usecase

object BackupPolicy {
    fun keepLatest(names: List<String>, max: Int): List<String> =
        names.sortedDescending().take(max.coerceAtLeast(0))
}