package com.chethan616.clearpdf.ui.glass

import android.os.Build
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.ui.theme.clear
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.drawBackdrop

/**
 * Whether glass is drawn solid, nothing showing through: the Clean appearance, or Reduce transparency
 * in either appearance (#158). Every glass surface asks this where it used to ask for Clean alone.
 */
val solidGlass: Boolean
    @Composable get() = clear.clean || GlassSettings.reduceTransparency

/** A quiet, theme-tinted backdrop for controls that float over Clean mode content. */
@Composable
internal fun Modifier.cleanFloatingSurface(shape: Shape, strongestAtTop: Boolean = false): Modifier {
    val panel = clear.solidPanel
    // (Reduce transparency is a Liquid Glass setting: Clean's floating bars keep their own frost)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return clip(shape).background(panel)
    }
    val mask = remember(strongestAtTop) {
        val light = Color.Black.copy(alpha = 0.12f)
        val stops = if (strongestAtTop) listOf(Color.Black, light) else listOf(light, Color.Black)
        Brush.verticalGradient(stops)
    }
    return drawBackdrop(
        backdrop = LocalBackdrop.current,
        shape = { shape },
        effects = { blur(14.dp.toPx()) },
        // Mask the recorded backdrop only. The underlying scene supplies the sharper detail.
        onDrawBackdrop = { draw ->
            draw()
            drawRect(mask, blendMode = BlendMode.DstIn)
        },
        highlight = null,
        shadow = null,
    ).background(panel.copy(alpha = 0.82f), shape)
}

/**
 * A still glass surface (bars, the dock's body, panels, sheets): refracts [LocalBackdrop] through
 * [shape], washed with [wash]. No shadow, ever.
 */
@Composable
fun Modifier.glass(
    shape: Shape,
    wash: Color = clear.glass,
    blur: Dp = 8.dp,
    lensHeight: Dp = 16.dp,
    lensAmount: Dp = 24.dp,
    styleOverride: GlassStyle? = null,
    luminance: GlassLuminance? = null,
    /**
     * A dimming layer ([glassDim]) over the wash, at its own strength whatever the preset's tint: what
     * guarantees the words on this glass their contrast, so it is not the preset's to thin out (#158).
     */
    dim: Color = Color.Unspecified,
    /**
     * A reading of what is behind, used only to add frost and milk over print ([GlassLuminance.busy]),
     * not to change the glass's colour or ink (that is [luminance], Adaptive luminance's).
     */
    assist: GlassLuminance? = null,
): Modifier {
    if (solidGlass) return clip(shape).background(clear.solidPanel)
    val clearDark = clear.dark
    val backdrop = LocalBackdrop.current
    val style = styleOverride ?: GlassSettings.style
    // adaptive: the glass answers what is behind it instead of wearing the theme's wash
    if (luminance != null) return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        shadow = null,
        effects = { adaptiveGlassEffects(style, luminance.value, blur.toPx(), lensHeight.toPx(), lensAmount.toPx(), busy = luminance.busy) },
        highlight = { glassHighlight(style) },
        onDrawBackdrop = luminance.recorder,
        onDrawSurface = {
            drawRect(adaptiveWash(luminance.value, luminance.busy).glassTint(style))
            drawGlassEdge(shape, adaptiveEdge(luminance.value))
        },
    )
    // Adaptive luminance on: a still surface (a bar, a panel, a sheet) takes its frost and light
    // from what is around it. It keeps its own wash: what is written on it is in the theme's ink.
    val around = rememberSurroundLuminance()
    if (around != null && styleOverride == null) {
        val base = style.adaptiveBase
        return then(around.modifier).drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            shadow = null,
            effects = { adaptiveGlassEffects(base, around.value, blur.toPx(), lensHeight.toPx(), lensAmount.toPx()) },
            highlight = { glassHighlight(base) },
            onDrawSurface = {
                if (wash.isSpecified) drawRect(wash)
                if (dim.isSpecified) drawRect(dim)
            },
        )
    }
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        shadow = null,
        effects = {
            glassEffects(style, blur.toPx(), lensHeight.toPx(), lensAmount.toPx())
            if (assist != null && assist.busy > 0.01f) {
                blur(18.dp.toPx() * assist.busy)
                pageLift(pageLiftAmount(assist.value, assist.busy))
            }
        },
        highlight = { glassHighlight(style) },
        onDrawBackdrop = assist?.recorder ?: { it() },
        onDrawSurface = {
            if (wash.isSpecified) drawRect(wash.glassTint(style))
            if (dim.isSpecified) drawRect(dim)
            // over print: milk over a light page (the lift has whitened it already, so less is needed)
            if (assist != null && assist.busy > 0.01f) drawRect(if (clearDark) Color.Black else Color.White, alpha = 0.35f * assist.busy)
        },
    )
}

/**
 * The dimming layer inside glass that carries words over the wallpaper (#158, after Apple's Liquid
 * Glass: clear glass over a bright or busy backdrop needs one). Smoke in the dark theme, where the ink
 * is white; milk in the light theme, where it is dark. [heavy] is the "regular" glass of a panel of
 * rows (the Files list); light is a bar's (search, tabs). The blur, lens and rim stay the preset's.
 */
@Composable
fun glassDim(heavy: Boolean = false): Color {
    // A picture of the user's own can be as busy as it likes, so it gets the whole dim. The app's own
    // wallpapers are calm: there the dim is a breath, at the preset's tint, and the glass stays clear
    // liquid glass rather than a frosted card.
    val k = if (GlassSettings.wallpaper == Wallpaper.Custom) 1f else 0.4f * GlassSettings.style.tint.coerceIn(0.5f, 1.8f)
    return if (clear.dark) Color(0xFF0B0F1A).copy(alpha = (if (heavy) 0.50f else 0.36f) * k)
    else Color.White.copy(alpha = (if (heavy) 0.56f else 0.40f) * k)
}

/**
 * A panel of rows on the same liquid glass as the search bar above it (#158): the wallpaper refracts
 * and frosts through it like the bar, under a heavier dim so rows of names read over any picture. The
 * rows lie flat on it (no glass of their own). Solid in the Clean appearance.
 */
@Composable
fun GlassListPanel(modifier: Modifier = Modifier, shape: Shape = RoundedRectangle(28.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.wash(shape), content = content)
}

/**
 * A glass card whose children refract the card itself: its surface is a sibling layer under [content],
 * so a glass button inside reads as part of the same frosted sheet, not a hole punched through it.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedRectangle(28.dp),
    wash: Color = clear.glass,
    blur: Dp = 8.dp,
    styleOverride: GlassStyle? = null,
    luminance: GlassLuminance? = null,
    assist: GlassLuminance? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val below = LocalBackdrop.current
    val layer = rememberLayerBackdrop()
    val combined = rememberCombinedBackdrop(below, layer)
    Box(modifier) {
        Box(Modifier.matchParentSize().layerBackdrop(layer).glass(shape, wash, blur, styleOverride = styleOverride, luminance = luminance, assist = assist))
        CompositionLocalProvider(LocalBackdrop provides combined) { Column(content = content) }
    }
}

/**
 * A painted surface (a desk card) that glass can sit on: [paint] is drawn as a sibling layer under
 * [content], so a [GlassChip] inside refracts the card's own colour instead of the wallpaper behind it.
 * (A glass control can't refract the layer it is drawn in, hence two layers.)
 */
@Composable
fun GlassHost(
    modifier: Modifier = Modifier,
    paint: @Composable BoxScope.() -> Unit,
    opaque: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val below = LocalBackdrop.current
    val layer = rememberLayerBackdrop()
    // an opaque paint hides what's below anyway; sampling only it keeps the glass on it still while
    // the whole host scrolls (nothing to re-blur), where the wallpaper behind would move every frame
    val combined = if (opaque) layer else rememberCombinedBackdrop(below, layer)
    Box(modifier) {
        Box(Modifier.matchParentSize().layerBackdrop(layer), content = paint)
        CompositionLocalProvider(LocalBackdrop provides combined) { content() }
    }
}

/**
 * A container of rows (a Settings group, a tool's panel): the same liquid glass as the Files list
 * and its search bar, app-wide. The wallpaper refracts and frosts through it at the user's preset
 * (Settings ▸ Liquid glass), under a dimming layer that is strong only over a picture of their own
 * ([glassDim]), so its words read on any wallpaper. It used to be a flat wash (tint and rim, no
 * backdrop) while Files was glass; now every container is one kind of glass. Solid while glass is
 * ([solidGlass]: Clean, or Reduce transparency).
 */
@Composable
fun Modifier.wash(shape: Shape = RoundedRectangle(28.dp), tint: Color = clear.glass): Modifier =
    glass(shape, wash = tint, blur = 20.dp, lensHeight = 14.dp, lensAmount = 20.dp, dim = glassDim(heavy = true)).clip(shape)

/**
 * The old flat wash: the glass's tint and its lit rim, without the backdrop pass. Only for a grid of
 * many small cards that scroll (the Pages screen's page cards), where a backdrop pass per card on
 * every scrolled frame would cost too much for what a blur adds there.
 */
@Composable
fun Modifier.flatWash(shape: Shape = RoundedRectangle(20.dp), tint: Color = clear.glass): Modifier {
    if (solidGlass) return clip(shape).background(clear.solidPanel)
    val dark = clear.dark
    val rimTop = Color.White.copy(alpha = if (dark) 0.16f else 0.70f)
    val rimBottom = Color.White.copy(alpha = if (dark) 0.03f else 0.18f)
    return clip(shape)
        .background(tint.glassTint(GlassSettings.style))
        .border(1.dp, Brush.verticalGradient(listOf(rimTop, rimBottom)), shape)
}

/** The [wash] of a panel (a file card, a group of rows), with its 20 dp continuous corners. */
@Composable
fun washPanel(): Modifier = Modifier.wash(RoundedRectangle(20.dp))

/** A [wash] panel holding rows. */
@Composable
fun WashPanel(modifier: Modifier = Modifier, shape: Shape = RoundedRectangle(28.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.wash(shape), content = content)
}

/** A glass panel holding rows: 28 dp continuous corners. */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, shape: Shape = RoundedRectangle(28.dp), wash: Color = clear.glass, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.glass(shape, wash), content = content)
}

/**
 * A flat pressable (a row, a tile, a card): sinks under the finger and eases back (no bounce: that
 * is the glass buttons'), one haptic per tap. No ripple.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    pressedScale: Float = Fluid.PressedScale,
    role: Role = Role.Button,
    enabled: Boolean = true,
    feel: Feel? = Feel.Tap,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val jelly = rememberJellyPress(source, pressedScale)
    val haptics = LocalHaptics.current
    return graphicsLayer {
        scaleX = jelly.scale
        scaleY = jelly.scale
    }.focusRing(source).combinedClickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
        role = role,
        onClick = { if (feel != null) haptics?.play(feel); onClick() },
        onLongClick = onLongClick?.let { l -> { haptics?.play(Feel.Bloom); jelly.rest(); l() } },
    )
}

/**
 * A ring round a control while it has keyboard, D-pad or Switch Access focus (#158): with no ripple
 * (the press is the jelly), nothing else would show which control is focused. Touch never focuses it.
 */
@Composable
fun Modifier.focusRing(source: MutableInteractionSource, corner: Dp = 12.dp): Modifier {
    val focused by source.collectIsFocusedAsState()
    if (!focused) return this
    val ring = com.chethan616.clearpdf.ui.theme.Candy.Sky
    return drawWithContent {
        drawContent()
        val w = 2.dp.toPx()
        drawRoundRect(
            ring, topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
            size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(minOf(corner.toPx(), size.minDimension / 2)),
            style = androidx.compose.ui.graphics.drawscope.Stroke(w),
        )
    }
}

/** An icon in the ink of the glass it sits on. */
@Composable
fun GlassIcon(icon: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    val ink = when {
        tint.isSpecified -> tint
        LocalGlassInk.current.isSpecified -> LocalGlassInk.current
        else -> clear.ink
    }
    Icon(icon, contentDescription, modifier, tint = ink)
}

/**
 * A soft fade at whichever end of a scrolling menu has more to show, so a cut-off last row reads as
 * "scroll for more" rather than a clipped word (#158). Put it before [verticalScroll], with the same
 * [state]: it is drawn over the viewport, not the content.
 */
fun Modifier.scrollEdgeFade(state: androidx.compose.foundation.ScrollState, length: Dp = 24.dp): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val f = (length.toPx() / size.height).coerceIn(0f, 0.5f)
        val top = state.canScrollBackward
        val bottom = state.canScrollForward
        if (f <= 0f || (!top && !bottom)) return@drawWithContent
        drawRect(
            Brush.verticalGradient(
                0f to (if (top) Color.Transparent else Color.Black),
                f to Color.Black,
                1f - f to Color.Black,
                1f to (if (bottom) Color.Transparent else Color.Black),
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** [verticalScroll] with its own state and a [scrollEdgeFade] at the ends that have more. */
@Composable
fun Modifier.fadingVerticalScroll(): Modifier {
    val state = androidx.compose.foundation.rememberScrollState()
    return scrollEdgeFade(state).then(Modifier.verticalScroll(state))
}
