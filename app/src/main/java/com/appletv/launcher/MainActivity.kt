package com.appletv.launcher
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
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
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
data class AppModel(
val packageName: String,
val label: String,
val icon: ImageBitmap,
val dominantColor: Color
)
class MainActivity : ComponentActivity() {
override fun onCreate(savedInstanceState: Bundle?) {
super.onCreate(savedInstanceState)
setContent {
AppleTvHomeScreen()
}
}
}
@Composable
fun AppleTvHomeScreen() {
val context = LocalContext.current
val scope = rememberCoroutineScope()
val prefs = remember { context.getSharedPreferences("tvos_prefs", Context.MODE_PRIVATE) }
    var installedApps by remember { mutableStateOf<List<AppModel>>(emptyList()) }
    var favoritePackages by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        scope.launch {
            val apps = scanInstalledApplications(context)
            val savedFavs = prefs.getStringSet("favorites", null)
            val initialFavs = if (savedFavs.isNullOrEmpty()) {
                apps.take(6).map { it.packageName }.toSet()
            } else {
                savedFavs
            }
            installedApps = apps
            favoritePackages = initialFavs
            prefs.edit().putStringSet("favorites", initialFavs).apply()
        }
    }

    val dockApps = installedApps.filter { it.packageName in favoritePackages }
    val gridApps = installedApps.filter { it.packageName !in favoritePackages }
    val gridRows = gridApps.chunked(6)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        // --- TOP SHELF (Огромный постер сверху на 60% экрана с градиентом) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .align(Alignment.TopCenter)
        ) {
            Image(
                painter = painterResource(id = R.drawable.bg_poster_studio),
                contentDescription = "Hero Background",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Плавный переход в черный низ
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0x88000000), Color.Black),
                            startY = 200f
                        )
                    )
            )
        }

        // --- ГЛАВНАЯ СЕТКА ПРОКРУТКИ ---
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 60.dp)
        ) {
            // Пустое пространство, чтобы спустить Док вниз
            item {
                Spacer(modifier = Modifier.height(420.dp))
            }

            // --- 1. DOCK (ИЗБРАННОЕ) С МАТОВЫМ ФОНОМ ---
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 60.dp)
                        .padding(bottom = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Подложка Дока (Frosted Glass)
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color(0x33FFFFFF),
                                shape = RoundedCornerShape(32.dp)
                            )
                            .padding(horizontal = 24.dp, vertical = 20.dp)
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(dockApps, key = { it.packageName }) { app ->
                                TvosAppIcon(
                                    app = app,
                                    onClick = { launchApp(context, app.packageName) },
                                    onLongClick = {
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

            // --- 2. СЕТКА ОСТАЛЬНЫХ ПРИЛОЖЕНИЙ ---
            items(gridRows) { rowList ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 84.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    for (app in rowList) {
                        TvosAppIcon(
                            app = app,
                            onClick = { launchApp(context, app.packageName) },
                            onLongClick = {
                                val newFavs = favoritePackages.toMutableSet().apply { add(app.packageName) }
                                favoritePackages = newFavs
                                prefs.edit().putStringSet("favorites", newFavs).apply()
                            }
                        )
                    }
                    val emptySpots = 6 - rowList.size
                    for (i in 0 until emptySpots) {
                        Spacer(modifier = Modifier.width(150.dp))
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvosAppIcon(
app: AppModel,
onClick: () -> Unit,
onLongClick: () -> Unit
) {
val interactionSource = remember { MutableInteractionSource() }
val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1.0f,
        animationSpec = tween(durationMillis = 200), label = "scale"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (isFocused) 0.6f else 0.0f,
        animationSpec = tween(durationMillis = 200), label = "glow"
    )

    val textAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(durationMillis = 200), label = "text"
    )

    Column(
        modifier = Modifier
            .width(150.dp)
            .zIndex(if (isFocused) 1f else 0f), 
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(150.dp)
                .height(90.dp)
                .scale(scale)
                .drawBehind {
                    if (glowAlpha > 0f) {
                        drawIntoCanvas { canvas ->
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.TRANSPARENT
                                setShadowLayer(
                                    45f, 0f, 20f,
                                    android.graphics.Color.argb((glowAlpha * 255).toInt(), 0, 0, 0)
                                )
                            }
                            canvas.nativeCanvas.drawRoundRect(
                                0f, 0f, size.width, size.height, 24.dp.toPx(), 24.dp.toPx(), paint
                            )
                            
                            val glowPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.TRANSPARENT
                                setShadowLayer(
                                    20f, 0f, 0f,
                                    android.graphics.Color.argb((glowAlpha * 180).toInt(), 255, 255, 255)
                                )
                            }
                            canvas.nativeCanvas.drawRoundRect(
                                0f, 0f, size.width, size.height, 24.dp.toPx(), 24.dp.toPx(), glowPaint
                            )
                        }
                    }
                }
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFEBEBEB))
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .focusable(interactionSource = interactionSource),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                modifier = Modifier.size(64.dp),
                contentScale = ContentScale.Fit
            )
        }
        
        Text(
            text = app.label,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .padding(top = 12.dp)
                .alpha(textAlpha)
        )
    }
}
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
        
        val bitmap = withContext(Dispatchers.Main) {
            drawableToBitmap(iconDrawable)
        }
        
        result.add(AppModel(packageName, label, bitmap.asImageBitmap(), Color.Transparent))
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
private fun launchApp(context: Context, packageName: String) {
val pm = context.packageManager
var intent = pm.getLeanbackLaunchIntentForPackage(packageName)
if (intent == null) {
intent = pm.getLaunchIntentForPackage(packageName)
}
intent?.let { context.startActivity(it) }
}
