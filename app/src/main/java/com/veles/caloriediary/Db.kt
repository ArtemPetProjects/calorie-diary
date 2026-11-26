package com.veles.caloriediary

import android.content.Context
import com.veles.caloriediary.data.SqliteDiaryRepository
import com.veles.caloriediary.domain.model.Targets

class Db(context: Context) : SqliteDiaryRepository(context) {
    companion object {
        const val SCHEME_OWN = "own"
        val DEFAULT_TARGETS = Targets(2000.0, 120.0, 60.0, 250.0)
    }
}
