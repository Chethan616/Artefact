package com.chethan616.clearpdf.ui.glass

import android.graphics.RuntimeShader
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.chethan616.clearpdf.ui.theme.clear
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.runtimeShaderEffect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The shape of liquid glass made of two pieces, as iOS 26's GlassEffectContainer joins them: the
 * button's circle and the menu's rounded rectangle, melted into one by a smooth minimum (a metaball
 * union) of their distance fields. While they are close, a neck of glass joins them; as [k] falls it
 * thins and lets go.
 */
private const val LiquidSdf = """
uniform float4 circle;
uniform float4 rect;
uniform float rectRadius;
uniform float k;

float sdCircle(float2 p, float2 c, float r) { return length(p - c) - r; }

float sdRRect(float2 p, float4 b, float r) {
    float2 c = (b.xy + b.zw) * 0.5;
    float2 hs = max((b.zw - b.xy) * 0.5, float2(0.0));
    float rr = min(r, min(hs.x, hs.y));
    float2 q = abs(p - c) - (hs - float2(rr));
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - rr;
}

float smin(float a, float b, float kk) {
    if (kk <= 0.001) return min(a, b);
    float h = clamp(0.5 + 0.5 * (b - a) / kk, 0.0, 1.0);
    return mix(b, a, h) - kk * h * (1.0 - h);
}

float sdLiquid(float2 p) {
    return smin(sdCircle(p, circle.xy, circle.z), sdRRect(p, rect, rectRadius), k);
}

float2 gradLiquid(float2 p) {
    float e = 1.0;
    float2 g = float2(sdLiquid(p + float2(e, 0.0)) - sdLiquid(p - float2(e, 0.0)),
                      sdLiquid(p + float2(0.0, e)) - sdLiquid(p - float2(0.0, e)));
    float l = length(g);
    return l > 0.0001 ? g / l : float2(0.0, -1.0);
}
"""

/** The glass's bending of what is behind it, along the liquid shape's edge (the app's lens, on a melted outline). */
private const val LiquidRefractionShader = """
uniform shader content;
uniform float2 offset;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depth;
uniform float dispersion;
$LiquidSdf

float circleMap(float x) { return 1.0 - sqrt(max(1.0 - x * x, 0.0)); }

// the same bend and colour split as the app's own glass (backdrop's rounded-rect refraction shaders),
// on the liquid outline: the split only toward the corners, a pixel or two, three taps
half4 main(float2 coord) {
    float2 p = coord + offset;
    float sd = sdLiquid(p);
    if (sd > 1.0) return half4(0.0);
    float cover = clamp(0.5 - sd, 0.0, 1.0);
    if (-sd >= refractionHeight) return content.eval(coord) * cover;
    float d = circleMap(1.0 - (-min(sd, 0.0)) / refractionHeight) * refractionAmount;
    // the centre and size each part of the glass bends around: the circle's own for the circle, the
    // menu's for the menu, blended across the neck (one centre for both bent the circle the wrong way)
    float dc = sdCircle(p, circle.xy, circle.z);
    float dr = sdRRect(p, rect, rectRadius);
    float w = clamp(0.5 + 0.5 * (dr - dc) / max(k, 1.0), 0.0, 1.0);
    float2 rc = (rect.xy + rect.zw) * 0.5;
    float2 rh = max((rect.zw - rect.xy) * 0.5, float2(1.0));
    float2 center = mix(rc, circle.xy, w);
    float2 halfSize = mix(rh, float2(max(circle.z, 1.0)), w);
    float2 centered = p - center;
    float2 g = gradLiquid(p);
    if (depth > 0.5 && length(centered) > 0.001) g = normalize(g + normalize(centered));
    float2 refracted = coord - d * g;
    if (dispersion > 0.5) {
        float intensity = clamp((centered.x * centered.y) / max(halfSize.x * halfSize.y, 1.0), -1.0, 1.0);
        float2 dc = -d * g * intensity;
        if (dot(dc, dc) >= 0.25) {
            half4 r = content.eval(refracted + dc * 0.4286);
            half4 gg = content.eval(refracted + dc * 0.0952);
            half4 bl = content.eval(refracted - dc * 0.6667);
            return half4(r.r, gg.g, bl.b, (r.a + gg.a + bl.a) / 3.0) * cover;
        }
    }
    return content.eval(refracted) * cover;
}
"""

/** The glass's wash and its lit rim, on the same melted outline. */
private const val LiquidSurfaceShader = """
uniform float4 wash;
uniform float rimAlpha;
$LiquidSdf

half4 main(float2 p) {
    float sd = sdLiquid(p);
    float cover = clamp(0.5 - sd, 0.0, 1.0);
    if (cover <= 0.0) return half4(0.0);
    float2 g = gradLiquid(p);
    float2 light = normalize(float2(-0.55, -1.0));
    float facing = max(dot(g, light), 0.0) + 0.45 * max(dot(-g, light), 0.0);
    float rim = (1.0 - smoothstep(0.0, 1.8, -sd)) * rimAlpha * (0.35 + 0.65 * facing);
    float a = wash.a * cover;
    float r = rim * cover;
    half3 col = half3(wash.rgb) * a + half3(1.0) * r;
    return half4(col, a + r * (1.0 - a));
}
"""

/**
 * [MorphButtonSurface] on Android 13 and later: the menu's glass flows out of the round button, as iOS
 * 26's glass does with a matched-geometry transition in a glass container. It starts as the button,
 * slides and grows to its place just below (or above) the button, and while the two are close they
 * are one piece of glass joined by a neck that thins and lets go as the menu settles. Closing, the menu
 * flows back up into the button and melts into it. The glass refracts along that liquid outline,
 * reads what is behind it (dark ink over light, white over dark) and follows the user's glass style.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun LiquidMorphSurface(
    anchor: Rect,
    open: Boolean,
    onDismiss: () -> Unit,
    onGone: () -> Unit,
    items: List<GlassMenuItem>,
    menuWidth: Dp,
    maxHeight: Dp,
    icon: @Composable () -> Unit,
) {
    val gone by rememberUpdatedState(onGone)
    GlassOverlay(animatesOut = true) {
        val presence = LocalOverlayPresence.current
        val shut = !open || presence?.leaving == true
        // removed from the portal for good (finished or cut short): the button is back either way
        androidx.compose.runtime.DisposableEffect(Unit) { onDispose { gone() } }
        // the button's own tint (a blue Done, a candy button): the glass keeps it at both ends
        val tint = MorphAnchors.tintAt(anchor) ?: Color.Unspecified
        BackHandler(enabled = !shut, onBack = onDismiss)
        val host = LocalGlassOverlayHost.current ?: return@GlassOverlay
        val bars = WindowInsets.systemBars
        val view = LocalView.current
        val density = LocalDensity.current

        // The circle itself becomes the menu, in three flowing movements, each starting as the one before
        // settles: it drips (a drop of glass runs down out of the circle, about a centimetre, joined to it
        // by a liquid neck), it spreads (the drop widens to the menu's full width, the circle melting into
        // it), it pours (down to the menu's full height). At rest there is only the menu, lying where the
        // button was. Closing runs it all back into the circle.
        // One motion, not three: a single spring drives the whole morph, and the three movements are
        // overlapping slices of it, so the speed flows from one into the next with no stop between them
        // (gated springs read as a machine). The spring's swell past 1 lands in the pour, as glass settling.
        val p = remember { Animatable(0f) }
        // a row chosen: the menu folds back into its button first, and the row's action runs only once
        // it is the button again (a row that opens Tools, Settings … never leaves glass on the next screen)
        var chosenAction by remember { androidx.compose.runtime.mutableStateOf<(() -> Unit)?>(null) }
        val runChosen = {
            val a = chosenAction
            chosenAction = null
            a?.invoke()
            Unit
        }
        val fade = remember { Animatable(1f) }
        LaunchedEffect(shut) {
            if (!shut) {
                // the very spring the close runs on (the owner liked its states and pace): the opening is
                // the close played backwards
                p.animateTo(1f, spring(1f, MotionSettings.stiffness(129f), 0.0005f))
            } else {
                // its button gone with its screen: dissolve too, never fold into a place that is no longer there
                if (MorphAnchors.glyphAt(anchor) == null) fade.animateTo(0f, androidx.compose.animation.core.tween(150))
                // an option picked: a quicker fold that ends the moment it looks landed (the action waits on
                // it); dismissed: the unhurried fold
                else if (chosenAction != null) p.animateTo(0f, spring(1f, MotionSettings.stiffness(260f), 0.03f))
                else p.animateTo(0f, spring(1f, MotionSettings.stiffness(129f), 0.01f))
                // the glass has landed: the real button takes its place and the morph is gone before the
                // row's action starts (an action that changes screen would otherwise slide the button away
                // while the morph's circle stayed put: two circles); then the action
                gone()
                presence?.done()
                val action = chosenAction
                chosenAction = null
                // straight after (on the next frame, once the morph has left the screen)
                if (action != null) android.os.Handler(android.os.Looper.getMainLooper()).post { action() }
            }
        }
        /** 0 while the glass is still the button's circle, 1 once it is liquid (raw progress, both ways). */
        fun liquidIn(): Float { val c = (p.value / 0.12f).coerceIn(0f, 1f); return c * c * (3f - 2f * c) }
        fun ease(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }

        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss))
        val local = Rect(host.windowToLocal(anchor.topLeft), host.windowToLocal(anchor.bottomRight))
        val px = { d: Dp -> with(density) { d.toPx() } }
        val widthPx = px(menuWidth)
        var full by remember { mutableIntStateOf(0) }
        val menuH = if (full > 0) full.toFloat() else local.height * 4f

        // the menu's place: over the button, from its corner (iOS 26), kept off the bars
        val origin = host.originInWindow
        val root = view.rootView
        val barTop = (bars.getTop(density) - origin.y).coerceAtLeast(0f)
        val barBottom = (bars.getBottom(density) - (root.height - origin.y - view.height)).coerceAtLeast(0f)
        val margin = px(12.dp)
        val screenW = view.width.toFloat()
        val screenH = view.height.toFloat()
        val fromRight = local.center.x > screenW / 2f
        val below = local.top + menuH < screenH - barBottom - margin
        val endL = (if (fromRight) local.right - widthPx else local.left).coerceIn(margin, (screenW - widthPx - margin).coerceAtLeast(margin))
        val endT = (if (below) local.top else local.bottom - menuH).coerceIn(barTop + margin, (screenH - barBottom - menuH - margin).coerceAtLeast(barTop + margin))
        val endR = endL + widthPx
        val endB = endT + menuH
        val r0 = min(local.width, local.height) / 2f
        // how far the drop runs down before it spreads: about 1.8 cm (less if the menu is shorter)
        val peek = (androidx.compose.ui.platform.LocalContext.current.resources.displayMetrics.ydpi / 2.54f * 1.8f).coerceAtMost((menuH - local.height).coerceAtLeast(0f))
        val swellPx = px(6.dp)
        val radius24 = px(24.dp)
        val neckPx = px(22.dp)
        val opening = !shut

        // The glass is ONE node of a fixed size for the whole morph (the button and the menu's place,
        // with room for the lens and the swell): nothing is laid out, resized or re-allocated per frame.
        // Each frame only the shape (shader uniforms) and the clip change, read in draw.
        val pad = px(28.dp)
        val stage = Rect(min(local.left, endL) - pad, min(local.top, endT) - pad - swellPx, max(local.right, endR) + pad, max(local.bottom, endB) + pad + swellPx)

        /** Where everything is this frame (read only in draw and layer blocks). */
        class Geo(val menu: Rect, val radius: Float, val k: Float, val circleR: Float, val motion: Float)
        fun geo(): Geo {
            val raw = p.value
            // opening, time runs slow at first (the drop gathers) and then at its normal pace through the
            // spread and the pour; closing runs straight, as it is
            // the same path both ways (no slow start on the way out)
            val pv = raw
            val d = ease(pv / 0.38f)
            val sp = ease((pv - 0.16f) / 0.46f)
            // the pour is left unclamped above, so the spring's settle shows as a swell of a few dp at most
            val pourV = if (pv >= 1f) 1f + (pv - 1f) / 0.6f else ease((pv - 0.36f) / 0.64f)
            val swellCap = swellPx / (menuH - local.height).coerceAtLeast(1f)
            val po = if (full > 0) (if (pourV > 1f) 1f + min(pourV - 1f, swellCap) else pourV) else 0f
            // the drop is born inside the circle (a small core at its middle) and grows out of it, and sinks
            // back into it the same way: it never sits beside the circle as a bubble of its own
            val dc = d.coerceIn(0f, 1f)
            val dropW = lerp(local.width * 0.5f, local.width * 0.86f, dc)
            val rl = lerp(local.center.x - dropW / 2f, endL, sp)
            val rr = lerp(local.center.x + dropW / 2f, endR, sp)
            val menu = if (below) {
                val top = lerp(local.center.y - local.height * 0.25f, endT, sp)
                val bottom = lerp(local.center.y + local.height * 0.25f, local.bottom + peek, d) + (endB - local.bottom - peek) * po
                Rect(rl, min(top, bottom - 1f), rr, max(bottom, top + 1f))
            } else {
                val bottom = lerp(local.center.y + local.height * 0.25f, endB, sp)
                val top = lerp(local.center.y - local.height * 0.25f, local.top - peek, d) - (local.top - peek - endT) * po
                Rect(rl, min(top, bottom - 1f), rr, max(bottom, top + 1f))
            }
            val radius = min(min(menu.width, menu.height) / 2f, lerp(r0, radius24, po.coerceIn(0f, 1f)))
            // a liquid neck joins the circle and the drop while the drop is out of it, and fades as the
            // circle melts into the menu
            val apart = (d / 0.35f).coerceIn(0f, 1f)
            val k = neckPx * apart * apart * (3f - 2f * apart) * (1f - po.coerceIn(0f, 1f))
            val circleR = r0 * (1f - po.coerceIn(0f, 1f)).coerceAtLeast(0f)
            val motion = kotlin.math.sin(Math.PI.toFloat() * ((d + sp + po) / 3f).coerceIn(0f, 1f))
            return Geo(menu, radius, k, circleR, motion)
        }

        val luminance = rememberGlassLuminance(enabled = true)
        val ink = adaptiveInk(luminance, clear.ink)
        val style = GlassSettings.style
        val dark = clear.dark
        val surface = remember { RuntimeShader(LiquidSurfaceShader) }
        val rimAlpha = (if (dark) 0.32f else 0.6f) * style.highlight.coerceIn(0f, 1f)
        // the menu's milk or smoke comes in as it opens and is gone by the time it is the circle again
        fun washIn() = ((p.value - 0.08f) / 0.4f).coerceIn(0f, 1f)
        // the luminance reading copies the backdrop: every few frames is plenty (its ink eases anyway)
        val frame = remember { intArrayOf(0) }

        fun RuntimeShader.shapeUniforms(g: Geo) {
            setFloatUniform("circle", local.center.x - stage.left, local.center.y - stage.top, g.circleR, 0f)
            setFloatUniform("rect", g.menu.left - stage.left, g.menu.top - stage.top, g.menu.right - stage.left, g.menu.bottom - stage.top)
            setFloatUniform("rectRadius", g.radius)
            setFloatUniform("k", g.k)
        }

        Box(
            Modifier
                .offset { IntOffset(stage.left.roundToInt(), stage.top.roundToInt()) }
                .size(with(density) { stage.width.toDp() }, with(density) { stage.height.toDp() })
                .graphicsLayer { alpha = fade.value * liquidIn() }
                .drawBackdrop(
                    backdrop = LocalBackdrop.current,
                    shape = { RectangleShape },
                    effects = {
                        val g = geo()
                        // the button's own colour and frost (as GlassCircle's), never a different tone
                        glassEffects(style, px(lerp(2f, 12f, washIn()).dp), 0f, 0f)
                        runtimeShaderEffect("LiquidMorphRefraction", LiquidRefractionShader, "content") {
                            setFloatUniform("offset", -padding, -padding)
                            // the button's own lens (12 / 24 dp at the user's style), a little livelier while it moves
                            setFloatUniform("refractionHeight", px((12f + 2f * g.motion).dp) * style.refractionHeight)
                            setFloatUniform("refractionAmount", px((24f + 4f * g.motion).dp) * style.refractionAmount)
                            setFloatUniform("depth", if (style.depthEffect) 1f else 0f)
                            setFloatUniform("dispersion", if (style.chromaticAberration) 1f else 0f)
                            shapeUniforms(g)
                        }
                    },
                    highlight = null,
                    shadow = null,
                    onDrawBackdrop = { drawIt ->
                        val rec = luminance?.recorder
                        if (rec != null && frame[0]++ % 6 == 0) rec(drawIt) else drawIt()
                    },
                    onDrawSurface = {
                        val g = geo()
                        val w = washIn()
                        val lum = luminance?.value ?: if (dark) 0.14f else 0.86f
                        val plain = adaptiveWash(lum).glassTint(style).let { it.copy(alpha = it.alpha * w) }
                        val wash = if (tint.isSpecified) androidx.compose.ui.graphics.lerp(tint.copy(alpha = 0.75f), plain, w) else plain
                        surface.setFloatUniform("wash", wash.red, wash.green, wash.blue, wash.alpha)
                        surface.setFloatUniform("rimAlpha", rimAlpha)
                        surface.shapeUniforms(g)
                        drawRect(ShaderBrush(surface))
                    },
                ),
        )

        // At the ends the glass IS the button: the very glass every round glass button wears (GlassCircle's
        // effects, lens and rim light), so the hand-off to the real button changes nothing. The liquid
        // shape takes over as the circle starts to drip, and gives it back before the circle lands.
        Box(
            Modifier
                .offset { IntOffset(local.left.roundToInt(), local.top.roundToInt()) }
                .size(with(density) { local.width.toDp() }, with(density) { local.height.toDp() })
                // on screen over the button now: the real button can step aside (from the next frame)
                .onGloballyPositioned { MorphAnchors.shownAt(anchor) }
                .graphicsLayer { alpha = fade.value * (1f - liquidIn()) }
                .drawBackdrop(
                    backdrop = LocalBackdrop.current,
                    shape = { androidx.compose.foundation.shape.CircleShape },
                    effects = { glassEffects(style, px(2.dp), px(12.dp), px(24.dp)) },
                    highlight = { glassHighlight(style) },
                    shadow = null,
                    onDrawSurface = {
                        // as GlassCircle paints a tinted button
                        if (tint.isSpecified) {
                            drawRect(tint, blendMode = androidx.compose.ui.graphics.BlendMode.Hue)
                            drawRect(tint.copy(alpha = 0.75f))
                        }
                    },
                ),
        )
        CompositionLocalProvider(LocalGlassInk provides ink, LocalMenuInk provides ink) {
          Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }) {
            // the button's glyph, fading as its circle becomes the menu
            Box(
                Modifier
                    .offset { IntOffset(local.left.roundToInt(), local.top.roundToInt()) }
                    .size(with(density) { local.width.toDp() }, with(density) { local.height.toDp() })
                    .graphicsLayer { alpha = (1f - p.value / 0.22f).coerceIn(0f, 1f) },
                contentAlignment = Alignment.Center,
            ) { icon() }
            // the rows at the menu's resting place (a fixed node), clipped each frame to the glass's
            // current rectangle in their layer: shown through the menu glass as it arrives
            Box(
                Modifier
                    .offset { IntOffset(endL.roundToInt(), (endT - swellPx).roundToInt()) }
                    .size(with(density) { widthPx.toDp() }, with(density) { (menuH + 2f * swellPx).toDp() })
                    .graphicsLayer {
                        val g = geo()
                        val ox = endL
                        val oy = endT - swellPx
                        val r = Rect(g.menu.left - ox, g.menu.top - oy, g.menu.right - ox, g.menu.bottom - oy)
                        shape = RectClip(r, g.radius)
                        clip = true
                    },
            ) {
                Box(
                    Modifier
                        .align(if (below) Alignment.TopStart else Alignment.BottomStart)
                        .padding(top = if (below) with(density) { swellPx.toDp() } else 0.dp, bottom = if (below) 0.dp else with(density) { swellPx.toDp() })
                        .wrapContentHeight(if (below) Alignment.Top else Alignment.Bottom, unbounded = true)
                        .width(menuWidth)
                        .onSizeChanged { full = it.height }
                        .graphicsLayer { alpha = ((p.value - 0.55f) / 0.35f).coerceIn(0f, 1f) },
                ) {
                    Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
                        Spacer(Modifier.height(6.dp))
                        items.forEach { item -> MorphMenuRow(item, shut) { chosenAction = item.onClick; onDismiss() } }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
          }
        }
    }
}

/** A rounded rectangle at [r] inside its layer: the rows' clip, following the glass frame by frame. */
private class RectClip(private val r: Rect, private val radius: Float) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density) =
        androidx.compose.ui.graphics.Outline.Rounded(androidx.compose.ui.geometry.RoundRect(r, androidx.compose.ui.geometry.CornerRadius(radius)))
}
