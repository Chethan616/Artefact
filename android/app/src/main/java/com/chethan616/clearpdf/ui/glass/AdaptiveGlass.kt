package com.chethan616.clearpdf.ui.glass

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.chethan616.clearpdf.ui.theme.clear
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sign

/**
 * Adaptive luminance: a glass surface that reads how light or dark the things behind it are and
 * answers them, as AndroidLiquidGlass's "Adaptive luminance glass" does (Kyant0, Apache-2.0; the
 * library our backdrop module is built on). Over a bright page the glass turns milky and bright and
 * takes dark ink; over a dark one it deepens and takes white ink. So a menu reads the same over a
 * white PDF, a black slide or the wallpaper, whatever the app's theme.
 *
 * How: the surface records the backdrop it draws a second time, very small, into [layer]; a few times
 * a second that record is read back and averaged into one luminance, which glides to its new value.
 * The glass's effects read [value] while drawing, so nothing recomposes as it moves; only [onLight]
 * (which ink) is composition state, and it flips with hysteresis so mid-grey never flickers.
 */
@Stable
class GlassLuminance internal constructor(initial: Float, internal val layer: GraphicsLayer, private val once: Boolean = false) {
    private val anim = Animatable(initial)
    private var recorded = false
    // how many times the backdrop has been recorded; the reading skips a record it has already read
    @Volatile private var records = 0

    /** 0 (black behind) … 1 (white behind). Read while drawing. */
    val value: Float get() = anim.value

    private val busyAnim = Animatable(0f)

    /**
     * 0 (calm: the wallpaper, an empty page) … 1 (busy: lines of text, a photo) behind the glass. Read
     * while drawing: a busy backdrop takes a thicker wash and frost, so what is behind does not ghost
     * through the words on the glass (#158), while a calm one stays as clear as it was. Only with
     * Adaptive luminance on (Settings ▸ Liquid glass): off, the glass is the preset's and nothing else.
     */
    val busy: Float get() = if (GlassSettings.adaptive) busyAnim.value else 0f

    /** Whether the glass sits over something light, so its ink should be dark. */
    var onLight by mutableStateOf(initial > 0.5f)
        private set

    /** True once the backdrop has really been read (until then [value] is the theme's guess). */
    var measured by mutableStateOf(false)
        private set

    /** Draws the backdrop, and a small copy of it into [layer] for the reading. */
    internal val recorder: DrawScope.(DrawScope.() -> Unit) -> Unit = { drawBackdrop ->
        drawBackdrop()
        val w = (size.width / DOWN).toInt().coerceAtLeast(1)
        val h = (size.height / DOWN).toInt().coerceAtLeast(1)
        layer.record(IntSize(w, h)) {
            scale(1f / DOWN, 1f / DOWN, pivot = Offset.Zero) { drawBackdrop() }
        }
        recorded = true
        records++
    }

    internal suspend fun run() = kotlinx.coroutines.coroutineScope {
        val px = IntArray(SAMPLES * SAMPLES)
        var waited = 0
        var read = -1
        while (true) {
            // nothing new drawn behind it since the last reading (a still page, the app in the
            // background): nothing to read again, so no bitmap copy every 220 ms for nothing (#158)
            if (measured && records == read) {
                delay(SAMPLE_INTERVAL_MS)
                continue
            }
            if (!recorded) {
                // nothing drawn to read yet: don't hold the surface back for ever
                if (!measured && ++waited > 12) measured = true
                delay(16)
                continue
            }
            read = records
            val stats = withContext(Dispatchers.Default) {
                runCatching {
                    val full = layer.toImageBitmap().asAndroidBitmap()
                    // a hardware bitmap can't be read: a small software copy can
                    val small = Bitmap.createScaledBitmap(full, SAMPLES, SAMPLES, true).copy(Bitmap.Config.ARGB_8888, false)
                    small.getPixels(px, 0, SAMPLES, 0, 0, SAMPLES, SAMPLES)
                    backdropStats(px, SAMPLES)
                }.getOrNull()
            }
            val lum = stats?.luminance
            if (stats != null && kotlin.math.abs(stats.busy - busyAnim.targetValue) > 0.15f) {
                // eased beside the luminance, not before it: neither waits for the other
                if (!measured) busyAnim.snapTo(stats.busy) else launch { busyAnim.animateTo(stats.busy, tween(420)) }
            }
            if (lum == null && !measured && ++waited > 12) measured = true
            if (lum != null) {
                onLight = when {
                    !measured -> lum > 0.5f
                    lum > 0.56f -> true
                    lum < 0.44f -> false
                    else -> onLight
                }
                if (!measured) {
                    // The first reading is taken as it is, at once. The surface is held unseen until
                    // it is in (see [MenuCard]), so a menu opened over a black page is dark from its
                    // first visible frame: it used to open in the theme's guess (white in the light
                    // theme) and then swing to black.
                    anim.snapTo(lum)
                    measured = true
                    // a menu reads what is behind it once, as it opens, and keeps it while it is up:
                    // read again and again, the page under it (the viewer's header stepping aside,
                    // its tiles sharpening) moved its glass and ink, and it seemed to breathe (#158)
                    if (once) return@coroutineScope
                } else if (kotlin.math.abs(lum - anim.targetValue) > 0.07f) {
                    // only a real change behind it moves it (a small one, read again and again, made it breathe)
                    anim.animateTo(lum, tween(420))
                }
            }
            delay(SAMPLE_INTERVAL_MS)
        }
    }

    private companion object {
        const val DOWN = 8f
        // enough cells to tell lines of text from an even wash; the mean is the same as at 6 x 6
        const val SAMPLES = 12
        const val SAMPLE_INTERVAL_MS = 220L
    }
}

/** What one reading found behind a glass: how light it is, and how busy ([GlassLuminance.busy]). */
internal class BackdropStats(val luminance: Float, val busy: Float)

/**
 * The mean luminance of [px] (a [side] x [side] sample) and how busy it is. Busy is the mean step
 * between neighbouring cells, not the spread of the whole: a smooth gradient (the aurora) has a wide
 * spread but tiny steps, lines of print or a photo have big ones. Cells nothing was drawn in say nothing.
 */
internal fun backdropStats(px: IntArray, side: Int): BackdropStats? {
    fun drawn(c: Int) = (c ushr 24) >= 8
    fun lum(c: Int) = (0.2126 * ((c shr 16) and 0xFF) + 0.7152 * ((c shr 8) and 0xFF) + 0.0722 * (c and 0xFF)) / 255.0
    var sum = 0.0
    var n = 0
    var step = 0.0
    var pairs = 0
    for (y in 0 until side) for (x in 0 until side) {
        val c = px[y * side + x]
        if (!drawn(c)) continue
        val l = lum(c)
        sum += l
        n++
        if (x + 1 < side) px[y * side + x + 1].let { if (drawn(it)) { step += kotlin.math.abs(lum(it) - l); pairs++ } }
        if (y + 1 < side) px[(y + 1) * side + x].let { if (drawn(it)) { step += kotlin.math.abs(lum(it) - l); pairs++ } }
    }
    if (n == 0) return null
    val texture = if (pairs == 0) 0.0 else step / pairs
    return BackdropStats((sum / n).toFloat(), ((texture - BusyFrom) / (BusyTo - BusyFrom)).toFloat().coerceIn(0f, 1f))
}


/**
 * The neighbour step at which a backdrop starts to count as busy, and where it is fully so. Measured on
 * a phone: a page of print behind a menu steps 0.14-0.18, the desk (title, chips, card edges) 0.06-0.10,
 * which should keep its clear glass.
 */
private const val BusyFrom = 0.06
private const val BusyTo = 0.15

/**
 * A luminance reading for one glass surface, or null when [enabled] is false. Pass it to [glass] (or
 * [GlassCard]) and take the ink from [adaptiveInk].
 */
@Composable
fun rememberGlassLuminance(enabled: Boolean = GlassSettings.adaptive, once: Boolean = false): GlassLuminance? {
    if (!enabled || clear.clean) return null
    val layer = rememberGraphicsLayer()
    val dark = clear.dark
    // the first guess: what the screen as a whole looks like now (the window's sampler, running
    // while Adaptive luminance is on), else the theme
    val screen = LocalScreenInk.current
    val state = remember(layer) { GlassLuminance(screen?.frame?.average() ?: if (dark) 0.14f else 0.86f, layer, once) }
    LaunchedEffect(state) { state.run() }
    return state
}

/**
 * The ink of the menu or popover being composed (unspecified outside one): its rows and glyphs take
 * it, so they follow the glass when it adapts to what is behind it.
 */
val LocalMenuInk = androidx.compose.runtime.compositionLocalOf { Color.Unspecified }

/** Dark ink over light glass, white over dark, gliding between them; [fallback] when there is no reading. */
@Composable
fun adaptiveInk(luminance: GlassLuminance?, fallback: Color): Color {
    if (luminance == null) return fallback
    val target = if (luminance.onLight) InkOnLight else Color.White
    val ink by animateColorAsState(target, tween(220), label = "adaptiveGlassInk")
    return ink
}

/** [luminance] as -1 (dark) … 1 (light), eased so mid-greys stay near neutral. */
private fun signed(luminance: Float): Float = (luminance * 2f - 1f).let { sign(it) * it * it }

/**
 * The adaptive effect stack (after Kyant0's demo): brighter and flatter over light, deeper over dark,
 * the frost growing with the light (a bright busy page needs more of it than a dark one), then the
 * user's lens.
 */
fun BackdropEffectScope.adaptiveGlassEffects(
    style: GlassStyle,
    luminance: Float,
    blurPx: Float,
    refractionHeightPx: Float,
    refractionAmountPx: Float,
    busy: Float = 0f,
) {
    val l = signed(luminance.coerceIn(0f, 1f))
    colorControls(
        brightness = (if (l > 0f) lerp(0.08f, 0.42f, l) else lerp(0.08f, -0.2f, -l)) + style.brightness,
        contrast = if (l > 0f) lerp(1f, 0.25f, l) else 1f,
        saturation = style.saturation,
    )
    blur(blurPx * style.blur * (if (l > 0f) lerp(1f, 1.5f, l) else lerp(1f, 0.6f, -l)) * (1f + 0.6f * busy.coerceIn(0f, 1f)))
    pageLift(pageLiftAmount(luminance, busy))
    lens(
        refractionHeightPx * style.refractionHeight,
        refractionAmountPx * style.refractionAmount,
        depthEffect = style.depthEffect,
        chromaticAberration = style.chromaticAberration,
    )
}

/**
 * The user's glass with its filters set aside: while Adaptive luminance is on, frost, colour,
 * brightness and tint are the luminance's to set, so the presets' values for them are not applied
 * (the lens and rim light, which are shape, stay).
 */
val GlassStyle.adaptiveBase: GlassStyle get() = copy(blur = 1f, saturation = 1.5f, brightness = 0f, tint = 1f)

/**
 * The thin wash over adaptive glass: milk over light, smoke over dark. Over a [busy] backdrop (a page
 * of print) it thickens, so the print does not show through as ghost text behind the glass's own words.
 */
fun adaptiveWash(luminance: Float, busy: Float = 0f): Color {
    val l = signed(luminance.coerceIn(0f, 1f))
    val b = busy.coerceIn(0f, 1f)
    return if (l >= 0f) Color.White.copy(alpha = lerp(lerp(0.10f, 0.30f, l), 0.68f, b))
    else Color.Black.copy(alpha = lerp(lerp(0.10f, 0.34f, -l), 0.60f, b))
}

/**
 * Glass over a light page of print lifts it: brighter and flatter, so the frosted print behind reads
 * as white glass, not as grey smudges (black text blurred averages to grey). Apple's glass brightens
 * over light content the same way. [amount] 0 (the wallpaper, a dark page: nothing) … 1 (white page
 * of print). The lens and colour are untouched.
 */
fun BackdropEffectScope.pageLift(amount: Float) {
    val a = amount.coerceIn(0f, 1f)
    if (a <= 0.01f) return
    // contrast pivots on mid grey, so lowering it alone greys the white too: the brightness puts
    // white back at white (0.5 * (1 - contrast)) and a touch over, and the print lifts toward it
    val contrast = lerp(1f, 0.5f, a)
    colorControls(brightness = 0.5f * (1f - contrast) + 0.03f * a, contrast = contrast)
}

/** How much a reading asks for [pageLift]: light behind (from 0.55 up) and busy with print. */
fun pageLiftAmount(luminance: Float, busy: Float): Float =
    ((luminance - 0.55f) / 0.3f).coerceIn(0f, 1f) * busy.coerceIn(0f, 1f)

/**
 * A dark hairline for glass over something near-white (a page): its white rim light vanishes there, so
 * without one a light menu or bar has no edge at all on a white page (#158). None over anything darker.
 * Callers draw it only with Adaptive luminance on ([adaptiveEdge]).
 */
fun lightEdge(luminance: Float): Color {
    // measured on a phone: a menu over a white page with the viewer's dark header band above reads
    // about 0.7, so the hairline starts at 0.6 and is full from 0.85
    val a = ((luminance - 0.6f) / 0.25f).coerceIn(0f, 1f) * 0.14f
    return if (a <= 0.005f) Color.Unspecified else Color.Black.copy(alpha = a)
}

/** The tallest a menu grows: its own most, or 72% of the screen, so its last row is never off-screen. */
@Composable
fun menuMaxHeight(designed: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp =
    minOf(designed, (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.72f).dp)

/** [lightEdge] while Adaptive luminance is on; none while it is off. */
fun adaptiveEdge(luminance: Float): Color = if (GlassSettings.adaptive) lightEdge(luminance) else Color.Unspecified

/** Draws [edge] (from [lightEdge]) along [shape]'s outline, about a dp of it showing inside. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlassEdge(shape: androidx.compose.ui.graphics.Shape, edge: Color) {
    if (edge == Color.Unspecified || edge.alpha <= 0f) return
    drawOutline(shape.createOutline(size, layoutDirection, this), edge, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
}
