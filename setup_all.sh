#!/bin/bash
set -e

echo "==> 1. Удаление старых Java-файлов..."
rm -f app/src/main/java/com/appletv/launcher/MainActivity.java

echo "==> 2. Создание каталога исходников Kotlin..."
mkdir -p app/src/main/java/com/appletv/launcher

echo "==> 3. Запись MainActivity.kt на Kotlin + Jetpack Compose..."
cat << 'KOTLIN_EOF' > app/src/main/java/com/appletv/launcher/MainActivity.kt
package com.appletv.launcher

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppModel(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val dominantColor: Color
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainLauncherScreen()
        }
    }
}

@Composable
fun MainLauncherScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs: SharedPreferences = remember {
        context.getSharedPreferences("macos_tv_prefs", Context.MODE_PRIVATE)
    }

    var installedApps by remember { mutableStateOf<List<AppModel>>(emptyList()) }
    var favoritePackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var activeApp by remember { mutableStateOf<AppModel?>(null) }
    var showSettingsModal by remember { mutableStateOf(false) }

    var currentTimeString by remember { mutableStateOf("") }
    var currentDateString by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale("ru"))
        val now = Date()
        currentTimeString = timeFormat.format(now)
        val formattedDate = dateFormat.format(now)
        currentDateString = formattedDate.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru")) else it.toString() }

        scope.launch {
            val apps = scanInstalledApplications(context)
            val savedFavs = prefs.getStringSet("favorite_packages", null)
            val initialFavs = if (savedFavs.isNullOrEmpty()) {
                apps.take(6).map { it.packageName }.toSet()
            } else {
                savedFavs
            }

            installedApps = apps
            favoritePackages = initialFavs
            if (activeApp == null && apps.isNotEmpty()) {
                activeApp = apps.firstOrNull { it.packageName in initialFavs } ?: apps.first()
            }
            prefs.edit().putStringSet("favorite_packages", initialFavs).apply()
        }
    }

    val dockApps = installedApps.filter { it.packageName in favoritePackages }
    val remainingAppRows = remainingApps.chunked(6)

    val ambientColor by animateColorAsState(
        targetValue = activeApp?.dominantColor?.copy(alpha = 0.45f) ?: Color(0x331E2A38),
        animationSpec = tween(durationMillis = 350),
        label = "ambientColor"
    )

    BackHandler(enabled = showSettingsModal) {
        showSettingsModal = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050508))
    ) {
        // --- TOP SHELF PREVIEW BANNER ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(540.dp)
                .align(Alignment.TopCenter)
        ) {
            Image(
                painter = rememberDrawablePainter(drawable = context.getDrawable(R.drawable.bg_poster_studio)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ambientColor)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x66050508),
                                Color(0x99050508),
                                Color(0xFF050508)
                            ),
                            startY = 180f,
                            endY = 1400f
                        )
                    )
            )
        }

        // --- ВЕРХНИЙ СТАТУС-БАР С ЧАСАМИ И НАСТРОЙКАМИ (macOS Pill) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp, end = 48.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopStatusPill(
                timeText = currentTimeString,
                dateText = currentDateString,
                onSettingsClick = { showSettingsModal = true }
            )
        }

        // --- ГЛАВНАЯ ИЕРАРХИЯ: TOP SHELF -> DOCK -> GRID ВСЕХ ОСТАЛЬНЫХ ---
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 72.dp)
        ) {
            // 1. Зона Top Shelf с крупным названием программы
            item {
                Spacer(modifier = Modifier.height(310.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 64.dp, bottom = 28.dp)
                ) {
                    Text(
                        text = activeApp?.label ?: "macOS TV",
                        color = Color.White,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = (-1.2).sp
                    )
                    Text(
                        text = if (activeApp != null && activeAppВот **готовый однострочный скрипт**. Скопируй этот блок целиком и просто вставь в терминал WSL. Он сам всё запишет, скомпилирует и сразу сделает 
