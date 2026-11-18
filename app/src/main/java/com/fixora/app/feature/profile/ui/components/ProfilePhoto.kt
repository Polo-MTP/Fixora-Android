package com.fixora.app.feature.profile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraAvatar
import com.fixora.app.core.designsystem.theme.Mist200
import com.fixora.app.core.ui.PhotoViewer

/**
 * Foto de perfil en círculo. [model] es el enlace firmado de la API o la foto recién elegida
 * (Uri). Sin foto, mientras carga o si falla, se ven las iniciales. Al tocarla se abre en grande.
 */
@Composable
fun ProfilePhoto(model: Any?, initials: String, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    if (model == null) {
        FixoraAvatar(initials, modifier, size)
        return
    }
    var showViewer by rememberSaveable { mutableStateOf(false) }
    if (showViewer) PhotoViewer(photos = listOf(model), onDismiss = { showViewer = false })
    SubcomposeAsyncImage(
        model = model,
        contentDescription = stringResource(R.string.profile_photo_description),
        contentScale = ContentScale.Crop,
        loading = { FixoraAvatar(initials, size = size) },
        error = { FixoraAvatar(initials, size = size) },
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Mist200)
            .clickable(onClickLabel = stringResource(R.string.photo_viewer_open)) { showViewer = true },
    )
}
