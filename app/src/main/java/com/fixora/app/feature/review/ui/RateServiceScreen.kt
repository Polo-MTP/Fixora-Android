package com.fixora.app.feature.review.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraPrimaryButton
import com.fixora.app.core.designsystem.theme.ErrorRed
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Mist200
import com.fixora.app.core.designsystem.theme.Mist50
import com.fixora.app.core.designsystem.theme.Navy800
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Orange500
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.core.ui.message
import com.fixora.app.feature.account.ui.components.AccountScaffold
import com.fixora.app.feature.account.ui.components.FormErrorText
import com.fixora.app.feature.account.ui.components.LoadErrorNotice
import com.fixora.app.feature.auth.ui.components.AuthTextField
import com.fixora.app.feature.payment.ui.NoticeTone
import com.fixora.app.feature.payment.ui.OutlineActionButton
import com.fixora.app.feature.payment.ui.PaymentNotice
import com.fixora.app.feature.review.domain.ReviewDirection
import com.fixora.app.feature.review.domain.ReviewRules
import com.fixora.app.feature.review.domain.ServiceReview
import com.fixora.app.feature.servicerequest.data.EvidenceProblem
import com.fixora.app.feature.servicerequest.ui.trip.EvidenceRow
import java.io.File

/** Calificar al terminar el servicio (ANDROID-26). Al guardar avisa y regresa. */
@Composable
fun RateServiceScreen(
    onBack: () -> Unit,
    viewModel: RateServiceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val thanks = stringResource(R.string.review_thanks)
    val noCamera = stringResource(R.string.profile_photo_no_camera)
    LaunchedEffect(state.isDone) {
        if (state.isDone) {
            Toast.makeText(context, thanks, Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) viewModel.onEvidencePicked(cameraUri)
    }
    // Selector de fotos y videos del sistema: no pide permiso de almacenamiento.
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onEvidencePicked(uri)
    }

    RateServiceContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onRatingChange = viewModel::onRatingChange,
        onCommentChange = viewModel::onCommentChange,
        onTakePhoto = {
            val uri = newReviewPhotoUri(context)
            cameraUri = uri
            try {
                takePhoto.launch(uri)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, noCamera, Toast.LENGTH_SHORT).show()
            }
        },
        onPickMedia = { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
        onRemoveEvidence = viewModel::removeEvidence,
        onSubmit = viewModel::submit,
    )
}

@Composable
private fun RateServiceContent(
    state: RateServiceUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRatingChange: (Int) -> Unit,
    onCommentChange: (String) -> Unit,
    onTakePhoto: () -> Unit,
    onPickMedia: () -> Unit,
    onRemoveEvidence: (Long) -> Unit,
    onSubmit: () -> Unit,
) {
    val rateTechnician = state.direction != ReviewDirection.ProviderToClient
    AccountScaffold(
        title = stringResource(R.string.review_title),
        onBack = onBack,
        bottomBar = {
            if (state.canReview) {
                FixoraPrimaryButton(
                    text = stringResource(R.string.review_send),
                    onClick = onSubmit,
                    loading = state.isSending,
                    enabled = !state.isReading,
                )
            } else if (!state.isLoading) {
                FixoraPrimaryButton(text = stringResource(R.string.review_done), onClick = onBack)
            }
        },
    ) {
        val loadError = state.loadError
        when {
            loadError != null -> LoadErrorNotice(loadError, onRetry = onRetry)
            state.isLoading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Navy800, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
            else -> {
                Counterpart(state.counterpartName, state.counterpartPhotoUrl, rateTechnician)
                val existing = state.existing
                when {
                    existing != null -> AlreadyRated(existing)
                    state.notYet -> PaymentNotice(
                        icon = R.drawable.ic_schedule,
                        title = stringResource(R.string.review_not_yet_title),
                        text = stringResource(R.string.review_not_yet_text),
                    )
                    else -> RateForm(state, rateTechnician, onRatingChange, onCommentChange, onTakePhoto, onPickMedia, onRemoveEvidence)
                }
            }
        }
    }
}

@Composable
private fun Counterpart(name: String, photoUrl: String?, rateTechnician: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Navy900),
        ) {
            Text(initials(name), style = MaterialTheme.typography.titleLarge, color = White)
            photoUrl?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        val shownName = name.ifBlank {
            stringResource(if (rateTechnician) R.string.review_the_technician else R.string.review_the_client)
        }
        Text(
            stringResource(if (rateTechnician) R.string.review_question_technician else R.string.review_question_client, shownName),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Navy900,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RateForm(
    state: RateServiceUiState,
    rateTechnician: Boolean,
    onRatingChange: (Int) -> Unit,
    onCommentChange: (String) -> Unit,
    onTakePhoto: () -> Unit,
    onPickMedia: () -> Unit,
    onRemoveEvidence: (Long) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        StarPicker(rating = state.rating, enabled = !state.isSending, onChange = onRatingChange)
        Text(
            if (state.rating == 0) stringResource(R.string.review_tap_stars) else ratingLabel(state.rating),
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.ratingMissing) ErrorRed else Slate500,
        )
    }
    AuthTextField(
        value = state.comment,
        onValueChange = onCommentChange,
        label = stringResource(R.string.review_comment),
        placeholder = stringResource(if (rateTechnician) R.string.review_comment_placeholder_technician else R.string.review_comment_placeholder_client),
        minLines = 3,
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.review_evidence), style = MaterialTheme.typography.labelLarge, color = Navy900)
        Text(
            stringResource(R.string.review_evidence_hint, ReviewRules.MAX_EVIDENCE),
            style = MaterialTheme.typography.bodySmall,
            color = Slate500,
        )
    }
    state.evidence.forEach { item ->
        EvidenceRow(item, enabled = !state.isSending, onRemove = { onRemoveEvidence(item.id) })
    }
    if (state.evidence.size < ReviewRules.MAX_EVIDENCE) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineActionButton(
                text = stringResource(R.string.review_take_photo),
                onClick = onTakePhoto,
                enabled = state.canAddEvidence,
                modifier = Modifier.weight(1f),
            )
            OutlineActionButton(
                text = stringResource(R.string.review_pick_media),
                onClick = onPickMedia,
                enabled = state.canAddEvidence,
                loading = state.isReading,
                modifier = Modifier.weight(1f),
            )
        }
    }
    state.problem?.let { FormErrorText(evidenceProblemText(it)) }
    state.error?.let { FormErrorText(it.message()) }
}

/** Cinco estrellas grandes para tocar; cada una es un botón con su descripción. */
@Composable
private fun StarPicker(rating: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (value in 1..5) {
            val description = stringResource(R.string.review_star, value, ratingLabel(value))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .clickable(enabled = enabled, role = Role.RadioButton) { onChange(value) }
                    .semantics {
                        contentDescription = description
                        selected = value == rating
                    },
            ) {
                Icon(
                    painterResource(R.drawable.ic_star),
                    contentDescription = null,
                    tint = if (value <= rating) Orange500 else Mist200,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

@Composable
private fun AlreadyRated(review: ServiceReview) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Mist50)
            .border(1.dp, Mist200, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text(
            stringResource(R.string.review_already_title),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = Navy900,
        )
        val description = stringResource(R.string.provider_profile_stars, review.rating)
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.semantics { contentDescription = description },
        ) {
            repeat(5) { index ->
                Icon(
                    painterResource(R.drawable.ic_star),
                    contentDescription = null,
                    tint = if (index < review.rating) Orange500 else Mist200,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (review.comment.isNotEmpty()) {
            Text(review.comment, style = MaterialTheme.typography.bodyMedium, color = Slate500)
        }
    }
    PaymentNotice(
        icon = R.drawable.ic_info,
        title = stringResource(R.string.review_already_hint),
        tone = NoticeTone.Info,
    )
}

@Composable
private fun ratingLabel(rating: Int): String = stringResource(
    when (rating) {
        1 -> R.string.review_rating_1
        2 -> R.string.review_rating_2
        3 -> R.string.review_rating_3
        4 -> R.string.review_rating_4
        else -> R.string.review_rating_5
    },
)

@Composable
private fun evidenceProblemText(problem: EvidenceProblem): String = when (problem) {
    EvidenceProblem.WrongType -> stringResource(R.string.review_evidence_wrong_type)
    EvidenceProblem.TooLarge -> stringResource(R.string.trip_cancel_evidence_too_large)
    EvidenceProblem.Unreadable -> stringResource(R.string.trip_cancel_evidence_unreadable)
}

private fun initials(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

/** Archivo nuevo en la caché para la foto de la cámara; borra los de reseñas anteriores. */
private fun newReviewPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
    dir.listFiles()?.filter { it.name.startsWith("resena-") }?.forEach { it.delete() }
    val file = File(dir, "resena-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RateServicePreview() {
    FixoraTheme {
        RateServiceContent(
            state = RateServiceUiState(
                isLoading = false,
                direction = ReviewDirection.ClientToProvider,
                counterpartName = "Juan Pérez",
                canReview = true,
                rating = 4,
            ),
            onBack = {}, onRetry = {}, onRatingChange = {}, onCommentChange = {}, onTakePhoto = {}, onPickMedia = {},
            onRemoveEvidence = {}, onSubmit = {},
        )
    }
}
