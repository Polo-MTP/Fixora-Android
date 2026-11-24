package com.fixora.app.feature.profile.ui.photo

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
import androidx.compose.ui.res.pluralStringResource
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
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.ui.message
import com.fixora.app.feature.account.ui.components.AccountScaffold
import com.fixora.app.feature.account.ui.components.ActiveServiceNotice
import com.fixora.app.feature.account.ui.components.FormErrorText
import com.fixora.app.feature.account.ui.components.LoadErrorNotice
import com.fixora.app.feature.auth.ui.components.AuthTextField
import com.fixora.app.feature.profile.ui.components.ProfilePhoto
import java.io.File

/** Foto (cámara o galería) y biografía del técnico (ANDROID-7). Al guardar regresa al perfil. */
@Composable
fun TechnicianPhotoScreen(
    onBack: () -> Unit,
    viewModel: TechnicianPhotoViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val savedMessage = stringResource(R.string.profile_photo_saved)
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

    TechnicianPhotoContent(
        state = state,
        onBack = onBack,
        onTakePhoto = {
            val uri = newCameraUri(context)
            cameraUri = uri
            try {
                takePicture.launch(uri)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, noCameraMessage, Toast.LENGTH_SHORT).show()
            }
        },
        onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onBioChange = viewModel::onBioChange,
        onSave = viewModel::save,
        onRetryLoad = viewModel::retryLoad,
    )
}

@Composable
private fun TechnicianPhotoContent(
    state: TechnicianPhotoUiState,
    onBack: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onBioChange: (String) -> Unit,
    onSave: () -> Unit,
    onRetryLoad: () -> Unit,
) {
    AccountScaffold(
        title = stringResource(R.string.profile_photo_title),
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
        if (state.notTechnician) {
            Text(stringResource(R.string.profile_not_technician), style = MaterialTheme.typography.bodySmall, color = Slate500)
            return@AccountScaffold
        }
        state.loadError?.takeIf { !state.isLoaded }?.let { LoadErrorNotice(it, onRetry = onRetryLoad) }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ProfilePhoto(model = state.pickedPhoto ?: state.savedPhotoUrl, initials = "", size = 120.dp)
        }
        if (state.photoLocked) {
            ActiveServiceNotice(stringResource(R.string.active_service_technician_photo_locked))
        } else {
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
            }
            Text(stringResource(R.string.profile_photo_hint), style = MaterialTheme.typography.labelSmall, color = Slate500)
        }
        if (state.photoUnreadable) FormErrorText(stringResource(R.string.profile_photo_unreadable))

        AuthTextField(
            value = state.bio,
            onValueChange = onBioChange,
            label = stringResource(R.string.profile_bio),
            placeholder = stringResource(R.string.profile_bio_placeholder),
            minLines = 4,
        )
        Text(
            text = pluralStringResource(R.plurals.profile_bio_remaining, state.bioRemaining, state.bioRemaining),
            style = MaterialTheme.typography.labelSmall,
            color = Slate500,
            modifier = Modifier.align(Alignment.End),
        )
        state.error?.let { FormErrorText(it.message()) }
    }
}

/** Archivo nuevo en la caché para la foto de la cámara; borra los de veces anteriores. */
private fun newCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "perfil-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TechnicianPhotoPreview() {
    FixoraTheme {
        TechnicianPhotoContent(
            state = TechnicianPhotoUiState(isLoaded = true, bio = "Plomero con 10 años de experiencia."),
            onBack = {}, onTakePhoto = {}, onPickPhoto = {}, onBioChange = {}, onSave = {}, onRetryLoad = {},
        )
    }
}
