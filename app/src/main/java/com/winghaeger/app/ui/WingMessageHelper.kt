package com.winghaeger.app.ui

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun Context.showWingMessage(title: String, message: String? = null, onDismiss: (() -> Unit)? = null) {
    val dlg = MaterialAlertDialogBuilder(this)
        .setTitle(title)
    if (!message.isNullOrBlank()) dlg.setMessage(message)
    dlg.setPositiveButton("OK") { d, _ -> d.dismiss(); onDismiss?.invoke() }
    dlg.show()
}
