package com.fixora.app.feature.providerprofile.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.fixora.app.BuildConfig
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
import com.fixora.app.core.designsystem.theme.SuccessGreen
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.core.ui.PhotoViewer
import com.fixora.app.core.ui.SystemBarsAppearance
import com.fixora.app.core.ui.message
import com.fixora.app.feature.account.ui.components.LoadErrorNotice
import com.fixora.app.feature.map.domain.MapProvider
import com.fixora.app.feature.map.ui.CameraRequest
import com.fixora.app.feature.map.ui.ProvidersMap
import com.fixora.app.feature.map.ui.serviceIcon
import com.fixora.app.feature.payment.ui.OutlineActionButton
import com.fixora.app.feature.portfolio.domain.PortfolioWork
import com.fixora.app.feature.providerprofile.domain.BasePrice
import com.fixora.app.feature.providerprofile.domain.DaySchedule
import com.fixora.app.feature.providerprofile.domain.ProviderProfile
import com.fixora.app.feature.providerprofile.domain.ProviderService
import com.fixora.app.feature.providerprofile.domain.PublicCertificate
import com.fixora.app.feature.providerprofile.domain.ScheduleGroup
import com.fixora.app.feature.providerprofile.domain.ScheduleRules
import kotlin.math.roundToInt
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MapHeight = 260.dp
private val PanelOverlap = 28.dp

/** Ficha pública del proveedor (ANDROID-11, Figma «Cliente · Ficha del proveedor»). */
@Composable
fun ProviderProfileScreen(
    onBack: () -> Unit,
    onRequestService: (branchId: String, categoryId: String?, distanceKm: String?) -> Unit,
    viewModel: ProviderProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    SystemBarsAppearance(lightIcons = false)
    val profile = state.profile
    val error = state.error
    Box(
        Modifier
            .fillMaxSize()
            .background(Mist50),
    ) {
        val position = profile?.position
        if (position != null) {
            val marker = remember(profile) { profile.asMapMarker() }
            ProvidersMap(
                styleUrl = BuildConfig.MAP_STYLE_URL,
                providers = listOf(marker),
                category = null,
                selectedId = marker.id,
                userLocation = null,
                camera = CameraRequest(position, zoom = 14.5, id = 1),
                bottomPadding = PanelOverlap,
                onSelect = {},
                onCameraIdle = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MapHeight + PanelOverlap),
            )
        }

        when {
            profile != null -> ProfilePanel(
                state = state,
                profile = profile,
                onTabSelected = viewModel::onTabSelected,
                onMoreReviews = viewModel::loadMoreReviews,
                onRequestService = { onRequestService(profile.id, state.categoryId, state.distanceKm) },
                modifier = Modifier.padding(top = if (position != null) MapHeight else 72.dp),
            )
            error != null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { LoadErrorNotice(error, onRetry = viewModel::retry) }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Navy800)
            }
        }

        RoundBackButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp),
        )
    }
}

@Composable
private fun RoundBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.auth_back)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(White)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_forward),
            contentDescription = null,
            tint = Navy900,
            modifier = Modifier
                .size(20.dp)
                .rotate(180f),
        )
    }
}

/** Panel blanco con los datos; «Solicitar servicio» queda fijo abajo. */
@Composable
private fun ProfilePanel(
    state: ProviderProfileUiState,
    profile: ProviderProfile,
    onTabSelected: (ProfileTab) -> Unit,
    onRequestService: () -> Unit,
    modifier: Modifier = Modifier,
    onMoreReviews: () -> Unit = {},
) {
    Column(
        modifier
            .fillMaxSize()
            .shadow(12.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(White)
            .navigationBarsPadding(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 40.dp, height = 5.dp)
                    .background(Mist200, CircleShape),
            )
            ProfileHeader(profile, state.distanceKm)
            StatsRow(profile, state.basePrice)
            ProfileTabs(state.tab, profile, onTabSelected)
            when (state.tab) {
                ProfileTab.Info -> InfoTab(profile)
                ProfileTab.Reviews -> ReviewsTab(state, profile, onMoreReviews)
                ProfileTab.Works -> WorksTab(profile.works)
                ProfileTab.Certificates -> CertificatesTab(profile.certificates)
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp, top = 8.dp),
        ) {
            if (!profile.acceptingRequests) {
                Text(
                    text = stringResource(R.string.provider_profile_not_accepting),
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate500,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FixoraPrimaryButton(
                text = stringResource(R.string.provider_profile_request),
                onClick = onRequestService,
                enabled = profile.acceptingRequests,
            )
        }
    }
}

@Composable
private fun ProfileHeader(profile: ProviderProfile, distanceKm: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (profile.independent) Orange500 else Navy900),
        ) {
            val service = profile.services.singleOrNull()
            if (service != null) {
                Icon(painterResource(serviceIcon("", service.name)), contentDescription = null, tint = White, modifier = Modifier.size(28.dp))
            } else {
                Text(initials(profile.title), style = MaterialTheme.typography.titleLarge, color = White)
            }
            if (profile.logoUrl != null) {
                AsyncImage(
                    model = profile.logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = profile.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Navy900,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (profile.verified) {
                    Icon(
                        painterResource(R.drawable.ic_verified),
                        contentDescription = stringResource(R.string.provider_profile_verified),
                        tint = SuccessGreen,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            val parts = listOfNotNull(
                if (profile.independent) stringResource(R.string.map_provider_independent) else profile.name,
                profile.services.singleOrNull()?.name,
                distanceKm?.let { stringResource(R.string.provider_profile_distance, it) },
            )
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Slate500)
        }
    }
}

/** Calificación, trabajos hechos y precio base, como en el diseño. */
@Composable
private fun StatsRow(profile: ProviderProfile, basePrice: BasePrice?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, Mist200, RoundedCornerShape(18.dp))
            .padding(vertical = 14.dp),
    ) {
        Stat(
            value = if (profile.hasReviews) formatDecimal(profile.rating) else "—",
            label = if (profile.hasReviews) {
                pluralStringResource(R.plurals.provider_profile_reviews, profile.reviewsCount, profile.reviewsCount)
            } else {
                stringResource(R.string.map_provider_no_reviews)
            },
            star = profile.hasReviews,
        )
        StatDivider()
        Stat(value = profile.totalJobs.toString(), label = stringResource(R.string.provider_profile_jobs_label))
        StatDivider()
        Stat(
            value = basePrice?.let { stringResource(R.string.provider_profile_price_value, wholePrice(it.amount)) } ?: "—",
            label = stringResource(
                if (basePrice?.isMinimum == true) R.string.provider_profile_price_from_label else R.string.provider_profile_price_label,
            ),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Stat(value: String, label: String, star: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Navy900)
            if (star) Icon(painterResource(R.drawable.ic_star), contentDescription = null, tint = Orange500, modifier = Modifier.size(18.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = Slate500, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StatDivider() {
    Box(
        Modifier
            .size(width = 1.dp, height = 40.dp)
            .background(Mist200),
    )
}

@Composable
private fun ProfileTabs(selected: ProfileTab, profile: ProviderProfile, onSelect: (ProfileTab) -> Unit) {
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            ProfileTab.entries.forEach { tab ->
                val text = when (tab) {
                    ProfileTab.Info -> stringResource(R.string.provider_profile_tab_info)
                    ProfileTab.Reviews -> stringResource(R.string.provider_profile_tab_reviews, profile.reviewsCount)
                    ProfileTab.Works -> stringResource(R.string.provider_profile_tab_works, profile.works.size)
                    ProfileTab.Certificates -> stringResource(R.string.provider_profile_tab_certificates, profile.certificates.size)
                }
                val isSelected = tab == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(role = Role.Tab) { onSelect(tab) }
                        .semantics { this.selected = isSelected },
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                        color = if (isSelected) Navy900 else Slate500,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Box(
                        Modifier
                            .size(width = 28.dp, height = 2.dp)
                            .background(if (isSelected) Navy900 else White),
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Mist200),
        )
    }
}

@Composable
private fun InfoTab(profile: ProviderProfile) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (profile.services.isNotEmpty()) {
            Text(stringResource(R.string.provider_profile_services), style = MaterialTheme.typography.titleSmall, color = Navy900)
            profile.services.forEach { ServiceRow(it) }
        }
        if (profile.plates.isNotEmpty()) {
            InfoRow(
                icon = R.drawable.ic_vehicle,
                label = stringResource(R.string.provider_profile_vehicle),
                value = stringResource(R.string.provider_profile_plates, profile.plates.joinToString(", ")),
            )
        }
        InfoRow(
            icon = R.drawable.ic_schedule,
            label = stringResource(R.string.provider_profile_schedule),
            value = scheduleText(profile.schedules),
        )
        val area = listOf(profile.colony, profile.municipality).filter { it.isNotBlank() }.joinToString(", ")
        if (area.isNotEmpty()) {
            InfoRow(icon = R.drawable.ic_place, label = stringResource(R.string.provider_profile_area), value = area)
        }
        if (profile.description.isNotEmpty()) {
            InfoRow(icon = R.drawable.ic_info, label = stringResource(R.string.provider_profile_about), value = profile.description)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Mist50)
                .padding(14.dp),
        ) {
            Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = Slate500, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.provider_profile_price_note), style = MaterialTheme.typography.bodySmall, color = Slate500)
        }
    }
}

@Composable
private fun ServiceRow(service: ProviderService) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBox(serviceIcon("", service.name))
        Column(Modifier.weight(1f)) {
            Text(service.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = Navy900)
            Text(
                stringResource(R.string.provider_profile_service_duration, service.durationMinutes),
                style = MaterialTheme.typography.labelSmall,
                color = Slate500,
            )
        }
        Text(
            stringResource(R.string.provider_profile_price_value, wholePrice(service.basePrice)),
            style = MaterialTheme.typography.titleMedium,
            color = Navy900,
        )
    }
}

@Composable
private fun InfoRow(@DrawableRes icon: Int, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBox(icon)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Slate500)
            Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Navy900)
        }
    }
}

@Composable
private fun IconBox(@DrawableRes icon: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Mist50),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Navy800, modifier = Modifier.size(20.dp))
    }
}

/** Horario en renglones: «Lun a vie, 8:00 a 18:00», «Todos los días, 24 horas». */
@Composable
private fun scheduleText(schedules: List<DaySchedule>): String {
    val groups = ScheduleRules.group(schedules)
    if (groups.isEmpty()) return stringResource(R.string.provider_profile_no_schedule)
    val days = stringArrayResource(R.array.week_days_short)
    val everyDay = stringResource(R.string.schedule_every_day)
    val allDayFormat = stringResource(R.string.schedule_all_day)
    val hoursFormat = stringResource(R.string.schedule_hours)
    val rangeFormat = stringResource(R.string.schedule_day_range)
    return groups.joinToString("\n") { group ->
        val dayText = when {
            group.coversWholeWeek() -> everyDay
            group.singleDay -> days[group.firstDay]
            else -> String.format(rangeFormat, days[group.firstDay], days[group.lastDay].lowercase())
        }
        if (group.allDay) {
            String.format(allDayFormat, dayText)
        } else {
            String.format(hoursFormat, dayText, ScheduleRules.shortTime(group.open), ScheduleRules.shortTime(group.close))
        }
    }
}

private fun ScheduleGroup.coversWholeWeek() = firstDay == 1 && lastDay == 0

/** Promedio y las reseñas: primero las últimas de la ficha y, con «Ver más», la lista completa. */
@Composable
private fun ReviewsTab(state: ProviderProfileUiState, profile: ProviderProfile, onMoreReviews: () -> Unit) {
    if (!profile.hasReviews || state.shownReviews.isEmpty()) {
        EmptyTabText(stringResource(R.string.provider_profile_no_reviews))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(formatDecimal(profile.rating), style = MaterialTheme.typography.headlineMedium, color = Navy900)
            Column {
                Stars(rating = profile.rating.roundToInt())
                Text(
                    pluralStringResource(R.plurals.provider_profile_reviews, profile.reviewsCount, profile.reviewsCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate500,
                )
            }
        }
        state.shownReviews.forEach { review ->
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Mist50)
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        review.author.ifBlank { stringResource(R.string.provider_profile_review_author_unknown) },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Navy900,
                        modifier = Modifier.weight(1f),
                    )
                    Stars(rating = review.rating)
                }
                review.createdAt?.let {
                    Text(ReviewDateFormat.format(it.atZone(ZoneId.systemDefault())), style = MaterialTheme.typography.labelSmall, color = Slate500)
                }
                if (review.comment.isNotEmpty()) {
                    Text(review.comment, style = MaterialTheme.typography.bodyMedium, color = Slate500)
                }
                if (review.photoUrls.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        review.photoUrls.forEach { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Mist200),
                            )
                        }
                    }
                }
            }
        }
        if (state.hasMoreReviews) {
            Text(
                stringResource(R.string.provider_profile_reviews_latest, state.shownReviews.size, profile.reviewsCount),
                style = MaterialTheme.typography.labelSmall,
                color = Slate500,
            )
            state.reviewsError?.let { Text(it.message(), style = MaterialTheme.typography.bodySmall, color = ErrorRed) }
            OutlineActionButton(
                text = stringResource(R.string.provider_profile_reviews_more),
                onClick = onMoreReviews,
                loading = state.isLoadingReviews,
            )
        }
    }
}

private val ReviewDateFormat = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-MX"))

/** Cinco estrellas, llenas hasta [rating]; se lee como «4 de 5 estrellas». */
@Composable
private fun Stars(rating: Int) {
    val description = stringResource(R.string.provider_profile_stars, rating)
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        repeat(5) { index ->
            Icon(
                painterResource(R.drawable.ic_star),
                contentDescription = null,
                tint = if (index < rating) Orange500 else Mist200,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun WorksTab(works: List<PortfolioWork>) {
    if (works.isEmpty()) {
        EmptyTabText(stringResource(R.string.provider_profile_no_works))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        works.forEach { WorkItem(it) }
    }
}

@Composable
private fun WorkItem(work: PortfolioWork) {
    var viewerIndex by rememberSaveable(work.id) { mutableStateOf<Int?>(null) }
    viewerIndex?.let { PhotoViewer(photos = work.photoUrls, startIndex = it, onDismiss = { viewerIndex = null }) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(work.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = Navy900)
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
                            .size(104.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Mist200)
                            .clickable(onClickLabel = openLabel) { viewerIndex = index },
                    )
                }
            }
        }
    }
}

@Composable
private fun CertificatesTab(certificates: List<PublicCertificate>) {
    if (certificates.isEmpty()) {
        EmptyTabText(stringResource(R.string.provider_profile_no_certificates))
        return
    }
    val context = LocalContext.current
    val noAppMessage = stringResource(R.string.provider_profile_certificate_no_app)
    var photoUrl by rememberSaveable { mutableStateOf<String?>(null) }
    photoUrl?.let { PhotoViewer(photos = listOf(it), onDismiss = { photoUrl = null }) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        certificates.forEach { certificate ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button) {
                        if (certificate.isPdf) {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, certificate.url.toUri()))
                            } catch (e: ActivityNotFoundException) {
                                Toast.makeText(context, noAppMessage, Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            photoUrl = certificate.url
                        }
                    }
                    .padding(vertical = 4.dp),
            ) {
                IconBox(R.drawable.ic_certificate)
                Column(Modifier.weight(1f)) {
                    Text(
                        certificate.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Navy900,
                    )
                    // La API guarda el tipo de documento, no un título: varios pueden llamarse igual.
                    Text(
                        stringResource(if (certificate.isPdf) R.string.provider_profile_certificate_pdf else R.string.provider_profile_certificate_photo),
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate500,
                    )
                }
                Text(stringResource(R.string.provider_profile_certificate_open), style = MaterialTheme.typography.labelLarge, color = Navy800)
            }
        }
    }
}

@Composable
private fun EmptyTabText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Slate500, modifier = Modifier.padding(vertical = 8.dp))
}

/** Para el marcador del mapa de arriba. */
private fun ProviderProfile.asMapMarker() = MapProvider(
    id = id,
    name = name,
    businessName = businessName,
    independent = independent,
    logoUrl = null,
    position = checkNotNull(position),
    distanceKm = 0.0,
    rating = rating,
    reviewsCount = reviewsCount,
    basePrice = null,
    openNow = acceptingRequests,
)

private fun initials(text: String): String {
    val words = text.split(' ').filter { it.isNotBlank() }
    return (if (words.size >= 2) "${words[0].first()}${words[1].first()}" else text.take(2)).uppercase()
}

/** «350.00» → «350»; «349.50» se queda con decimales. */
private fun wholePrice(amount: String): String = amount.removeSuffix(".00")

private fun formatDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ProviderProfilePreview() {
    FixoraTheme {
        ProfilePanel(
            state = ProviderProfileUiState(isLoading = false, distanceKm = "1.2", basePrice = BasePrice("350.00", false)),
            profile = ProviderProfile(
                id = "1", name = "Juan Hernández", businessName = "Plomería Hernández", independent = true,
                description = "", logoUrl = null, verified = true, plates = listOf("FHR-482-B"), rating = 4.8,
                reviewsCount = 126, totalJobs = 312, acceptingRequests = true, colony = "Centro", municipality = "Torreón",
                position = null, schedules = (1..6).map { DaySchedule(it, "08:00", "20:00") },
                services = listOf(ProviderService("1", "Plomería", "350.00", 60)), works = emptyList(), certificates = emptyList(),
            ),
            onTabSelected = {},
            onRequestService = {},
        )
    }
}
