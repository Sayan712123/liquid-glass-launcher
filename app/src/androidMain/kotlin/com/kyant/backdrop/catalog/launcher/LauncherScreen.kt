package com.kyant.backdrop.catalog.launcher

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import glass.app.generated.resources.Res
import glass.app.generated.resources.wallpaper_light
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The launcher home screen.
 *
 * Everything glass here is driven by [DeviceProfile]: on a low-RAM phone the
 * blur radius drops, the runtime-shader lens and the shader highlight are
 * skipped entirely, and the icon bitmaps are decoded at grid size instead of
 * full size. That is what keeps the grid scrolling smoothly on a POCO M4 5G.
 */
@Composable
fun LauncherScreen() {
    val context = LocalContext.current
    val density = LocalDensity.current
    val profile = remember { DeviceProfile.detect(context) }
    val repository = remember { AppRepository(context) }

    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }

    val iconPx = with(density) { profile.iconSizeDp.dp.roundToPx() }

    LaunchedEffect(Unit) {
        apps = repository.loadApps(iconPx)
    }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    val dockApps = remember(apps) {
        val preferred = listOf(
            "com.android.dialer", "com.google.android.dialer",
            "com.android.mms", "com.google.android.apps.messaging",
            "com.android.chrome", "org.mozilla.firefox",
            "com.android.camera", "com.google.android.GoogleCamera"
        )
        val picked = preferred.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
        (picked + apps).distinctBy { it.packageName }.take(4)
    }

    val backdrop = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize()) {
        Image(
            painterResource(Res.drawable.wallpaper_light),
            null,
            Modifier
                .layerBackdrop(backdrop)
                .fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            ClockHeader()

            SearchField(
                query = query,
                onQueryChange = { query = it },
                backdrop = backdrop,
                profile = profile
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(profile.gridColumns),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    AppTile(
                        app = app,
                        backdrop = backdrop,
                        profile = profile,
                        onClick = { repository.launch(app.packageName) },
                        onLongClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", app.packageName, null)
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    )
                }
            }

            Dock(dockApps, backdrop, profile) { repository.launch(it.packageName) }
        }
    }
}

@Composable
private fun ClockHeader() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(15_000L)
        }
    }
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(now) }
    val date = remember(now) { SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(now) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BasicText(
            time,
            style = TextStyle(Color.White, 46.sp, FontWeight.Light)
        )
        BasicText(
            date,
            style = TextStyle(Color.White.copy(alpha = 0.85f), 13.sp, FontWeight.Medium)
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    backdrop: LayerBackdrop,
    profile: DeviceProfile
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .height(44.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(profile.blurRadiusDp.dp.toPx())
                    if (profile.useLens) lens(10.dp.toPx(), 20.dp.toPx())
                },
                highlight = if (profile.useShaderHighlight) {
                    { Highlight.Default }
                } else {
                    { Highlight.Plain }
                },
                shadow = null,
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.14f)) }
            )
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (query.isEmpty()) {
            BasicText(
                "Search apps",
                style = TextStyle(Color.White.copy(alpha = 0.6f), 15.sp)
            )
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(Color.White, 15.sp),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    app: AppEntry,
    backdrop: LayerBackdrop,
    profile: DeviceProfile,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        Modifier
            .clip(RoundedRectangle(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(profile.iconSizeDp.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(15.dp) },
                    effects = {
                        vibrancy()
                        blur(profile.blurRadiusDp.dp.toPx())
                        if (profile.useLens) lens(8.dp.toPx(), 16.dp.toPx())
                    },
                    highlight = if (profile.useShaderHighlight) {
                        { Highlight.Default }
                    } else {
                        { Highlight.Plain }
                    },
                    shadow = null,
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 0.10f)) }
                ),
            contentAlignment = Alignment.Center
        ) {
            val icon = app.icon
            if (icon != null) {
                Image(
                    icon,
                    contentDescription = app.label,
                    modifier = Modifier
                        .size((profile.iconSizeDp - 14).dp)
                        .clip(RoundedRectangle(11.dp))
                )
            } else {
                Box(
                    Modifier
                        .size((profile.iconSizeDp - 14).dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        BasicText(
            app.label,
            style = TextStyle(Color.White, 11.sp, FontWeight.Medium).copy(textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Dock(
    apps: List<AppEntry>,
    backdrop: LayerBackdrop,
    profile: DeviceProfile,
    onLaunch: (AppEntry) -> Unit
) {
    if (apps.isEmpty()) return

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .height(78.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(26.dp) },
                effects = {
                    vibrancy()
                    blur(profile.blurRadiusDp.dp.toPx())
                    if (profile.useLens) lens(12.dp.toPx(), 24.dp.toPx())
                },
                highlight = if (profile.useShaderHighlight) {
                    { Highlight.Default }
                } else {
                    { Highlight.Plain }
                },
                shadow = null,
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.13f)) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            apps.forEach { app ->
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedRectangle(14.dp))
                        .combinedClickableCompat { onLaunch(app) },
                    contentAlignment = Alignment.Center
                ) {
                    val icon = app.icon
                    if (icon != null) {
                        Image(
                            icon,
                            contentDescription = app.label,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedRectangle(12.dp))
                        )
                    } else {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f))
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick)
