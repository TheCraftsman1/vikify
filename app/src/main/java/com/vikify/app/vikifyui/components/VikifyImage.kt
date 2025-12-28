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
    @androidx.annotation.DrawableRes placeholder: Int = R.drawable.artwork_placeholder,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            placeholder = painterResource(placeholder),
            error = painterResource(placeholder)
        )
    } else {
        Image(
            painter = painterResource(placeholder),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}
