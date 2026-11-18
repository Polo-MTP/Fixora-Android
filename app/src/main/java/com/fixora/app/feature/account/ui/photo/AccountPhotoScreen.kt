package com.fixora.app.feature.account.ui.photo

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraCard
import com.fixora.app.core.designsystem.component.FixoraCardDivider
import com.fixora.app.core.designsystem.component.FixoraListRow
import com.fixora.app.core.designsystem.component.FixoraPrimaryButton
import com.fixora.app.core.designsystem.theme.ErrorRed
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.ui.message
import com.fixora.app.feature.account.ui.components.AccountScaffold
import com.fixora.app.feature.account.ui.components.FormErrorText
import com.fixora.app.feature.profile.ui.components.ProfilePhoto
import java.io.File

/** Foto de perfil del cliente (cámara o galería). Al guardar regresa a Mi cuenta. */
@Composable
fun AccountPhotoScreen(
    onBack: () -> Unit,
    viewModel: AccountPhotoViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val savedMessage = stringResource(R.string.account_photo_saved)
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
        if (taken) viewModel.onPhotoPicked(cameraUri)
    }
    // Selector de fotos del sistema: no pide permiso de almacenamiento.
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onPhotoPicked(uri)
    }

    AccountPhotoContent(
        state = state,
        onBack = onBack,
        onTakePhoto = {
            val uri = newAccountPhotoUri(context)
            cameraUri = uri
            try {
                takePicture.launch(uri)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, noCameraMessage, Toast.LENGTH_SHORT).show()
            }
        },
        onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onRemove = viewModel::remove,
        onSave = viewModel::save,
    )
}

@Composable
private fun AccountPhotoContent(
    state: AccountPhotoUiState,
    onBack: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onRemove: () -> Unit,
    onSave: () -> Unit,
) {
    AccountScaffold(
        title = stringResource(R.string.account_photo_title),
        onBack = onBack,
        bottomBar = {
            FixoraPrimaryButton(
                text = stringResource(R.string.account_save),
                onClick = onSave,
                enabled = state.isLoaded,
                loading = state.isSaving,
            )
        },
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ProfilePhoto(model = state.shownPhoto, initials = "", size = 120.dp)
        }
        FixoraCard {
            FixoraListRow(
                icon = R.drawable.ic_photo_camera,
                title = stringResource(R.string.profile_photo_take),
                onClick = { if (state.isLoaded && !state.isSaving) onTakePhoto() },
            )
            FixoraCardDivider()
            FixoraListRow(
                icon = R.drawable.ic_image,
                title = stringResource(R.string.profile_photo_pick),
                onClick = { if (state.isLoaded && !state.isSaving) onPickPhoto() },
            )
            if (state.canRemove) {
                FixoraCardDivider()
                FixoraListRow(
                    icon = R.drawable.ic_image,
                    title = stringResource(R.string.account_photo_remove),
                    onClick = { if (!state.isSaving) onRemove() },
                    tint = ErrorRed,
                    showChevron = false,
                )
            }
        }
        Text(stringResource(R.string.account_photo_hint), style = MaterialTheme.typography.labelSmall, color = Slate500)
        if (state.photoUnreadable) FormErrorText(stringResource(R.string.profile_photo_unreadable))
        state.error?.let { FormErrorText(it.message()) }
    }
}

/** Archivo nuevo en la caché para la foto de la cámara; borra los de veces anteriores. */
private fun newAccountPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
    dir.listFiles()?.filter { it.name.startsWith("cuenta-") }?.forEach { it.delete() }
    val file = File(dir, "cuenta-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun AccountPhotoPreview() {
    FixoraTheme {
        AccountPhotoContent(
            state = AccountPhotoUiState(isLoaded = true),
            onBack = {}, onTakePhoto = {}, onPickPhoto = {}, onRemove = {}, onSave = {},
        )
    }
}
