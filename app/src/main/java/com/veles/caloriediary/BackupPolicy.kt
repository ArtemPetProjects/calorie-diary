package com.veles.caloriediary

object BackupPolicy {
    fun keepLatest(names: List<String>, max: Int): List<String> =
        com.veles.caloriediary.domain.usecase.BackupPolicy.keepLatest(names, max)
}
