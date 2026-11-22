package com.veles.caloriediary

import android.app.Dialog
import android.content.Context
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

// Вызывается из OnShowListener: ставит курсор и явно поднимает клавиатуру.
fun Dialog.focusAndShowKeyboard(field: View, selectAll: Boolean = false) {
    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    field.requestFocus()
    if (selectAll && field is EditText) {
        field.setSelection(0, field.text.length)
    }
    field.post {
        field.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
    }
}
