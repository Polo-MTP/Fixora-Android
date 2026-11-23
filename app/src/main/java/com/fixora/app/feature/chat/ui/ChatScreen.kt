package com.fixora.app.feature.chat.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import coil3.compose.AsyncImage
import com.fixora.app.R
import com.fixora.app.core.designsystem.theme.ErrorRed
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Mist200
import com.fixora.app.core.designsystem.theme.Mist50
import com.fixora.app.core.designsystem.theme.Navy800
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Orange500
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.core.ui.PhotoViewer
import com.fixora.app.core.ui.SystemBarsAppearance
import com.fixora.app.feature.account.ui.components.LoadErrorNotice
import com.fixora.app.feature.chat.domain.ChatMessage
import com.fixora.app.feature.chat.domain.ChatPhoto
import com.fixora.app.feature.chat.domain.ChatReply
import com.fixora.app.feature.chat.domain.ChatRules
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Chat de la solicitud entre el cliente y el técnico (ANDROID-23). */
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LifecycleStartEffect(Unit) {
        viewModel.onStart()
        onStopOrDispose { viewModel.onStop() }
    }

    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        cameraUri?.takeIf { taken }?.let { viewModel.onPhotosPicked(listOf(it.toString())) }
    }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(ChatRules.MAX_PHOTOS)) { uris ->
        viewModel.onPhotosPicked(uris.map(Uri::toString))
    }
    val noCamera = stringResource(R.string.profile_photo_no_camera)

    ChatContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onDraftChange = viewModel::onDraftChange,
        onSend = viewModel::send,
        onReply = viewModel::onReply,
        onCancelReply = viewModel::cancelReply,
        onPickPhotos = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onTakePhoto = {
            val uri = newChatPhotoUri(context)
            cameraUri = uri
            try {
                takePicture.launch(uri)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, noCamera, Toast.LENGTH_SHORT).show()
            }
        },
        onRemovePhoto = viewModel::removePhoto,
        onRetrySend = viewModel::retrySend,
        onDiscard = viewModel::discard,
        onLoadOlder = viewModel::loadOlder,
    )
}

/** Un renglón de la lista: un mensaje confirmado, uno pendiente o la fecha que separa los días. */
private sealed interface ChatRow {
    val key: String

    data class Day(val date: LocalDate) : ChatRow {
        override val key = "day-$date"
    }

    data class Sent(val message: ChatMessage) : ChatRow {
        override val key = "m-${message.id}"
    }

    data class Pending(val message: OutgoingMessage) : ChatRow {
        override val key = "p-${message.clientUuid}"
    }
}

/** La lista va de abajo hacia arriba: el primer renglón es el más reciente. */
private fun rowsOf(state: ChatUiState): List<ChatRow> {
    val rows = mutableListOf<ChatRow>()
    state.outgoing.asReversed().forEach { rows += ChatRow.Pending(it) }
    val zone = ZoneId.systemDefault()
    val messages = state.messages.asReversed()
    messages.forEachIndexed { index, message ->
        rows += ChatRow.Sent(message)
        val day = message.createdAt?.atZone(zone)?.toLocalDate()
        val olderDay = messages.getOrNull(index + 1)?.createdAt?.atZone(zone)?.toLocalDate()
        if (day != null && day != olderDay) rows += ChatRow.Day(day)
    }
    return rows
}

@Composable
private fun ChatContent(
    state: ChatUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onReply: (ChatMessage) -> Unit,
    onCancelReply: () -> Unit,
    onPickPhotos: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemovePhoto: (String) -> Unit,
    onRetrySend: (String) -> Unit,
    onDiscard: (String) -> Unit,
    onLoadOlder: () -> Unit,
) {
    SystemBarsAppearance(lightIcons = false)
    var viewing by remember { mutableStateOf<Pair<List<Any>, Int>?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Mist50)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        TopBar(state, onBack)
        if (state.reconnecting) {
            Text(
                stringResource(R.string.chat_reconnecting),
                style = MaterialTheme.typography.labelMedium,
                color = Slate500,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Mist200.copy(alpha = 0.5f))
                    .padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val error = state.loadError
            when {
                state.isLoading && state.messages.isEmpty() -> CircularProgressIndicator(
                    color = Navy800,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp).align(Alignment.Center),
                )
                error != null -> Box(Modifier.padding(24.dp)) { LoadErrorNotice(error, onRetry = onRetry) }
                state.messages.isEmpty() && state.outgoing.isEmpty() -> Text(
                    stringResource(if (state.canSend) R.string.chat_empty else R.string.chat_empty_closed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate500,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
                else -> MessageList(
                    state = state,
                    onReply = onReply,
                    onRetrySend = onRetrySend,
                    onDiscard = onDiscard,
                    onLoadOlder = onLoadOlder,
                    onOpenPhotos = { photos, index -> viewing = photos to index },
                )
            }
        }
        if (state.canSend) {
            Composer(state, onDraftChange, onSend, onCancelReply, onPickPhotos, onTakePhoto, onRemovePhoto)
        } else {
            Text(
                stringResource(R.string.chat_closed),
                style = MaterialTheme.typography.bodySmall,
                color = Slate500,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(White)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }
    viewing?.let { (photos, index) -> PhotoViewer(photos, startIndex = index, onDismiss = { viewing = null }) }
}

@Composable
private fun TopBar(state: ChatUiState, onBack: () -> Unit) {
    Column(Modifier.background(White)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onBack),
            ) {
                Icon(
                    painterResource(R.drawable.ic_arrow_forward),
                    contentDescription = stringResource(R.string.auth_back),
                    tint = Navy900,
                    modifier = Modifier.size(22.dp).rotate(180f),
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .background(Mist50, CircleShape)
                    .border(1.dp, Mist200, CircleShape),
            ) {
                Text(initials(state.otherName.orEmpty()), style = MaterialTheme.typography.titleSmall, color = Navy800)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    state.otherName ?: stringResource(R.string.chat_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Navy900,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                state.categoryName?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Slate500, maxLines = 1)
                }
            }
        }
        HorizontalDivider(color = Mist200)
    }
}

@Composable
private fun MessageList(
    state: ChatUiState,
    onReply: (ChatMessage) -> Unit,
    onRetrySend: (String) -> Unit,
    onDiscard: (String) -> Unit,
    onLoadOlder: () -> Unit,
    onOpenPhotos: (List<Any>, Int) -> Unit,
) {
    val rows = rowsOf(state)
    val listState = rememberLazyListState()
    val byId = state.messages.associateBy { it.id }

    // Al llegar un mensaje nuevo (o mandar uno) baja al final si ya estaba ahí.
    val newest = rows.firstOrNull()?.key
    LaunchedEffect(newest) {
        if (listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
    }
    // Cerca del mensaje más viejo pide los anteriores.
    val nearTop by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 } }
    LaunchedEffect(listState, rows.size) {
        snapshotFlow { nearTop }
            .distinctUntilChanged()
            .filter { it >= rows.size - 3 }
            .collect { onLoadOlder() }
    }

    LazyColumn(
        state = listState,
        reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        items(rows, key = { it.key }) { row ->
            when (row) {
                is ChatRow.Day -> DayLabel(row.date)
                is ChatRow.Sent -> {
                    val mine = state.isMine(row.message)
                    Bubble(
                        mine = mine,
                        text = row.message.text,
                        reply = row.message.replyTo,
                        replyMine = row.message.replyTo?.let { it.senderId == state.myUserId } ?: false,
                        replyOriginal = row.message.replyTo?.let { byId[it.id] },
                        photos = row.message.photos.map(ChatPhoto::url),
                        time = row.message.createdAt,
                        status = if (mine && row.message.id == state.lastReadMineId) stringResource(R.string.chat_read) else null,
                        onLongPress = { if (state.canSend) onReply(row.message) },
                        onOpenPhoto = { index -> onOpenPhotos(row.message.photos.map(ChatPhoto::url), index) },
                    )
                }
                is ChatRow.Pending -> Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                    Bubble(
                        mine = true,
                        text = row.message.text,
                        reply = row.message.replyTo,
                        replyMine = row.message.replyTo?.let { it.senderId == state.myUserId } ?: false,
                        replyOriginal = row.message.replyTo?.let { byId[it.id] },
                        photos = row.message.photoUris,
                        time = null,
                        status = if (row.message.failed) null else stringResource(R.string.chat_sending),
                        pending = true,
                        onLongPress = {},
                        onOpenPhoto = { index -> onOpenPhotos(row.message.photoUris, index) },
                    )
                    if (row.message.failed) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 4.dp, end = 4.dp)) {
                            Text(
                                stringResource(R.string.chat_failed_retry),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = ErrorRed,
                                modifier = Modifier.clickable(role = Role.Button) { onRetrySend(row.message.clientUuid) },
                            )
                            Text(
                                stringResource(R.string.chat_failed_discard),
                                style = MaterialTheme.typography.labelMedium,
                                color = Slate500,
                                modifier = Modifier.clickable(role = Role.Button) { onDiscard(row.message.clientUuid) },
                            )
                        }
                    }
                }
            }
        }
        if (state.isLoadingOlder) {
            item(key = "older") {
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Navy800, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun DayLabel(date: LocalDate) {
    val today = LocalDate.now()
    val text = when (date) {
        today -> stringResource(R.string.chat_today)
        today.minusDays(1) -> stringResource(R.string.chat_yesterday)
        else -> DayFormat.format(date)
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = Slate500,
            modifier = Modifier
                .background(Mist200.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    mine: Boolean,
    text: String?,
    reply: ChatReply?,
    replyMine: Boolean,
    replyOriginal: ChatMessage?,
    photos: List<String>,
    time: Instant?,
    status: String?,
    onLongPress: () -> Unit,
    onOpenPhoto: (Int) -> Unit,
    pending: Boolean = false,
) {
    val background = if (mine) Navy900 else White
    val content = if (mine) White else Navy900
    val secondary = if (mine) White.copy(alpha = 0.7f) else Slate500
    Row(
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp))
                .background(if (pending) background.copy(alpha = 0.85f) else background)
                .then(if (mine) Modifier else Modifier.border(1.dp, Mist200, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)))
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            reply?.let { ReplyQuote(it, replyMine, replyOriginal, mine) }
            if (photos.isNotEmpty()) PhotoGrid(photos, onOpenPhoto)
            text?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = content) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.align(Alignment.End)) {
                time?.let { Text(TimeFormat.format(it.atZone(ZoneId.systemDefault())), style = MaterialTheme.typography.labelSmall, color = secondary) }
                status?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = secondary) }
            }
        }
    }
}

/** El mensaje al que se responde, resumido arriba de la burbuja. */
@Composable
private fun ReplyQuote(reply: ChatReply, replyMine: Boolean, original: ChatMessage?, insideMine: Boolean) {
    val accent = if (insideMine) Orange500 else Navy800
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (insideMine) White.copy(alpha = 0.12f) else Mist50)
            .padding(end = 10.dp),
    ) {
        Box(Modifier.width(3.dp).height(38.dp).background(accent))
        Column(Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) {
            Text(
                if (replyMine) stringResource(R.string.chat_reply_you) else original?.senderName ?: stringResource(R.string.chat_reply_other),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = accent,
                maxLines = 1,
            )
            Text(
                reply.text ?: stringResource(R.string.chat_photo),
                style = MaterialTheme.typography.bodySmall,
                color = if (insideMine) White.copy(alpha = 0.8f) else Slate500,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PhotoGrid(photos: List<String>, onOpen: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        photos.chunked(2).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEachIndexed { index, url ->
                    AsyncImage(
                        model = url,
                        contentDescription = stringResource(R.string.chat_photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(if (photos.size == 1) 220.dp else 120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Mist200)
                            .clickable(role = Role.Image) { onOpen(rowIndex * 2 + index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Composer(
    state: ChatUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancelReply: () -> Unit,
    onPickPhotos: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemovePhoto: (String) -> Unit,
) {
    Column(Modifier.background(White)) {
        HorizontalDivider(color = Mist200)
        state.replyTo?.let { reply ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp),
            ) {
                Icon(painterResource(R.drawable.ic_reply), contentDescription = null, tint = Navy800, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(
                        if (state.isMine(reply)) stringResource(R.string.chat_reply_you) else reply.senderName ?: stringResource(R.string.chat_reply_other),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Navy800,
                    )
                    Text(
                        reply.text ?: stringResource(R.string.chat_photo),
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SmallIconButton(R.drawable.ic_close, stringResource(R.string.chat_reply_cancel), onCancelReply)
            }
        }
        if (state.draftPhotos.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                items(state.draftPhotos, key = { it }) { uri ->
                    Box {
                        AsyncImage(
                            model = uri,
                            contentDescription = stringResource(R.string.chat_photo),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(Mist200),
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(22.dp)
                                .background(Navy900.copy(alpha = 0.75f), CircleShape)
                                .clickable(role = Role.Button) { onRemovePhoto(uri) },
                        ) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.chat_photo_remove), tint = White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
        if (state.photoError) {
            Text(
                stringResource(R.string.chat_photo_error),
                style = MaterialTheme.typography.bodySmall,
                color = ErrorRed,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 6.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
        ) {
            val photosFull = state.draftPhotos.size >= ChatRules.MAX_PHOTOS
            SmallIconButton(R.drawable.ic_image, stringResource(R.string.chat_attach_gallery), onPickPhotos, enabled = !photosFull)
            SmallIconButton(R.drawable.ic_photo_camera, stringResource(R.string.chat_attach_camera), onTakePhoto, enabled = !photosFull)
            OutlinedTextField(
                value = state.draft,
                onValueChange = onDraftChange,
                placeholder = { Text(stringResource(R.string.chat_placeholder), style = MaterialTheme.typography.bodyMedium) },
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(22.dp),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Mist50,
                    unfocusedContainerColor = Mist50,
                    focusedBorderColor = Mist200,
                    unfocusedBorderColor = Mist200,
                    cursorColor = Navy800,
                    focusedTextColor = Navy900,
                    unfocusedTextColor = Navy900,
                    focusedPlaceholderColor = Slate500,
                    unfocusedPlaceholderColor = Slate500,
                ),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(bottom = 2.dp)
                    .size(46.dp)
                    .background(if (state.canSubmit) Orange500 else Mist200, CircleShape)
                    .clickable(enabled = state.canSubmit, role = Role.Button, onClick = onSend),
            ) {
                Icon(painterResource(R.drawable.ic_send), contentDescription = stringResource(R.string.chat_send), tint = White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun SmallIconButton(icon: Int, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Icon(painterResource(icon), contentDescription = description, tint = if (enabled) Navy800 else Mist200, modifier = Modifier.size(22.dp))
    }
}

private fun initials(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val DayFormat = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es-MX"))

/** Archivo nuevo en la caché para la foto de la cámara; borra los de veces anteriores. */
private fun newChatPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
    dir.listFiles()?.filter { it.name.startsWith("chat-") }?.forEach { it.delete() }
    val file = File(dir, "chat-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ChatPreview() {
    val now = Instant.parse("2026-11-23T18:00:00Z")
    FixoraTheme {
        ChatContent(
            state = ChatUiState(
                isLoading = false,
                otherName = "Juan Hernández",
                categoryName = "Plomería",
                myUserId = "29",
                messages = listOf(
                    ChatMessage("1", "9", "Juan Hernández", "Voy en camino, llego en 10 minutos.", null, emptyList(), null, now, now),
                    ChatMessage("2", "29", "Carla", "Perfecto, la puerta es la negra.", ChatReply("1", "9", "Voy en camino, llego en 10 minutos.", false), emptyList(), null, now, now.plusSeconds(60)),
                ),
                outgoing = listOf(OutgoingMessage("u1", "¿Trae la llave de paso?", null, emptyList(), failed = true)),
                draft = "",
            ),
            onBack = {}, onRetry = {}, onDraftChange = {}, onSend = {}, onReply = {}, onCancelReply = {},
            onPickPhotos = {}, onTakePhoto = {}, onRemovePhoto = {}, onRetrySend = {}, onDiscard = {}, onLoadOlder = {},
        )
    }
}
