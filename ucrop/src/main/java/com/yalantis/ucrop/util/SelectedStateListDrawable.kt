package com.yalantis.ucrop.util

import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable

/**
 * Hack class to properly support state drawable back to Android 1.6
 */
class SelectedStateListDrawable(drawable: Drawable?, private val mSelectionColor: Int) : StateListDrawable() {

    init {
        addState(intArrayOf(android.R.attr.state_selected), drawable)
        addState(intArrayOf(), drawable)
    }

    override fun onStateChange(states: IntArray): Boolean {
        var isStatePressedInArray = false
        for (state in states) {
            if (state == android.R.attr.state_selected) {
                isStatePressedInArray = true
            }
        }
        if (isStatePressedInArray) {
            super.setColorFilter(mSelectionColor, PorterDuff.Mode.SRC_ATOP)
        } else {
            super.clearColorFilter()
        }
        return super.onStateChange(states)
    }

    override fun isStateful(): Boolean {
        return true
    }

}