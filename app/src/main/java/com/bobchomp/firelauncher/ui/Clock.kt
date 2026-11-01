package com.bobchomp.firelauncher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/** Time and date, updated on each minute boundary (one wake-up a minute, nothing more). */
@Composable
fun Clock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var time by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            // Formatters are rebuilt each tick so time zone and 12/24h changes show up.
            time = DateFormat.getTimeFormat(context).format(Date(now))
            val datePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd")
            date = DateFormat.format(datePattern, now).toString()
            delay(60_000L - now % 60_000L)
        }
    }

    Column(modifier) {
        Text(text = time, fontSize = 44.sp, fontWeight = FontWeight.Light)
        Text(text = date, fontSize = 18.sp, color = TextSecondary)
    }
}
