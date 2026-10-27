package com.appletv.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// --- МОДЕЛЬ ДАННЫХ ---
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MacOSTvLauncherScreen()
        }
    }
}

@Composable
fun MacOSTvLauncherScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Состояния списка приложений и избранного
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var favoritePackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    // Загрузка SharedPreferences для Избранного
    val prefs = context.getSharedPreferences("mac_tv_prefs", Context.MODE_PRIVATE)

    // Инициализация (запрос PackageManager)
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            val loadedApps = loadInstalledApps(context)
            val savedFavs = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
            
            // Если избранное пустое (первый запуск), добавляем первые 5 приложений
            val initialFavs = if (savedFavs.isEmpty()) {
                loadedApps.take(5).map { it.packageName }.toSet()
            } else {
                savedFavs
            }
            
            allApps = loadedApps
            favoritePackages = initialFavs
            prefs.edit().putStringSet("favorites", initialFavs).apply()
        }
    }

    // Фильтрация списков
    val dockApps = allApps.filter { it.packageName in favoritePackages }
    val gridApps = allApps.filter { it.packageName !in favoritePackages }

    // Разделяем Grid-приложения на ряды по 6 штук
    val gridRows = gridApps.chunked(6)

    // Общий фон (Черный, кинематографичный)
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF050508))) {
        
        // Фоновый постер (Top Shelf Banner)
        Image(
            painter = rememberDrawablePainter(drawable = context.getDrawable(R.drawable.bg_poster_studio)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(600.dp)
                .align(Alignment.TopCenter)
        )

        // Градиент затемнения от постера к доку
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xFF050508)),
                        startY = 300f,
                        endY = 1200f
                    )
                )
        )

        // ОДИН СКВОЗНОЙ ЛАЗИ-СПИСОК (Вертикальная навигация пульта)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 64.dp)
        ) {
            // 1. ПУСТАЯ ЗОНА (Top Shelf Header)
            item {
                Spacer(modifier = Modifier.height(350.dp))
                
                // Заголовок активного приложения (опционально, здесь статичный)
                Text(
                    text = "macOS TV",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 42.sp,
                    letterSpacing = (-1).sp,
                    modifier = Modifier.padding(start = 64.dp, bottom = 48.dp)
                )
            }

            // 2. ЗАКРЕПЛЕННЫЙ РЯД (Dock / Избранное)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 64.dp)
                        .padding(bottom = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Подложка Dock - Frosted Glass (Полупрозрачный темно-серый)
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color(0xE6252528),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .padding(16.dp)
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(dockApps, key = { it.packageName }) { app ->
                                AppCard(
                                    appInfo = app,
                                    onClick = { launchApp(context, app.packageName) },
                                    onLongClick = {
                                        // Удаляем из Дока
                                        val newFavs = favoritePackages.toMutableSet().apply { remove(app.packageName) }
                                        favoritePackages = newFavs
                                        prefs.edit().putStringSet("favorites", newFavs).apply()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 3. ОСНОВНАЯ СЕТКА ВСЕХ ПРИЛОЖЕНИЙ
            if (gridRows.isNotEmpty()) {
                item {
                    Text(
                        text = "Библиотека приложений",
                        color = Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(start = 64.dp, bottom = 16.dp, top = 24.dp)
                    )
                }

                items(gridRows) { rowApps ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 64.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        for (app in rowApps) {
                            AppCard(
                                appInfo = app,
                                onClick = { launchApp(context, app.packageName) },
                                onLongClick = {
                                    // Добавляем в Док
                                    val newFavs = favoritePackages.toMutableSet().apply { add(app.packageName) }
                                    favoritePackages = newFavs
                                    prefs.edit().putStringSet("favorites", newFavs).apply()
                                }
                            )
                        }
                        // Добиваем пустые ячейки для выравнивания
                        val emptySpots = 6 - rowApps.size
                        for (i in 0 until emptySpots) {
                            Spacer(modifier = Modifier.width(140.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCard(
    appInfo: AppInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Анимация масштаба
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.12f else 1.0f,
        animationSpec = tween(durationMillis = 200), label = ""
    )

    // Анимация интенсивности свечения (Glow Effect)
    val glowAlpha by animateFloatAsState(
        targetValue = if (isFocused) 0.5f else 0.0f,
        animationSpec = tween(durationMillis = 250), label = ""
    )

    Box(
        modifier = Modifier
            .size(width = 140.dp, height = 82.dp)
            .scale(scale)
            // Эффект динамического свечения (Оптимизировано через FrameworkPaint)
            .drawBehind {
                if (glowAlpha > 0f) {
                    drawIntoCanvas { canvas ->
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.TRANSPARENT
                            // Мягкое белое свечение
                            setShadowLayer(
                                40f, 0f, 12f,
                                android.graphics.Color.argb((glowAlpha * 255).toInt(), 255, 255, 255)
                            )
                        }
                        canvas.nativeCanvas.drawRoundRect(
                            0f, 0f, size.width, size.height, 18.dp.toPx(), 18.dp.toPx(), paint
                        )
                    }
                }
            }
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF3A3A3C)) // Цвет подложки мака
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null, // Убираем стандартный Ripple, у нас свой Scale
                onClick = onClick,
                onLongClick = onLongClick
            )
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center
    ) {
        // Иконка приложения
        Image(
            painter = rememberDrawablePainter(drawable = appInfo.icon),
            contentDescription = appInfo.label,
            modifier = Modifier.size(52.dp),
            contentScale = ContentScale.Fit
        )
    }
}

// --- ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ---

private suspend fun loadInstalledApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val apps = mutableListOf<AppInfo>()
    
    // Запрос TV приложений
    val tvIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    var resolveInfos = pm.queryIntentActivities(tvIntent, 0)
    
    // Если TV приложений нет, ищем мобильные
    if (resolveInfos.isEmpty()) {
        val phoneIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        resolveInfos = pm.queryIntentActivities(phoneIntent, 0)
    }

    for (ri in resolveInfos) {
        val pkg = ri.activityInfo.packageName
        if (pkg == context.packageName) continue // Пропускаем сам лаунчер

        val label = ri.loadLabel(pm).toString()
        val icon = ri.loadIcon(pm)
        apps.add(AppInfo(pkg, label, icon))
    }
    
    // Сортируем по алфавиту
    apps.sortedBy { it.label.lowercase(Locale.getDefault()) }
}

private fun launchApp(context: Context, packageName: String) {
    val pm = context.packageManager
    var launchIntent = pm.getLeanbackLaunchIntentForPackage(packageName)
    if (launchIntent == null) {
        launchIntent = pm.getLaunchIntentForPackage(packageName)
    }
    if (launchIntent != null) {
        context.startActivity(launchIntent)
    }
}
