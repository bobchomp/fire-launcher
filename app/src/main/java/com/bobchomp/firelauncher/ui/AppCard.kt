package com.bobchomp.firelauncher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import com.bobchomp.firelauncher.AppArt
import com.bobchomp.firelauncher.AppArtLoader
import com.bobchomp.firelauncher.LauncherApp

private val CardShape = RoundedCornerShape(8.dp)

/** A 16:9 tile: the app's banner, or its icon and name when it has no banner. */
@Composable
fun AppCard(
    app: LauncherApp,
    artLoader: AppArtLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val art by produceState<AppArt?>(artLoader.cached(app), app.key) {
        if (value == null) value = artLoader.load(app)
    }

    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(16f / 9f),
        shape = CardDefaults.shape(CardShape),
        colors = CardDefaults.colors(containerColor = CardBackground),
        scale = CardDefaults.scale(focusedScale = 1.1f),
        border = CardDefaults.border(
            focusedBorder = Border(border = BorderStroke(3.dp, Color.White), shape = CardShape),
        ),
    ) {
        val current = art
        if (current != null && current.isBanner) {
            Image(
                bitmap = current.image,
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(8.dp),
                ) {
                    if (current != null) {
                        Image(
                            bitmap = current.image,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    Text(
                        text = app.label,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}
