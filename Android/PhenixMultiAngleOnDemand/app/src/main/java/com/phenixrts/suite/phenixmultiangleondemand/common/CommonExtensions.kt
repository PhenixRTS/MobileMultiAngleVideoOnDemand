/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixmultiangleondemand.common

import android.view.View
import android.widget.AdapterView
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.phenixrts.suite.phenixcore.common.launchMain
import com.phenixrts.suite.phenixmultiangleondemand.R
import com.phenixrts.suite.phenixmultiangleondemand.common.enums.ExpressError
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

private fun AppCompatActivity.closeApp() {
    finishAffinity()
    finishAndRemoveTask()
    exitProcess(0)
}

fun AppCompatActivity.getErrorMessage(error: ExpressError): String {
    return when (error) {
        ExpressError.DEEP_LINK_ERROR -> getString(R.string.err_invalid_deep_link)
        ExpressError.UNRECOVERABLE_ERROR -> getString(R.string.err_unrecoverable_error)
        ExpressError.CONFIGURATION_CHANGED_ERROR -> getString(R.string.err_configuration_changed)
    }
}

fun View.showSnackBar(message: String) = launchMain {
    Snackbar.make(this@showSnackBar, message, Snackbar.LENGTH_INDEFINITE).show()
}

fun AppCompatActivity.showErrorDialog(error: String) {
    AlertDialog.Builder(this, R.style.AlertDialogTheme)
        .setCancelable(false)
        .setMessage(error)
        .setPositiveButton(getString(R.string.popup_ok)) { dialog, _ ->
            dialog.dismiss()
            closeApp()
        }
        .create()
        .show()
}

fun View.setVisibleOr(visible: Boolean, orElse: Int = View.GONE) {
    val newVisibility = if (visible) View.VISIBLE else orElse
    if (visibility != newVisibility) {
        visibility = newVisibility
    }
}

fun Date.toDateString(): String = SimpleDateFormat("mm:ss", Locale.getDefault()).format(this)

fun String.toMillis(): Long = split(":").takeIf { it.size == 2 }?.let { parts ->
    try {
        val minutes = TimeUnit.MINUTES.toMillis(parts[0].toLong())
        val seconds = TimeUnit.SECONDS.toMillis(parts[1].toLong())
        minutes + seconds
    } catch (e: Exception) {
        0L
    }
} ?: 0L

fun Spinner.onSelectionChanged(callback: (Int) -> Unit) {
    var lastSelectedPosition = 0
    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) {
            /* Ignored */
        }

        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            if (lastSelectedPosition != position) {
                lastSelectedPosition = position
                callback(position)
            }
        }
    }
}
