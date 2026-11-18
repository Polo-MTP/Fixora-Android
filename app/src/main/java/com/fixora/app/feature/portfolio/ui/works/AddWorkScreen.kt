package com.fixora.app.feature.portfolio.ui.works

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraCard
import com.fixora.app.core.designsystem.component.FixoraCardDivider
import com.fixora.app.core.designsystem.component.FixoraListRow
import com.fixora.app.core.designsystem.component.FixoraPrimaryButton
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Mist200
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.core.ui.PhotoViewer
import com.fixora.app.core.ui.message
import com.fixora.app.feature.account.ui.components.AccountScaffold
import com.fixora.app.feature.account.ui.components.FormErrorText
import com.fixora.app.feature.auth.ui.components.AuthTextField
import com.fixora.app.feature.portfolio.domain.PortfolioRules
import java.io.File

/** Agregar un trabajo (ANDROID-8): título, descripción y hasta 10 fotos opcionales. */
@Composable
fun AddWorkScreen(
    onBack: () -> Unit,
    viewModel: AddWorkViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val savedMessage = stringResource(R.string.add_work_saved)
    val noCameraMessage = stringResource(R.string.profile_photo_no_camera)
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    // Uri donde la app de cámara deja la foto; se guarda por si Android recrea la pantalla.
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        cameraUri?.takeIf { taken }?.let { viewModel.onPhotosPicked(listOf(it)) }
    }
    // Selector de fotos del sistema: no pide permiso de almacenamiento y deja elegir varias.
    val pickPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(PortfolioRules.MAX_PHOTOS),
    ) { uris -> viewModel.onPhotosPicked(uris) }

    AddWorkContent(
        state = state,
        onBack = onBack,
        onTitleChange = viewModel::onTitleChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onTakePhoto = {
            val uri = newWorkPhotoUri(context)
            cameraUri = uri
            try {
                takePicture.launch(uri)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, noCameraMessage, Toast.LENGTH_SHORT).show()
            }
        },
        onPickPhotos = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onRemovePhoto = viewModel::removePhoto,
        onSave = viewModel::save,
    )
}

@Composable
private fun AddWorkContent(
    state: AddWorkUiState,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhotos: () -> Unit,
    onRemovePhoto: (Uri) -> Unit,
    onSave: () -> Unit,
) {
    AccountScaffold(
        title = stringResource(R.string.add_work_title),
        onBack = onBack,
        bottomBar = {
            FixoraPrimaryButton(text = stringResource(R.string.add_work_save), onClick = onSave, loading = state.isSaving)
        },
    ) {
        if (state.noBranch) {
            Text(stringResource(R.string.portfolio_no_branch), style = MaterialTheme.typography.bodySmall, color = Slate500)
            return@AccountScaffold
        }
        AuthTextField(
            value = state.title,
            onValueChange = onTitleChange,
            label = stringResource(R.string.add_work_field_title),
            placeholder = stringResource(R.string.add_work_field_title_placeholder),
            error = if (state.titleMissing) stringResource(R.string.add_work_title_required) else null,
        )
        AuthTextField(
            value = state.description,
            onValueChange = onDescriptionChange,
            label = stringResource(R.string.add_work_field_description),
            placeholder = stringResource(R.string.add_work_field_description_placeholder),
            minLines = 3,
        )

        Text(stringResource(R.string.add_work_photos), style = MaterialTheme.typography.labelLarge, color = Navy900)
        if (state.photos.isNotEmpty()) {
            var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
            viewerIndex?.let { PhotoViewer(photos = state.photos, startIndex = it, onDismiss = { viewerIndex = null }) }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                state.photos.forEachIndexed { index, uri ->
                    PickedPhoto(uri, onOpen = { viewerIndex = index }, onRemove = { onRemovePhoto(uri) })
                }
            }
        }
        if (state.canAddPhotos) {
            FixoraCard {
                FixoraListRow(
                    icon = R.drawable.ic_photo_camera,
                    title = stringResource(R.string.profile_photo_take),
                    onClick = { if (!state.isSaving) onTakePhoto() },
                )
                FixoraCardDivider()
                FixoraListRow(
                    icon = R.drawable.ic_image,
                    title = stringResource(R.string.profile_photo_pick),
                    onClick = { if (!state.isSaving) onPickPhotos() },
                )
            }
        }
        Text(
            text = pluralStringResource(R.plurals.add_work_photos_left, state.photosLeft, state.photosLeft),
            style = MaterialTheme.typography.labelSmall,
            color = Slate500,
        )
        if (state.photoUnreadable) FormErrorText(stringResource(R.string.add_work_photo_unreadable))
        state.error?.let { FormErrorText(it.message()) }
    }
}

/** Miniatura de una foto elegida, con una «x» para quitarla antes de guardar. */
@Composable
private fun PickedPhoto(uri: Uri, onOpen: () -> Unit, onRemove: () -> Unit) {
    val removeLabel = stringResource(R.string.add_work_remove_photo)
    val openLabel = stringResource(R.string.photo_viewer_open)
    Box(contentAlignment = Alignment.TopEnd) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Mist200)
                .clickable(onClickLabel = openLabel, onClick = onOpen),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(4.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(Navy900.copy(alpha = 0.7f))
                .clickable(onClick = onRemove)
                .semantics { contentDescription = removeLabel },
        ) {
            Text("×", color = White, fontSize = 18.sp)
        }
    }
}

/** Archivo nuevo en la caché para la foto de la cámara. No borra las anteriores: siguen elegidas. */
internal fun newWorkPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
    val file = File(dir, "trabajo-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun AddWorkPreview() {
    FixoraTheme {
        AddWorkContent(
            state = AddWorkUiState(title = "Cambio de tubería", submitted = false),
            onBack = {}, onTitleChange = {}, onDescriptionChange = {}, onTakePhoto = {}, onPickPhotos = {},
            onRemovePhoto = {}, onSave = {},
        )
    }
}
