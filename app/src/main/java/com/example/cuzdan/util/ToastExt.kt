package com.example.cuzdan.util

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.example.cuzdan.R
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar

fun Fragment.showToast(@StringRes messageRes: Int) {
    view?.let { showCustomSnackbar(it, getString(messageRes)) }
}

fun Fragment.showToast(message: String) {
    view?.let { showCustomSnackbar(it, message) }
}

fun Activity.showToast(message: String) {
    findViewById<View>(android.R.id.content)?.let { showCustomSnackbar(it, message) }
}

fun Activity.showToast(@StringRes messageRes: Int) {
    findViewById<View>(android.R.id.content)?.let { showCustomSnackbar(it, getString(messageRes)) }
}

private fun showCustomSnackbar(view: View, message: String) {
    val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_SHORT)
    
    // Customize snackbar to look like a clean toast (no icon)
    val snackbarView = snackbar.view
    snackbarView.setBackgroundResource(R.drawable.bg_toast) // We will create this
    
    val textView = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
    textView.setTextColor(Color.WHITE)
    textView.textAlignment = View.TEXT_ALIGNMENT_CENTER
    
    // Position it like a toast (bottom but with margin)
    val params = snackbarView.layoutParams as FrameLayout.LayoutParams
    params.setMargins(64, 0, 64, 250) // Adjust bottom margin as needed
    snackbarView.layoutParams = params
    
    snackbar.animationMode = BaseTransientBottomBar.ANIMATION_MODE_FADE
    snackbar.show()
}
