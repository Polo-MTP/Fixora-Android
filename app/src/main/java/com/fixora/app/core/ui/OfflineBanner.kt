package com.fixora.app.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fixora.app.R
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Navy950
import com.fixora.app.core.designsystem.theme.Navy200
import com.fixora.app.core.designsystem.theme.White

/** Aviso abajo de la pantalla mientras no hay internet, con lo que no se puede hacer. */
@Composable
fun OfflineBanner(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(12.dp)
                .fillMaxWidth()
                .background(Navy950, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(stringResource(R.string.offline_title), style = MaterialTheme.typography.labelLarge, color = White)
            Text(stringResource(R.string.offline_message), style = MaterialTheme.typography.bodySmall, color = Navy200)
        }
    }
}

@Preview
@Composable
private fun OfflineBannerPreview() {
    FixoraTheme { OfflineBanner(visible = true) }
}
