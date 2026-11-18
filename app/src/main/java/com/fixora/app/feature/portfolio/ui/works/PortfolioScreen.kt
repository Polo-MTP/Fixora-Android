package com.fixora.app.feature.portfolio.ui.works

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraCard
import com.fixora.app.core.designsystem.component.FixoraPrimaryButton
import com.fixora.app.core.designsystem.theme.ErrorRed
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Mist200
import com.fixora.app.core.designsystem.theme.Navy800
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.ui.PhotoViewer
import com.fixora.app.core.ui.message
import com.fixora.app.feature.account.ui.components.AccountScaffold
import com.fixora.app.feature.account.ui.components.FormErrorText
import com.fixora.app.feature.account.ui.components.LoadErrorNotice
import com.fixora.app.feature.portfolio.domain.PortfolioRules
import com.fixora.app.feature.portfolio.domain.PortfolioWork

/** Trabajos realizados del proveedor (ANDROID-8): lo que el cliente ve en su ficha. */
@Composable
fun PortfolioScreen(
    onBack: () -> Unit,
    onAddWork: () -> Unit,
    viewModel: PortfolioViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val noCameraMessage = stringResource(R.string.profile_photo_no_camera)
    // También al regresar de agregar un trabajo, para que aparezca en la lista.
    LaunchedEffect(Unit) { viewModel.refresh() }

    // Trabajo al que van las fotos que se tomen o elijan; se guarda por si Android recrea la pantalla.
    var targetWorkId by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val workId = targetWorkId
        val uri = cameraUri
        if (taken && workId != null && uri != null) viewModel.addPhotos(workId, listOf(uri))
    }
    val pickPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(PortfolioRules.MAX_PHOTOS),
    ) { uris -> targetWorkId?.let { viewModel.addPhotos(it, uris) } }

    PortfolioContent(
        state = state,
        onBack = onBack,
        onAddWork = onAddWork,
        onDelete = viewModel::delete,
        onRetry = viewModel::refresh,
        onTakePhoto = { workId ->
            targetWorkId = workId
            val uri = newWorkPhotoUri(context)
            cameraUri = uri
            try {
                takePicture.launch(uri)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, noCameraMessage, Toast.LENGTH_SHORT).show()
            }
        },
        onPickPhotos = { workId ->
            targetWorkId = workId
            pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
    )
}

@Composable
private fun PortfolioContent(
    state: PortfolioUiState,
    onBack: () -> Unit,
    onAddWork: () -> Unit,
    onDelete: (String) -> Unit,
    onRetry: () -> Unit,
    onTakePhoto: (String) -> Unit,
    onPickPhotos: (String) -> Unit,
) {
    var confirmDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var choosePhotoSourceFor by rememberSaveable { mutableStateOf<String?>(null) }

    AccountScaffold(
        title = stringResource(R.string.portfolio_title),
        onBack = onBack,
        bottomBar = if (state.noBranch) null else {
            { FixoraPrimaryButton(text = stringResource(R.string.portfolio_add), onClick = onAddWork) }
        },
    ) {
        when {
            state.noBranch -> Text(stringResource(R.string.portfolio_no_branch), style = MaterialTheme.typography.bodySmall, color = Slate500)
            state.loadError != null && state.works.isEmpty() -> LoadErrorNotice(state.loadError, onRetry = onRetry)
            !state.isLoading && state.works.isEmpty() ->
                Text(stringResource(R.string.portfolio_empty), style = MaterialTheme.typography.bodyMedium, color = Slate500)
            else -> {
                Text(stringResource(R.string.portfolio_hint), style = MaterialTheme.typography.labelSmall, color = Slate500)
                state.works.forEach { work ->
                    WorkCard(
                        work = work,
                        deleting = state.deletingId == work.id,
                        addingPhotos = state.addingPhotosId == work.id,
                        busy = state.addingPhotosId != null,
                        onAddPhotos = { choosePhotoSourceFor = work.id },
                        onDelete = { confirmDeleteId = work.id },
                    )
                }
            }
        }
        if (state.photoUnreadable) FormErrorText(stringResource(R.string.add_work_photo_unreadable))
        state.addPhotosError?.let { FormErrorText(it.message()) }
        state.deleteError?.let { FormErrorText(it.message()) }
    }

    choosePhotoSourceFor?.let { workId ->
        AlertDialog(
            onDismissRequest = { choosePhotoSourceFor = null },
            title = { Text(stringResource(R.string.portfolio_add_photos)) },
            confirmButton = {
                TextButton(onClick = {
                    choosePhotoSourceFor = null
                    onPickPhotos(workId)
                }) { Text(stringResource(R.string.profile_photo_pick)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    choosePhotoSourceFor = null
                    onTakePhoto(workId)
                }) { Text(stringResource(R.string.profile_photo_take)) }
            },
        )
    }

    confirmDeleteId?.let { workId ->
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            title = { Text(stringResource(R.string.portfolio_delete_title)) },
            text = { Text(stringResource(R.string.portfolio_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteId = null
                    onDelete(workId)
                }) { Text(stringResource(R.string.portfolio_delete), color = ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteId = null }) { Text(stringResource(R.string.dialog_cancel)) }
            },
        )
    }
}

@Composable
private fun WorkCard(
    work: PortfolioWork,
    deleting: Boolean,
    addingPhotos: Boolean,
    busy: Boolean,
    onAddPhotos: () -> Unit,
    onDelete: () -> Unit,
) {
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    viewerIndex?.let { PhotoViewer(photos = work.photoUrls, startIndex = it, onDismiss = { viewerIndex = null }) }

    FixoraCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
            Text(
                text = work.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = Navy900,
            )
            if (work.description.isNotBlank()) {
                Text(work.description, style = MaterialTheme.typography.bodyMedium, color = Slate500)
            }
            if (work.photoUrls.isNotEmpty()) {
                val openLabel = stringResource(R.string.photo_viewer_open)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    work.photoUrls.forEachIndexed { index, url ->
                        AsyncImage(
                            model = url,
                            contentDescription = stringResource(R.string.portfolio_photo_description, work.title),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Mist200)
                                .clickable(onClickLabel = openLabel) { viewerIndex = index },
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = pluralStringResource(R.plurals.portfolio_photos_count, work.photoUrls.size, work.photoUrls.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate500,
                    modifier = Modifier.weight(1f),
                )
                if (addingPhotos) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = Navy800, modifier = Modifier.size(20.dp))
                } else if (work.photosLeft() > 0) {
                    TextButton(onClick = onAddPhotos, enabled = !busy && !deleting) {
                        Text(stringResource(R.string.portfolio_add_photos), color = Navy800)
                    }
                }
                TextButton(onClick = onDelete, enabled = !deleting && !addingPhotos) {
                    Text(stringResource(R.string.portfolio_delete), color = ErrorRed)
                }
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PortfolioPreview() {
    FixoraTheme {
        PortfolioContent(
            state = PortfolioUiState(
                isLoading = false,
                works = listOf(
                    PortfolioWork("1", "Cambio de tubería de cobre", "Baño completo en dos días.", emptyList()),
                    PortfolioWork("2", "Calentador solar", "", emptyList()),
                ),
            ),
            onBack = {}, onAddWork = {}, onDelete = {}, onRetry = {}, onTakePhoto = {}, onPickPhotos = {},
        )
    }
}
