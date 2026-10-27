$(cat << 'INNER_EOF'
package com.appletv.launcher

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
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
import androidx.compose.ui.graphics.ImageBitmap
    import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppModel(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap, // Используем нативный ImageBitmap вместо Drawable
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
    val remainingApps = installedApps.filter { it.packageName !in favoritePackages }
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
        // --- 1. TOP SHELF: ПОСТЕР И ДИНАМИЧЕСКИЙ ЭМБИЕНТ-ФОН ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(560.dp)
                .align(Alignment.TopCenter)
        ) {
            Image(
                painter = painterResource(id = R.drawable.bg_poster_studio),
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
                                Color(0x55050508),
                                Color(0x88050508),
                                Color(0xFF050508)
                            ),
                            startY = 180f,
                            endY = 1400f
                        )
                    )
            )
        }

        // --- ВЕРХНИЙ СТАТУС-БАР (ВРЕМЯ, ДАТА, НАСТРОЙКИ) ---
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

        // --- ГЛАВНАЯ ИЕРАРХИЯ: TOP SHELF -> DOCK ВНИЗУ -> СЕТКА ЕЩЕ НИЖЕ ---
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Зона отступа под Top Shelf и крупное название активной программы
            item {
                Spacer(modifier = Modifier.height(320.dp))
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
                        text = if (activeApp != null && activeApp!!.packageName in favoritePackages) "Избранное в Dock" else "Библиотека программ",
                        color = Color(0x99FFFFFF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 2. ДОК ИЗБРАННОГО (центрированный ряд, frosted glass, сужен от краев)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 64.dp)
                        .padding(bottom = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color(0xE6252528),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .border(
                                border = BorderStroke(1.dp, Color(0x2BFFFFFF)),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(dockApps, key = { it.packageName }) { app ->
                                AppTileCard(
                                    app = app,
                                    onFocused = {
                                        activeApp = app
                                        SoundManager.getInstance(context).playFocus()
                                    },
                                    onClick = {
                                        SoundManager.getInstance(context).playClick()
                                        launchApplication(context, app.packageName)
                                    },
                                    onLongClick = {
                                        SoundManager.getInstance(context).playClick()
                                        val updated = favoritePackages.toMutableSet().apply { remove(app.packageName) }
                                        favoritePackages = updated
                                        prefs.edit().putStringSet("favorite_packages", updated).apply()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 3. ОСНОВНАЯ СЕТКА ВСЕХ ОСТАЛЬНЫХ ПРОГРАММ (СТРОГО НИЖЕ ДОКА, БЕЗ ДУБЛИКАТОВ)
            if (remainingAppRows.isNotEmpty()) {
                item {
                    Text(
                        text = "Другие приложения",
                        color = Color(0xB3FFFFFF),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp,
                        modifier = Modifier.padding(start = 64.dp, bottom = 16.dp, top = 8.dp)
                    )
                }

                items(remainingAppRows) { rowList ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 64.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        for (app in rowList) {
                            AppTileCard(
                                app = app,
                                onFocused = {
                                    activeApp = app
                                    SoundManager.getInstance(context).playFocus()
                                },
                                onClick = {
                                    SoundManager.getInstance(context).playClick()
                                    launchApplication(context, app.packageName)
                                },
                                onLongClick = {
                                    SoundManager.getInstance(context).playClick()
                                    val updated = favoritePackages.toMutableSet().apply { add(app.packageName) }
                                    favoritePackages = updated
                                    prefs.edit().putStringSet("favorite_packages", updated).apply()
                                }
                            )
                        }

                        val emptySlots = 6 - rowList.size
                        for (i in 0 until emptySlots) {
                            Spacer(modifier = Modifier.width(140.dp))
                        }
                    }
                }
            }
        }

        // --- МОДАЛЬНОЕ ОКНО НАСТРОЕК (МАСШТАБ ПОЛНОСТЬЮ ВЫРЕЗАН) ---
        AnimatedVisibility(
            visible = showSettingsModal,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200))
        ) {
            SettingsOverlayScreen(
                prefs = prefs,
                onDismiss = { showSettingsModal = false }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTileCard(
    app: AppModel,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused()
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.12f else 1.0f,
        animationSpec = tween(durationMillis = 180),
        label = "scale"
    )

    val glowIntensity by animateFloatAsState(
        targetValue = if (isFocused) 0.65f else 0.0f,
        animationSpec = tween(durationMillis = 220),
        label = "glowIntensity"
    )

    val glowNativeColor = remember(app.dominantColor, glowIntensity) {
        val r = (app.dominantColor.red * 255).toInt()
        val g = (app.dominantColor.green * 255).toInt()
        val b = (app.dominantColor.blue * 255).toInt()
        val a = (glowIntensity * 255).coerceIn(0f, 255f).toInt()
        android.graphics.Color.argb(a, r, g, b)
    }

    Box(
        modifier = Modifier
            .size(width = 140.dp, height = 82.dp)
            .scale(scale)
            .drawBehind {
                if (glowIntensity > 0f) {
                    drawIntoCanvas { canvas ->
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.TRANSPARENT
                            setShadowLayer(
                                36f,
                                0f,
                                10f,
                                glowNativeColor
                            )
                        }
                        canvas.nativeCanvas.drawRoundRect(
                            0f,
                            0f,
                            size.width,
                            size.height,
                            18.dp.toPx(),
                            18.dp.toPx(),
                            paint
                        )
                    }
                }
            }
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF3A3A3C))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center
    ) {
        // Нативный вывод Bitmap, избавление от библиотеки Accompanist
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier.size(50.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun TopStatusPill(
    timeText: String,
    dateText: String,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .background(
                color = Color(0xE6252528),
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = timeText,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp
            )
            Text(
                text = dateText,
                color = Color(0x88FFFFFF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal
            )
        }

        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(20.dp)
                .background(Color(0x33FFFFFF))
        )
        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (isFocused) Color.White else Color(0x26FFFFFF))
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        SoundManager.getInstance(context).playClick()
                        onSettingsClick()
                    }
                )
                .focusable(interactionSource = interactionSource),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⚙",
                color = if (isFocused) Color.Black else Color.White,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun SettingsOverlayScreen(
    prefs: SharedPreferences,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var soundEnabled by remember { mutableStateOf(prefs.getBoolean("sfx_enabled", true)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD90A0A0E)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .background(Color(0xFF242428), RoundedCornerShape(24.dp))
                .border(BorderStroke(1.dp, Color(0x33FFFFFF)), RoundedCornerShape(24.dp))
                .padding(32.dp)
        ) {
            Text(
                text = "Настройки macOS TV",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Все параметры масштабирования выверены под стандарт 4K/1080p.",
                color = Color(0x88FFFFFF),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(24.dp))

            SettingsActionRow(
                title = "Звуковые эффекты tvOS",
                status = if (soundEnabled) "Включены" else "Выключены",
                onClick = {
                    val next = !soundEnabled
                    soundEnabled = next
                    SoundManager.getInstance(context).setEnabled(next, context)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingsActionRow(
                title = "Сбросить избранное Dock",
                status = "Сброс",
                onClick = {
                    prefs.edit().remove("favorite_packages").apply()
                    onDismiss()
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingsActionRow(
                title = "Системные параметры Android TV",
                status = "Открыть",
                onClick = {
                    try {
                        context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                    } catch (ignored: Exception) {}
                }
            )

            Spacer(modifier = Modifier.height(28.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                val backSource = remember { MutableInteractionSource() }
                val backFocused by backSource.collectIsFocusedAsState()

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (backFocused) Color.White else Color(0x26FFFFFF))
                        .combinedClickable(
                            interactionSource = backSource,
                            indication = null,
                            onClick = onDismiss
                        )
                        .focusable(interactionSource = backSource)
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Готово",
                        color = if (backFocused) Color.Black else Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsActionRow(
    title: String,
    status: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isFocused) Color.White else Color(0xFF323236))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = if (isFocused) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            status,
            color = if (isFocused) Color(0xFF1E2430) else Color(0x99FFFFFF),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// --- БЕЗОПАСНАЯ ИЗВЛЕЧЕНИЕ ГРАФИКИ БЕЗ ПАДЕНИЙ ---
private suspend fun scanInstalledApplications(context: Context): List<AppModel> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val result = mutableListOf<AppModel>()

    val tvIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    var activities = pm.queryIntentActivities(tvIntent, 0)
    if (activities.isEmpty()) {
        val phoneIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        activities = pm.queryIntentActivities(phoneIntent, 0)
    }

    for (resolveInfo in activities) {
        val packageName = resolveInfo.activityInfo.packageName
        if (packageName == context.packageName) continue

        val label = resolveInfo.loadLabel(pm).toString()
        val iconDrawable = resolveInfo.loadIcon(pm)
        
        // Потокобезопасная отрисовка иконки (важно для предотвращения крашей на ТВ)
        val bitmap = withContext(Dispatchers.Main) {
            drawableToBitmap(iconDrawable)
        }
        val dominantColor = extractDominantColor(bitmap)
        
        result.add(AppModel(packageName, label, bitmap.asImageBitmap(), dominantColor))
    }

    result.sortedBy { it.label.lowercase(Locale.getDefault()) }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val w = drawable.intrinsicWidth.coerceAtLeast(1)
    val h = drawable.intrinsicHeight.coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bmp
}

private fun extractDominantColor(bitmap: Bitmap): Color {
    return try {
        val scaled = Bitmap.createScaledBitmap(bitmap, 12, 12, false)
        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        var counted = 0

        for (x in 2 until 10) {
            for (y in 2 until 10) {
                val pixel = scaled.getPixel(x, y)
                if (android.graphics.Color.alpha(pixel) > 100) {
                    totalR += android.graphics.Color.red(pixel)
                    totalG += android.graphics.Color.green(pixel)
                    totalB += android.graphics.Color.blue(pixel)
                    counted++
                }
            }
        }

        if (counted > 0) {
            Color(
                red = (totalR / counted).toInt(),
                green = (totalG / counted).toInt(),
                blue = (totalB / counted).toInt()
            )
        } else {
            Color(0xFF38465C)
        }
    } catch (e: Exception) {
        Color(0xFF38465C)
    }
}

private fun launchApplication(context: Context, packageName: String) {
    val pm = context.packageManager
    var intent = pm.getLeanbackLaunchIntentForPackage(packageName)
    if (intent == null) {
        intent = pm.getLaunchIntentForPackage(packageName)
    }
    intent?.let { context.startActivity(it) }
}
INNER_EOF
)
