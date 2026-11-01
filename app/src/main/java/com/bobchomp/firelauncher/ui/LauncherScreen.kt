package com.bobchomp.firelauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.bobchomp.firelauncher.AppArtLoader
import com.bobchomp.firelauncher.LauncherApp
import com.bobchomp.firelauncher.R

private const val COLUMNS = 5

// Room around the grid so the 1.1x focus zoom isn't clipped at the edges.
private val FocusRoom = 14.dp

@Composable
fun LauncherScreen(
    apps: List<LauncherApp>?,
    artLoader: AppArtLoader,
    homePresses: Int,
    homeRedirectOn: Boolean,
    onLaunch: (LauncherApp) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            // TV safe area: 48dp sides, 27dp top/bottom, minus the grid's focus room.
            .padding(horizontal = 48.dp - FocusRoom, vertical = 27.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = FocusRoom),
        ) {
            Clock()
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings)) }
        }

        if (!homeRedirectOn) {
            Text(
                text = stringResource(R.string.home_off_hint),
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = FocusRoom).padding(top = 8.dp),
            )
        }

        when {
            apps == null -> Unit // Still loading; usually a few frames.
            apps.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_apps), color = TextSecondary)
            }
            else -> AppGrid(apps, artLoader, homePresses, onLaunch)
        }
    }
}

@Composable
private fun AppGrid(
    apps: List<LauncherApp>,
    artLoader: AppArtLoader,
    homePresses: Int,
    onLaunch: (LauncherApp) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val firstApp = remember { FocusRequester() }

    // Focus the first app on start, and again whenever Home is pressed while we're showing.
    LaunchedEffect(homePresses) {
        gridState.scrollToItem(0)
        withFrameNanos { } // Let the first item attach before asking for focus.
        runCatching { firstApp.requestFocus() }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
        state = gridState,
        contentPadding = PaddingValues(FocusRoom),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize().padding(top = 12.dp),
    ) {
        itemsIndexed(apps, key = { _, app -> app.key }) { index, app ->
            AppCard(
                app = app,
                artLoader = artLoader,
                onClick = { onLaunch(app) },
                modifier = if (index == 0) Modifier.focusRequester(firstApp) else Modifier,
            )
        }
    }
}
