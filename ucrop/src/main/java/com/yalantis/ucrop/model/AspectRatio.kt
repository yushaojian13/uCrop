package com.yalantis.ucrop.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

/**
 * Created by Oleksii Shliama [https://github.com/shliama] on 6/24/16.
 */
@Parcelize
class AspectRatio(val aspectRatioTitle: String?, val aspectRatioX: Float, val aspectRatioY: Float) : Parcelable