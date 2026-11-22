package com.veles.caloriediary

import android.app.Dialog
import android.content.Context
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

// Вызывать сразу после .create(), до show(): иначе система не поднимает клавиатуру.
fun Dialog.prepareForKeyboard() {
    window?.setSoftInputMode(
        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    )
}

// Ставит курсор и показывает клавиатуру только после того, как окно и поле прикреплены.
fun Dialog.focusAndShowKeyboard(field: View, selectAll: Boolean = false) {
    val dialog = this
    field.requestFocus()
    if (selectAll && field is EditText) {
        field.setSelection(0, field.text.length)
    }
    field.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            val windowReady = dialog.window?.decorView?.isAttachedToWindow == true
            if (!field.isAttachedToWindow || !windowReady) return
            field.viewTreeObserver.removeOnGlobalLayoutListener(this)
            field.requestFocus()
            val imm = dialog.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        }
    })
}
