package com.vikify.app.vikifyui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import coil3.compose.AsyncImage
import com.vikify.app.R

@Composable
fun VikifyImage(
    url: String?,
    placeholder: Any? = R.drawable.artwork_placeholder,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val placeholderPainter = when (placeholder) {
        is Int -> painterResource(placeholder)
        is androidx.compose.ui.graphics.vector.ImageVector -> androidx.compose.ui.graphics.vector.rememberVectorPainter(placeholder)
        else -> painterResource(R.drawable.artwork_placeholder)
    }

    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            placeholder = placeholderPainter,
            error = placeholderPainter
        )
    } else {
        Image(
            painter = placeholderPainter,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}
