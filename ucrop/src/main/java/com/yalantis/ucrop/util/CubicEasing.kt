package com.yalantis.ucrop.util

object CubicEasing {

    @JvmStatic
    fun easeOut(time: Float, start: Float, end: Float, duration: Float): Float {
        var t = time
        return end * ((t / duration - 1.0f.also { t = it }) * t * t + 1.0f) + start
    }

    fun easeIn(time: Float, start: Float, end: Float, duration: Float): Float {
        var t = time
        return end * duration.let { t /= it; t } * t * t + start
    }

    @JvmStatic
    fun easeInOut(time: Float, start: Float, end: Float, duration: Float): Float {
        var t = time
        return if (duration / 2.0f.let { t /= it; t } < 1.0f) end / 2.0f * t * t * t + start else end / 2.0f * (2.0f.let { t -= it; t } * t * t + 2.0f) + start
    }
}