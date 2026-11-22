package com.veles.caloriediary

import android.app.Dialog
import android.view.View
import android.view.WindowManager
import android.widget.EditText

// Вызывается из OnShowListener: ставит курсор и поднимает клавиатуру сразу при открытии окна.
fun Dialog.focusAndShowKeyboard(field: View, selectAll: Boolean = false) {
    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    field.requestFocus()
    if (selectAll && field is EditText) {
        field.setSelection(0, field.text.length)
    }
}
