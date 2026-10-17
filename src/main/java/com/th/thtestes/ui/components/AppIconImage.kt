package com.th.thtestes.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

@Composable
fun AppIconImage(
    drawable: Drawable?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val bitmap = remember(drawable) {
        if (drawable != null) {
            try {
                val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth.coerceAtMost(192) else 96
                val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight.coerceAtMost(192) else 96
                drawable.toBitmap(width = w, height = h)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier.size(size)
        )
    } else {
        Icon(
            imageVector = Icons.Default.Android,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier.size(size)
        )
    }
}
