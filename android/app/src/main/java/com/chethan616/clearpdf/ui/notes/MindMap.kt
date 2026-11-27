package com.chethan616.clearpdf.ui.notes

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.glass.Feel
import com.chethan616.clearpdf.ui.glass.GlassBackButton
import com.chethan616.clearpdf.ui.glass.GlassCircle
import com.chethan616.clearpdf.ui.glass.GlassIcon
import com.chethan616.clearpdf.ui.glass.GlassMenu
import com.chethan616.clearpdf.ui.glass.GlassMenuItem
import com.chethan616.clearpdf.ui.glass.GlassPill
import com.chethan616.clearpdf.ui.glass.LocalBackdrop
import com.chethan616.clearpdf.ui.glass.LocalGlassInk
import com.chethan616.clearpdf.ui.glass.glassBar
import com.chethan616.clearpdf.ui.glass.pressable
import com.chethan616.clearpdf.ui.glass.BarGlyphButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import com.chethan616.clearpdf.ui.glass.rememberFeel
import com.chethan616.clearpdf.ui.glass.rememberGlassInk
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.clear
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.io.File
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// ---- the map ---------------------------------------------------------------------------------------

/** The most ideas a map holds (a note is not a database). */
private const val MAX_NODES = 400

/** The branch colours, in the order a map's first ideas take them. */
private val BranchColors = listOf(Candy.Sky, Candy.Bubblegum, Candy.Mint, Candy.Tangerine, Candy.Grape, Candy.Lemon, Candy.Tomato, Candy.Lime)

internal fun branchColor(i: Int): Color = if (i < 0) Candy.Sky else BranchColors[i % BranchColors.size]

/**
 * One idea of a mind map: its words, the colour picked for it (-1: its branch's), whether its
 * branch is folded away, and how far it was dragged from where the map would put it (dp). The rest
 * is set by [layoutMind] each time the map changes.
 */
internal class MindNode(var text: String, val id: String = UUID.randomUUID().toString().take(12)) {
    val children = ArrayList<MindNode>()
    var color = -1
    var collapsed = false
    var dx = 0f
    var dy = 0f

    // how its words are set (#156); each default is the map's own way for the idea's depth
    /** Bold or not; null: the depth's own weight. */
    var bold: Boolean? = null
    var italic = false
    var underline = false
    var strike = false
    /** A step on the depth's size (1: as the map sets it). */
    var size = 1f
    /** The words' colour (ARGB), 0: the map's own. */
    var ink = 0
    /** A highlight behind the words (ARGB), 0: none. */
    var mark = 0
    /** -1 start, 0 centre (the map's own), 1 end. */
    var align = 0

    /** Whether the words are set some way of their own. */
    val styled: Boolean get() = bold != null || italic || underline || strike || size != 1f || ink != 0 || mark != 0 || align != 0

    // set out by layoutMind, in pixels
    var parent: MindNode? = null
    var depth = 0
    var tint = 0
    var side = 1
    var left = 0f
    var top = 0f
    var width = 0f
    var height = 0f
    var span = 0f
    var hidden = 0
    var words: StaticLayout? = null
    var wordsKey = ""

    val cx: Float get() = left + width / 2f
    val cy: Float get() = top + height / 2f
    val right: Float get() = left + width
    val bottom: Float get() = top + height

    fun find(id: String): MindNode? {
        if (this.id == id) return this
        for (c in children) c.find(id)?.let { return it }
        return null
    }

    fun count(): Int = 1 + children.sumOf { it.count() }

    /** Whether [n] is this idea or one under it. */
    fun holds(n: MindNode): Boolean = this === n || children.any { it.holds(n) }
}

/** How a map is set out (#156). [key] is how it is kept in the note. */
internal enum class MapLayout(val key: String) {
    /** The centre in the middle, ideas to both sides in turn. */
    Balanced("both"),
    /** Every idea to one side: a left-to-right logic chart. */
    Logic("right"),
    /** Top down: each idea's own ideas in a row under it. */
    Org("org"),
    /** An indented tree, each idea on a line of its own under the one before. */
    Tree("tree"),
    /** The first ideas along a line, their own ideas above and below it in turn. */
    Timeline("timeline"),
    /** The centre in the middle, the ideas around it on rings. */
    Radial("radial"),
}

/** Which way a map reads (#156): from its words (Auto), or left to right, or right to left. */
internal enum class MapDirection(val key: String) { Auto("auto"), Ltr("ltr"), Rtl("rtl") }

/** A mind map: its centre idea, how it is set out, and which way it reads. */
internal class MindDoc(val root: MindNode, var layout: MapLayout = MapLayout.Balanced, var direction: MapDirection = MapDirection.Auto) {
    /** Every idea to one side (the logic chart); kept for the older maps' "right". */
    var oneSided: Boolean
        get() = layout == MapLayout.Logic
        set(v) { layout = if (v) MapLayout.Logic else MapLayout.Balanced }

    /**
     * Whether the map reads right to left: as set, or (Auto) as its centre's words do, else its
     * ideas' first words that have a direction.
     */
    val rtl: Boolean
        get() = when (direction) {
            MapDirection.Ltr -> false
            MapDirection.Rtl -> true
            MapDirection.Auto -> firstStrongRtl(root.text) ?: run {
                fun walk(n: MindNode): Boolean? {
                    for (c in n.children) {
                        firstStrongRtl(c.text)?.let { return it }
                        walk(c)?.let { return it }
                    }
                    return null
                }
                walk(root)
            } ?: false
        }

    /** A new mind-map note holding this map. */
    fun asNote() = com.chethan616.clearpdf.notes.Note(
        UUID.randomUUID().toString(), com.chethan616.clearpdf.notes.NoteKind.Map,
        title = root.text.trim(), body = outline(), map = toJson(),
    )
}

/** Whether [s]'s first letter with a direction is right-to-left (Arabic, Hebrew…), null when none has one. */
internal fun firstStrongRtl(s: String): Boolean? {
    for (ch in s) {
        when (Character.getDirectionality(ch)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
            Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
        }
    }
    return null
}

private fun MindNode.json(): JSONObject = JSONObject().apply {
    put("id", id)
    put("t", text)
    if (color >= 0) put("c", color)
    if (collapsed) put("f", true)
    bold?.let { put("b", it) }
    if (italic) put("i", true)
    if (underline) put("u", true)
    if (strike) put("s", true)
    if (size != 1f) put("z", size.toDouble())
    if (ink != 0) put("tc", ink)
    if (mark != 0) put("hc", mark)
    if (align != 0) put("al", align)
    if (dx != 0f) put("dx", dx.toDouble())
    if (dy != 0f) put("dy", dy.toDouble())
    if (children.isNotEmpty()) put("k", JSONArray().apply { children.forEach { put(it.json()) } })
}

private fun JSONObject.node(budget: IntArray): MindNode = MindNode(optString("t"), optString("id").ifEmpty { UUID.randomUUID().toString().take(12) }).also { n ->
    n.color = optInt("c", -1)
    n.collapsed = optBoolean("f")
    if (has("b")) n.bold = optBoolean("b")
    n.italic = optBoolean("i")
    n.underline = optBoolean("u")
    n.strike = optBoolean("s")
    n.size = optDouble("z", 1.0).toFloat().coerceIn(0.5f, 2.5f)
    n.ink = optInt("tc", 0)
    n.mark = optInt("hc", 0)
    n.align = optInt("al", 0).coerceIn(-1, 1)
    n.dx = optDouble("dx", 0.0).toFloat()
    n.dy = optDouble("dy", 0.0).toFloat()
    optJSONArray("k")?.let { a ->
        for (i in 0 until a.length()) {
            if (budget[0]-- <= 0) break
            n.children.add(a.getJSONObject(i).node(budget))
        }
    }
}

/** The map as it is kept in its note. */
internal fun MindDoc.toJson(): String =
    JSONObject().put("v", 1).put("layout", layout.key).apply { if (direction != MapDirection.Auto) put("dir", direction.key) }
        .put("root", root.json()).toString()

internal fun mindFromJson(s: String): MindDoc? {
    if (s.isBlank()) return null
    return runCatching {
        val o = JSONObject(s)
        MindDoc(
            o.getJSONObject("root").node(intArrayOf(MAX_NODES)),
            MapLayout.entries.firstOrNull { it.key == o.optString("layout") } ?: MapLayout.Balanced,
            MapDirection.entries.firstOrNull { it.key == o.optString("dir") } ?: MapDirection.Auto,
        )
    }.getOrNull()
}

/** The map's ideas as an indented outline (two spaces a level): the note's body, for search and sharing. */
internal fun MindDoc.outline(): String = buildString {
    fun walk(n: MindNode, depth: Int) {
        for (c in n.children) {
            if (c.text.isNotBlank()) {
                repeat(depth) { append("  ") }
                append(c.text.replace('\n', ' ').trim()).append('\n')
            }
            walk(c, depth + 1)
        }
    }
    walk(root, 0)
}.trimEnd()

/** The map as OPML 2.0, which desktop outliners and mind-map apps open. */
internal fun MindDoc.opml(): String = buildString {
    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("\n", "&#10;")
    append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<opml version=\"2.0\">\n  <head><title>")
    append(esc(root.text)).append("</title></head>\n  <body>\n")
    fun walk(n: MindNode, depth: Int) {
        val indent = "  ".repeat(depth + 2)
        append(indent).append("<outline text=\"").append(esc(n.text)).append('"')
        if (n.children.isEmpty()) {
            append("/>\n")
        } else {
            append(">\n")
            n.children.forEach { walk(it, depth + 1) }
            append(indent).append("</outline>\n")
        }
    }
    walk(root, 0)
    append("  </body>\n</opml>\n")
}

/**
 * An OPML outline (what desktop outliners and mind-map apps export) as a map: its `outline`
 * elements nested as ideas, each named by its `text` (or `title`) attribute. One top outline is
 * the centre; several hang from a centre named by the head's `title`. Null when it has no outline.
 * Read with a small tokenizer rather than an XML parser, so a damaged file still gives what it can.
 */
internal fun mindFromOpml(xml: String): MindDoc? {
    fun unescape(s: String): String = Regex("&(#x[0-9a-fA-F]+|#\\d+|amp|lt|gt|quot|apos);").replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e == "amp" -> "&"
            e == "lt" -> "<"
            e == "gt" -> ">"
            e == "quot" -> "\""
            e == "apos" -> "'"
            e.startsWith("#x") -> e.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
            else -> e.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
        }
    }
    val attr = Regex("""([A-Za-z_:][-\w:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)')""")
    fun attrs(s: String): Map<String, String> = attr.findAll(s).associate { it.groupValues[1].lowercase() to unescape(it.groupValues[2].ifEmpty { it.groupValues[3] }) }
    val title = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)?.let { unescape(it).trim() }.orEmpty()
    val holder = MindNode(title)
    val path = ArrayList<MindNode>().apply { add(holder) }
    var count = 0
    for (m in Regex("""<(/?)outline\b([^>]*?)(/?)>""", RegexOption.IGNORE_CASE).findAll(xml)) {
        val closing = m.groupValues[1] == "/"
        if (closing) {
            if (path.size > 1) path.removeAt(path.lastIndex)
            continue
        }
        if (++count > MAX_NODES) break
        val a = attrs(m.groupValues[2])
        val node = MindNode((a["text"] ?: a["title"] ?: "").replace('\n', ' ').trim())
        path.last().children.add(node)
        if (m.groupValues[3] != "/") path.add(node)
    }
    if (holder.children.isEmpty()) return null
    // one top outline is the centre itself
    return MindDoc(holder.children.singleOrNull() ?: holder)
}

/**
 * A note's outline as a tree. One idea a line: headings (`#`, `##`…) open branches, and under
 * them, or with no headings at all, indentation (two spaces or a tab a step) and bullets (`-`,
 * `*`, `•`, `1.`) nest ideas under the line above. [title] is the map's centre.
 */
internal fun parseOutline(title: String, body: String): MindNode {
    val root = MindNode(title.trim())
    // each open idea with how deep it sits (the centre at -1)
    val path = ArrayList<Pair<MindNode, Int>>().apply { add(root to -1) }
    var base = 0
    var count = 0
    for (raw in body.lines()) {
        if (raw.isBlank()) continue
        var indent = 0
        for (ch in raw) {
            if (ch == ' ') indent++ else if (ch == '\t') indent += 2 else break
        }
        var s = raw.trim()
        val hashes = s.takeWhile { it == '#' }.length
        val level: Int
        if (hashes in 1..6 && s.getOrNull(hashes) == ' ') {
            level = hashes - 1
            base = hashes
            s = s.drop(hashes)
        } else {
            Regex("""^([-*•+]|\d{1,3}[.)])\s+""").find(s)?.let { s = s.substring(it.range.last + 1) }
            level = base + indent / 2
        }
        s = s.replace("**", "").replace("__", "").trim()
        if (s.isEmpty()) continue
        if (++count > MAX_NODES) break
        while (path.size > 1 && path.last().second >= level) path.removeAt(path.lastIndex)
        val node = MindNode(s)
        val (parent, depth) = path.last()
        parent.children.add(node)
        path.add(node to depth + 1)
    }
    return root
}

// ---- laid out --------------------------------------------------------------------------------------

/** A map set out: every idea shown (in drawing order) with its box placed, and the room they take. */
internal class MindLayout(val doc: MindDoc, val density: Float, val nodes: List<MindNode>, val bounds: RectF, val rtl: Boolean = false) {
    /** The idea at ([x], [y]), the one drawn on top; [skip]'s branch is looked through (an idea being dragged). */
    fun hit(x: Float, y: Float, slop: Float, skip: MindNode? = null): MindNode? =
        nodes.lastOrNull { (skip == null || !skip.holds(it)) && x >= it.left - slop && x <= it.right + slop && y >= it.top - slop && y <= it.bottom + slop }

    /** The folded idea whose count badge is at ([x], [y]). */
    fun badgeAt(x: Float, y: Float): MindNode? =
        nodes.firstOrNull { it.hidden > 0 && hypot(x - badgeX(it), y - it.cy) <= 17f * density }
}

private fun badgeX(n: MindNode) = if (n.side >= 0) n.right else n.left

/** The app's own face (Plus Jakarta Sans) for the map's words, once a screen has loaded it. */
internal object MapType {
    @Volatile
    private var base: Typeface? = null

    val loaded: Boolean get() = base != null

    fun load(context: android.content.Context) {
        if (base == null) base = runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, R.font.outfit) }.getOrNull()
    }

    fun face(weight: Int): Typeface = Typeface.create(base ?: Typeface.DEFAULT, weight, false)
}

private fun wordsPaint(d: Float, depth: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = when (depth) {
        0 -> 19f
        1 -> 16f
        else -> 15f
    } * d
    typeface = MapType.face(if (depth == 0) 800 else if (depth == 1) 700 else 500)
}

/**
 * Sets [doc] out the way mind maps are drawn: the centre in the middle, its ideas branching out to
 * the right and the left in turn (or all to the right), each idea's own ideas stacked beside it and
 * centred on it. A dragged idea keeps its offset, and its branch moves with it. Words wrap past a
 * width and are measured once until they change.
 */
internal fun layoutMind(doc: MindDoc, d: Float, rootHint: String, hint: String): MindLayout {
    val paints = Array(3) { wordsPaint(d, it) }
    val shown = ArrayList<MindNode>()
    val rtl = doc.rtl
    // each idea's words read their own way (mixed Arabic and English in one idea come out right);
    // a right-to-left map's ideas start at the right
    val direction = when (doc.direction) {
        MapDirection.Ltr -> android.text.TextDirectionHeuristics.FIRSTSTRONG_LTR
        MapDirection.Rtl -> android.text.TextDirectionHeuristics.FIRSTSTRONG_RTL
        MapDirection.Auto -> if (rtl) android.text.TextDirectionHeuristics.FIRSTSTRONG_RTL else android.text.TextDirectionHeuristics.FIRSTSTRONG_LTR
    }
    fun measure(n: MindNode, parent: MindNode?, depth: Int, branch: Int) {
        n.parent = parent
        n.depth = depth
        n.tint = when {
            depth == 0 -> -1
            n.color >= 0 -> n.color
            depth == 1 -> branch
            else -> parent!!.tint
        }
        val plain = n.text.ifBlank { if (depth == 0) rootHint else hint }
        val key = "$d|$depth|${MapType.loaded}|${n.text.isBlank()}|$plain|${n.bold}|${n.italic}|${n.underline}|${n.strike}|${n.size}|${n.mark}|${n.align}|$rtl|${doc.direction}"
        if (n.words == null || n.wordsKey != key) {
            val base = paints[depth.coerceAtMost(2)]
            // the idea's own style on the depth's (#156)
            val p = if (n.bold == null && !n.italic && !n.underline && !n.strike && n.size == 1f) base else TextPaint(base).apply {
                n.bold?.let { typeface = MapType.face(if (it) 800 else 400) }
                if (n.italic) textSkewX = -0.22f
                isUnderlineText = n.underline
                isStrikeThruText = n.strike
                textSize = base.textSize * n.size
            }
            val words: CharSequence = if (n.mark == 0 || n.text.isBlank()) plain else android.text.SpannableString(plain).apply {
                setSpan(android.text.style.BackgroundColorSpan(n.mark), 0, plain.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val max = (if (depth == 0) 240f else 210f) * d * n.size.coerceAtLeast(1f)
            val w = ceil(min(Layout.getDesiredWidth(words, p), max)).toInt().coerceAtLeast(1)
            n.words = StaticLayout.Builder.obtain(words, 0, words.length, p, w)
                .setAlignment(
                    when (n.align) {
                        -1 -> Layout.Alignment.ALIGN_NORMAL
                        1 -> Layout.Alignment.ALIGN_OPPOSITE
                        else -> Layout.Alignment.ALIGN_CENTER
                    },
                )
                .setTextDirection(direction)
                .setIncludePad(false)
                .setMaxLines(6)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            n.wordsKey = key
        }
        val padX = when (depth) {
            0 -> 26f
            1 -> 18f
            else -> 14f
        } * d
        val padY = when (depth) {
            0 -> 16f
            1 -> 11f
            else -> 9f
        } * d
        val words2 = n.words!!
        n.width = max(words2.width + padX * 2f, (if (depth == 0) 96f else 56f) * d)
        n.height = words2.height + padY * 2f
        n.hidden = if (n.collapsed) n.count() - 1 else 0
        shown.add(n)
        if (!n.collapsed) n.children.forEachIndexed { i, c -> measure(c, n, depth + 1, if (depth == 0) i else branch) }
    }
    measure(doc.root, null, 0, 0)

    val gapRoot = 24f * d
    val gap = 12f * d
    fun spans(n: MindNode): Float {
        val kids = if (n.collapsed) emptyList() else n.children
        val inner = kids.sumOf { spans(it).toDouble() }.toFloat() + (if (n.depth == 0) gapRoot else gap) * (kids.size - 1).coerceAtLeast(0)
        n.span = max(n.height, inner)
        return n.span
    }
    spans(doc.root)

    fun place(n: MindNode, side: Int, edge: Float, centreY: Float) {
        n.side = side
        val reach = (if (n.depth == 1) 64f else 40f) * d
        n.left = if (side > 0) edge + reach else edge - reach - n.width
        n.top = centreY - n.height / 2f
        if (n.collapsed || n.children.isEmpty()) return
        val total = n.children.sumOf { it.span.toDouble() }.toFloat() + gap * (n.children.size - 1)
        var y = centreY - total / 2f
        for (c in n.children) {
            place(c, side, if (side > 0) n.right else n.left, y + c.span / 2f)
            y += c.span + gap
        }
    }
    val root = doc.root
    root.side = 0
    root.left = -root.width / 2f
    root.top = -root.height / 2f
    when (doc.layout) {
        MapLayout.Balanced, MapLayout.Logic -> if (!root.collapsed) {
            for (side in intArrayOf(1, -1)) {
                val kids = root.children.filterIndexed { i, _ -> if (side > 0) doc.oneSided || i % 2 == 0 else !doc.oneSided && i % 2 == 1 }
                if (kids.isEmpty()) continue
                val total = kids.sumOf { it.span.toDouble() }.toFloat() + gapRoot * (kids.size - 1)
                var y = -total / 2f
                for (c in kids) {
                    place(c, side, if (side > 0) root.right else root.left, y + c.span / 2f)
                    y += c.span + gapRoot
                }
            }
        }
        MapLayout.Org -> layoutOrg(root, d)
        MapLayout.Tree -> layoutTree(root, d)
        MapLayout.Timeline -> layoutTimeline(root, d)
        MapLayout.Radial -> layoutRadial(root, d)
    }
    // right to left: the whole map mirrored, so its branches grow to the left (dragged offsets are
    // the screen's, so they are added after)
    if (rtl) for (n in shown) {
        n.left = -(n.left + n.width)
        n.side = -n.side
    }
    // dragged ideas, with their branches
    fun shift(n: MindNode, ox: Float, oy: Float) {
        val x = ox + n.dx * d
        val y = oy + n.dy * d
        n.left += x
        n.top += y
        if (!n.collapsed) n.children.forEach { shift(it, x, y) }
    }
    shift(root, 0f, 0f)

    val bounds = RectF(root.left, root.top, root.right, root.bottom)
    for (n in shown) {
        bounds.union(n.left, n.top, n.right, n.bottom)
        if (n.hidden > 0) bounds.union(badgeX(n) - 12f * d, n.cy - 12f * d, badgeX(n) + 12f * d, n.cy + 12f * d)
    }
    return MindLayout(doc, d, shown, bounds, rtl)
}

/** The shown ideas under [n] (its own, unless it is folded). */
private fun MindNode.shownKids(): List<MindNode> = if (collapsed) emptyList() else children

/** Top down (#156): each idea centred over the row of its own ideas. */
private fun layoutOrg(root: MindNode, d: Float) {
    val gap = 14f * d
    fun width(n: MindNode): Float {
        val kids = n.shownKids()
        val inner = kids.sumOf { width(it).toDouble() }.toFloat() + gap * (kids.size - 1).coerceAtLeast(0)
        n.span = max(n.width, inner)
        return n.span
    }
    width(root)
    fun place(n: MindNode, cx: Float, top: Float) {
        n.side = 1
        n.left = cx - n.width / 2f
        n.top = top
        val kids = n.shownKids()
        if (kids.isEmpty()) return
        val total = kids.sumOf { it.span.toDouble() }.toFloat() + gap * (kids.size - 1)
        var x = cx - total / 2f
        val below = n.bottom + (if (n.depth == 0) 56f else 40f) * d
        for (c in kids) {
            place(c, x + c.span / 2f, below)
            x += c.span + gap
        }
    }
    place(root, 0f, -root.height / 2f)
}

/** An indented tree (#156): the centre at the top, each idea on its own line, a step in from its parent. */
private fun layoutTree(root: MindNode, d: Float) {
    val step = 30f * d
    val gap = 10f * d
    var y = root.bottom + 18f * d
    fun place(n: MindNode) {
        for (c in n.shownKids()) {
            c.side = 1
            c.left = n.left + step
            c.top = y
            y = c.bottom + gap
            place(c)
        }
    }
    root.left = 0f
    place(root)
}

/**
 * A timeline (#156): the centre at the start, its ideas along a line after it, and their own ideas
 * stacked under and over the line in turn, each a step in from the one it belongs to.
 */
private fun layoutTimeline(root: MindNode, d: Float) {
    val reach = 56f * d
    val apart = 28f * d
    val off = 26f * d
    val step = 22f * d
    val gap = 10f * d
    root.left = -root.width
    var x = root.right + reach
    root.shownKids().forEachIndexed { i, stop ->
        stop.side = 1
        stop.left = x
        stop.top = -stop.height / 2f
        val down = i % 2 == 0
        var y = if (down) stop.bottom + off else stop.top - off
        var right = stop.right
        fun place(n: MindNode, indent: Float) {
            for (c in n.shownKids()) {
                c.side = 1
                c.left = stop.left + indent
                if (down) {
                    c.top = y
                    y = c.bottom + gap
                } else {
                    c.top = y - c.height
                    y = c.top - gap
                }
                right = max(right, c.right)
                place(c, indent + step)
            }
        }
        place(stop, step)
        x = right + apart
    }
}

/**
 * A radial map (#156): the centre in the middle, each ring of ideas round it, every idea given a
 * share of its parent's arc by how many ideas end under it, so a big branch has the room it needs.
 */
private fun layoutRadial(root: MindNode, d: Float) {
    fun leaves(n: MindNode): Int {
        val kids = n.shownKids()
        n.span = if (kids.isEmpty()) 1f else kids.sumOf { leaves(it) }.toFloat()
        return n.span.toInt()
    }
    leaves(root)
    // each ring far enough out for the ideas on it to sit side by side
    val ringWidth = HashMap<Int, Float>()
    fun widths(n: MindNode) {
        for (c in n.shownKids()) {
            ringWidth[c.depth] = (ringWidth[c.depth] ?: 0f) + max(c.width, c.height) + 18f * d
            widths(c)
        }
    }
    widths(root)
    val radius = HashMap<Int, Float>()
    var r = max(root.width, root.height) / 2f
    for (depth in 1..(ringWidth.keys.maxOrNull() ?: 0)) {
        r = max(r + 120f * d, (ringWidth[depth] ?: 0f) / (2f * Math.PI.toFloat()))
        radius[depth] = r
    }
    fun place(n: MindNode, from: Float, to: Float) {
        val kids = n.shownKids()
        if (kids.isEmpty()) return
        var a = from
        val each = (to - from) / n.span
        for (c in kids) {
            val share = each * c.span
            val mid = a + share / 2f
            val rr = radius[c.depth] ?: 0f
            val cx = rr * kotlin.math.cos(mid)
            val cy = rr * kotlin.math.sin(mid)
            c.side = if (cx >= 0f) 1 else -1
            c.left = cx - c.width / 2f
            c.top = cy - c.height / 2f
            place(c, a, a + share)
            a += share
        }
    }
    // the first idea at the top, the rest round clockwise
    place(root, -Math.PI.toFloat() / 2f, 3f * Math.PI.toFloat() / 2f)
}

// ---- drawn -----------------------------------------------------------------------------------------

/** A branch from ([x0], [y0]) to ([x1], [y1]): a soft S-curve that thins from [w0] to [w1]. */
private fun taper(path: Path, x0: Float, y0: Float, x1: Float, y1: Float, w0: Float, w1: Float) {
    val mx = (x0 + x1) / 2f
    taper(path, x0, y0, mx, y0, mx, y1, x1, y1, w0, w1)
}

/** A branch along the curve from ([x0], [y0]) to ([x1], [y1]) through two handles, thinning from [w0] to [w1]. */
private fun taper(path: Path, x0: Float, y0: Float, ax: Float, ay: Float, bx: Float, by: Float, x1: Float, y1: Float, w0: Float, w1: Float) {
    path.reset()
    val steps = 22
    val xs = FloatArray(steps + 1)
    val ys = FloatArray(steps + 1)
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val u = 1f - t
        xs[i] = u * u * u * x0 + 3f * u * u * t * ax + 3f * u * t * t * bx + t * t * t * x1
        ys[i] = u * u * u * y0 + 3f * u * u * t * ay + 3f * u * t * t * by + t * t * t * y1
    }
    val lx = FloatArray(steps + 1)
    val ly = FloatArray(steps + 1)
    val rx = FloatArray(steps + 1)
    val ry = FloatArray(steps + 1)
    for (i in 0..steps) {
        val a = max(i - 1, 0)
        val b = min(i + 1, steps)
        var tx = xs[b] - xs[a]
        var ty = ys[b] - ys[a]
        val len = hypot(tx, ty).coerceAtLeast(0.001f)
        tx /= len
        ty /= len
        val half = lerp(w0, w1, i / steps.toFloat()) / 2f
        lx[i] = xs[i] - ty * half
        ly[i] = ys[i] + tx * half
        rx[i] = xs[i] + ty * half
        ry[i] = ys[i] - tx * half
    }
    path.moveTo(lx[0], ly[0])
    for (i in 1..steps) path.lineTo(lx[i], ly[i])
    for (i in steps downTo 0) path.lineTo(rx[i], ry[i])
    path.close()
    path.addCircle(x1, y1, w1 / 2f, Path.Direction.CW)
}

/**
 * Draws [layout] on [canvas] (the screen, a picture or a PDF page alike): branches that thin as
 * they go out, in their branch's colour; the centre as a dark capsule, its first ideas filled with
 * their colour, the rest as cards edged with it. A folded idea carries the count of what it hides;
 * [selected] wears a ring, and [target] (where a dragged idea would go) a green one.
 */
internal fun drawMind(canvas: android.graphics.Canvas, layout: MindLayout, dark: Boolean, selected: String? = null, target: String? = null, found: Set<String> = emptySet()) {
    val d = layout.density
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * d
        typeface = MapType.face(800)
        textAlign = Paint.Align.CENTER
        color = android.graphics.Color.WHITE
    }
    val path = Path()
    val box = RectF()
    val shape = layout.doc.layout
    val rtl = layout.rtl
    for (n in layout.nodes) {
        val p = n.parent ?: continue
        val toRight = n.cx >= p.cx
        val (w0, w1) = when (n.depth) {
            1 -> 8f to 3f
            2 -> 3.8f to 2f
            else -> 2.6f to 1.5f
        }
        // each layout's own branches (#156): sideways S-curves, down from the parent, elbows from
        // its edge, a line from stop to stop, or straight spokes out of the middle
        val elbowX = if (rtl) p.right - min(16f * d, p.width / 2f) else p.left + min(16f * d, p.width / 2f)
        when {
            shape == MapLayout.Org -> {
                val my = (p.bottom + n.top) / 2f
                taper(path, p.cx, p.bottom, p.cx, my, n.cx, my, n.cx, n.top, w0 * d, w1 * d)
            }
            shape == MapLayout.Tree || (shape == MapLayout.Timeline && n.depth >= 2) -> {
                val below = n.cy > p.cy
                val y0 = if (below) p.bottom else p.top
                val x1 = if (rtl) n.right else n.left
                taper(path, elbowX, y0, elbowX, n.cy, elbowX, n.cy, x1, n.cy, w0 * d, w1 * d)
            }
            shape == MapLayout.Timeline -> {
                // the line runs from stop to stop: from the one before, or from the centre
                val before = p.children.getOrNull(p.children.indexOf(n) - 1)
                val from = before ?: p
                val x0 = if (rtl) from.left else from.right
                val x1 = if (rtl) n.right else n.left
                taper(path, x0, from.cy, x1, n.cy, if (before == null) w0 * d else w1 * d * 1.6f, w1 * d * 1.6f)
            }
            shape == MapLayout.Radial -> {
                val ax = p.cx + (n.cx - p.cx) / 3f
                val ay = p.cy + (n.cy - p.cy) / 3f
                val bx = p.cx + (n.cx - p.cx) * 2f / 3f
                val by = p.cy + (n.cy - p.cy) * 2f / 3f
                taper(path, p.cx, p.cy, ax, ay, bx, by, n.cx, n.cy, w0 * d, w1 * d)
            }
            else -> {
                val x0 = if (p.depth == 0) p.cx + (if (toRight) 1f else -1f) * p.width * 0.3f else if (toRight) p.right else p.left
                val x1 = if (toRight) n.left else n.right
                taper(path, x0, p.cy, x1, n.cy, w0 * d, w1 * d)
            }
        }
        fill.color = branchColor(n.tint).copy(alpha = if (dark) 0.9f else 0.82f).toArgb()
        canvas.drawPath(path, fill)
    }
    val ink = if (dark) Color(0xFFF1F3F9) else Color(0xFF181B26)
    for (n in layout.nodes) {
        val colour = branchColor(n.tint)
        box.set(n.left, n.top, n.right, n.bottom)
        val radius = min(n.height / 2f, (if (n.depth == 0) 26f else if (n.depth == 1) 18f else 13f) * d)
        fill.color = Color.Black.copy(alpha = if (dark) 0.3f else 0.08f).toArgb()
        box.offset(0f, 2.5f * d)
        canvas.drawRoundRect(box, radius, radius, fill)
        box.offset(0f, -2.5f * d)
        val words: Color
        when (n.depth) {
            0 -> {
                fill.color = (if (dark) Color(0xFFF2F4FA) else Color(0xFF1E2233)).toArgb()
                words = if (dark) Color(0xFF14152B) else Color.White
            }
            1 -> {
                fill.color = colour.toArgb()
                words = if (colour.luminance() > 0.5f) Color(0xFF1B1B1F) else Color.White
            }
            else -> {
                fill.color = (if (dark) Color(0xFF242842) else Color.White).toArgb()
                words = ink
            }
        }
        canvas.drawRoundRect(box, radius, radius, fill)
        if (n.depth >= 2) {
            stroke.strokeWidth = 1.6f * d
            stroke.color = colour.copy(alpha = 0.8f).toArgb()
            canvas.drawRoundRect(box, radius, radius, stroke)
        }
        n.words?.let { w ->
            val own = if (n.ink != 0 && n.text.isNotBlank()) Color(n.ink) else words
            w.paint.color = (if (n.text.isBlank()) words.copy(alpha = 0.5f) else own).toArgb()
            canvas.save()
            canvas.translate(n.cx - w.width / 2f, n.cy - w.height / 2f)
            w.draw(canvas)
            canvas.restore()
        }
        if (n.hidden > 0) {
            val bx = badgeX(n)
            fill.color = (if (n.depth == 0) Candy.Sky else colour).toArgb()
            canvas.drawCircle(bx, n.cy, 11f * d, fill)
            stroke.strokeWidth = 2f * d
            stroke.color = (if (dark) Color(0xFF14152B) else Color.White).toArgb()
            canvas.drawCircle(bx, n.cy, 11f * d, stroke)
            val label = if (n.hidden > 99) "99+" else n.hidden.toString()
            canvas.drawText(label, bx, n.cy - (badge.descent() + badge.ascent()) / 2f, badge)
        }
        if (n.id == selected || n.id == target || n.id in found) {
            val ring = 5f * d
            stroke.strokeWidth = 2.5f * d
            stroke.color = (if (n.id == target) Candy.Mint else if (n.id == selected) Candy.Sky else Candy.Tangerine).toArgb()
            box.inset(-ring, -ring)
            canvas.drawRoundRect(box, radius + ring, radius + ring, stroke)
        }
    }
}

/** [doc] as a picture (PNG, at most 4096 pixels on its long side), a one-page PDF, or OPML. */
private fun writeMind(doc: MindDoc, d: Float, dark: Boolean, kind: String, out: File, rootHint: String, hint: String) {
    if (kind == "opml") {
        out.writeText(doc.opml())
        return
    }
    val layout = layoutMind(doc, d, rootHint, hint)
    val pad = 36f * d
    val b = RectF(layout.bounds).apply { inset(-pad, -pad) }
    if (kind == "pdf") {
        // a page of the map's own size, a dp to a point, on white (it is for printing)
        val doc2 = PdfDocument()
        try {
            val page = doc2.startPage(PdfDocument.PageInfo.Builder((b.width() / d).toInt().coerceAtLeast(1), (b.height() / d).toInt().coerceAtLeast(1), 1).create())
            page.canvas.drawColor(android.graphics.Color.WHITE)
            page.canvas.scale(1f / d, 1f / d)
            page.canvas.translate(-b.left, -b.top)
            drawMind(page.canvas, layout, dark = false)
            doc2.finishPage(page)
            out.outputStream().use { doc2.writeTo(it) }
        } finally {
            doc2.close()
        }
    } else {
        val k = min(1f, 4096f / max(b.width(), b.height()))
        val bitmap = Bitmap.createBitmap((b.width() * k).toInt().coerceAtLeast(1), (b.height() * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor((if (dark) Color(0xFF14152B) else Color(0xFFF8F9FC)).toArgb())
        canvas.scale(k, k)
        canvas.translate(-b.left, -b.top)
        drawMind(canvas, layout, dark)
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}

private fun safeName(s: String, fallback: String) =
    s.replace(Regex("""[\\/:*?"<>|\n\r]"""), " ").trim().take(60).ifEmpty { fallback }

/** The faint dot grid a map lies on, moving and scaling with it. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.dots(pan: Offset, scale: Float, d: Float, colour: Color) {
    var step = 26f * d * scale
    while (step < 14f * d) step *= 2f
    val points = ArrayList<Offset>()
    var x = ((pan.x % step) + step) % step
    while (x < size.width) {
        var y = ((pan.y % step) + step) % step
        while (y < size.height) {
            points.add(Offset(x, y))
            y += step
        }
        x += step
    }
    drawPoints(points, androidx.compose.ui.graphics.PointMode.Points, colour, strokeWidth = 2.4f * d, cap = StrokeCap.Round)
}

// ---- the editor ------------------------------------------------------------------------------------

/**
 * A mind-map note. Tap an idea to pick it and tap it again to type; the bar under it adds an idea
 * under it or beside it, colours its branch, folds it away or deletes it. Drag an idea to move it
 * (its branch comes along), or drop it on another idea to put it there. Drag the paper to look
 * around and pinch to zoom. Every change is kept with the note as it is made, and can be undone.
 */
@Composable
fun MindMapEditor(vm: AppViewModel, id: String) {
    val note = vm.notes.get(id) ?: run {
        LaunchedEffect(id) { vm.openNote = null }
        return
    }
    val c = clear
    val d = LocalDensity.current.density
    val context = LocalContext.current
    remember(context) { MapType.load(context) }
    val scope = rememberCoroutineScope()
    val feel = rememberFeel()
    val keyboard = LocalSoftwareKeyboardController.current
    val rootHint = stringResource(R.string.mindmap_root_hint)
    val hint = stringResource(R.string.mindmap_node_hint)
    val fallback = stringResource(R.string.mindmap_title)

    var doc by remember(id) { mutableStateOf(mindFromJson(note.map) ?: MindDoc(parseOutline(note.title, note.body))) }
    var version by remember { mutableIntStateOf(0) }
    val undo = remember(id) { ArrayList<String>() }
    val redo = remember(id) { ArrayList<String>() }
    var history by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }
    var fresh by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue()) }
    // the picked idea's Style panel: its words' style and colours, and its branch's colour
    var palette by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<String?>(null) }
    // #156: looking for words in the map, the map as an outline, and choosing its layout and direction
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var foundAt by remember { mutableIntStateOf(0) }
    var outlining by remember { mutableStateOf(false) }
    var outlineAt by remember { mutableStateOf<String?>(null) }
    var outlineFocus by remember { mutableStateOf<String?>(null) }
    var layoutMenu by remember { mutableStateOf(false) }
    var directionMenu by remember { mutableStateOf(false) }
    var menuAt by remember { mutableStateOf<Rect?>(null) }
    var moreAnchor by remember { mutableStateOf(Rect.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var view by remember { mutableStateOf(IntSize.Zero) }
    var fitted by remember { mutableStateOf(false) }
    val layout = remember(doc, version, d, rootHint, hint) { layoutMind(doc, d, rootHint, hint) }
    val current by rememberUpdatedState(layout)
    val focus = remember { FocusRequester() }
    var glide by remember { mutableStateOf<Job?>(null) }

    fun save() {
        val n = vm.notes.get(id) ?: return
        val json = doc.toJson()
        if (n.map != json) vm.notes.put(n.copy(title = doc.root.text.trim(), body = doc.outline(), map = json))
    }
    fun changed() {
        version++
        save()
    }
    // the map as it was, kept before each change for Undo
    fun keep() {
        undo.add(doc.toJson())
        if (undo.size > 80) undo.removeAt(0)
        redo.clear()
        history++
    }
    fun glideTo(s: Float, p: Offset) {
        glide?.cancel()
        val s0 = scale
        val p0 = pan
        glide = scope.launch {
            animate(0f, 1f, animationSpec = spring(dampingRatio = 0.9f, stiffness = 240f)) { v, _ ->
                scale = lerp(s0, s, v)
                pan = androidx.compose.ui.geometry.lerp(p0, p, v)
            }
        }
    }
    fun fit(animated: Boolean = true) {
        if (view.width == 0) return
        val b = current.bounds
        val top = 92f * d
        val bottom = 130f * d
        val w = view.width - 40f * d
        val h = (view.height - top - bottom).coerceAtLeast(100f)
        val s = min(w / b.width().coerceAtLeast(1f), h / b.height().coerceAtLeast(1f)).coerceIn(0.25f, 1.25f)
        val p = Offset(view.width / 2f - b.centerX() * s, top + h / 2f - b.centerY() * s)
        if (animated) glideTo(s, p) else {
            scale = s
            pan = p
        }
    }
    fun show(n: MindNode) {
        val s = scale.coerceIn(0.85f, 1.6f)
        glideTo(s, Offset(view.width / 2f, view.height * 0.3f) - Offset(n.cx, n.cy) * s)
    }
    fun startEdit(n: MindNode, isNew: Boolean) {
        if (!isNew) keep()
        fresh = isNew
        palette = false
        selected = n.id
        editing = n.id
        field = TextFieldValue(n.text, TextRange(0, n.text.length))
    }
    fun finishEdit() {
        val n = editing?.let { doc.root.find(it) }
        editing = null
        if (n != null) {
            n.text = n.text.trim()
            // an idea left without words goes again
            if (n.text.isEmpty() && n !== doc.root) {
                n.parent?.children?.remove(n)
                selected = n.parent?.id
            }
        }
        changed()
        // nothing came of it: no step to undo
        if (undo.isNotEmpty() && undo.last() == doc.toJson()) {
            undo.removeAt(undo.lastIndex)
            history++
        }
    }
    fun addChild(p: MindNode) {
        keep()
        p.collapsed = false
        val n = MindNode("")
        p.children.add(n)
        version++
        startEdit(n, isNew = true)
        feel(Feel.Rise)
    }
    fun addSibling(n: MindNode) {
        val p = n.parent ?: return addChild(n)
        keep()
        val s = MindNode("")
        p.children.add(p.children.indexOf(n) + 1, s)
        version++
        startEdit(s, isNew = true)
        feel(Feel.Rise)
    }
    fun delete(n: MindNode) {
        val p = n.parent ?: return
        keep()
        p.children.remove(n)
        selected = p.id
        changed()
        feel(Feel.Fall)
    }
    fun fold(n: MindNode) {
        if (n.children.isEmpty()) return
        keep()
        n.collapsed = !n.collapsed
        changed()
        feel(if (n.collapsed) Feel.ToggleOff else Feel.ToggleOn)
    }
    fun colour(n: MindNode, i: Int) {
        if (n.color == i) return
        keep()
        n.color = i
        changed()
        feel(Feel.Tick)
    }
    /** The picked idea's words set another way (#156): one step to undo. */
    fun style(n: MindNode, change: MindNode.() -> Unit) {
        keep()
        n.change()
        changed()
        feel(Feel.Tick)
    }
    /** The ideas holding the words looked for, in the map's order (folded branches too). */
    fun matches(): List<MindNode> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<MindNode>()
        fun walk(n: MindNode) {
            if (n.text.contains(q, ignoreCase = true)) out.add(n)
            n.children.forEach(::walk)
        }
        walk(doc.root)
        return out
    }
    /** [n] picked and in view: the branches folded over it opened, then the map glides to it. */
    fun reveal(n: MindNode) {
        val trail = ArrayList<MindNode>()
        fun path(at: MindNode): Boolean {
            if (at === n) return true
            trail.add(at)
            for (c in at.children) if (path(c)) return true
            trail.removeAt(trail.lastIndex)
            return false
        }
        path(doc.root)
        var opened = false
        trail.forEach { if (it.collapsed) { it.collapsed = false; opened = true } }
        if (opened) changed()
        selected = n.id
        palette = false
        scope.launch {
            delay(40)
            current.doc.root.find(n.id)?.let { show(it) }
        }
    }
    fun pickLayout(l: MapLayout) {
        if (doc.layout == l) return
        keep()
        doc.layout = l
        changed()
        scope.launch {
            delay(30)
            fit()
        }
    }
    fun pickDirection(dir: MapDirection) {
        if (doc.direction == dir) return
        keep()
        doc.direction = dir
        changed()
        scope.launch {
            delay(30)
            fit()
        }
    }
    // the outline (#156): a new idea after [n] (the first under the centre), indent and outdent
    fun outlineNew(n: MindNode, parent: MindNode?) {
        keep()
        val fresh = MindNode("")
        if (parent == null) {
            n.collapsed = false
            n.children.add(0, fresh)
        } else {
            parent.children.add(parent.children.indexOf(n) + 1, fresh)
        }
        changed()
        outlineFocus = fresh.id
    }
    fun indent(n: MindNode, parent: MindNode?) {
        val p = parent ?: return
        val i = p.children.indexOf(n)
        if (i <= 0) return
        keep()
        val before = p.children[i - 1]
        p.children.removeAt(i)
        before.collapsed = false
        before.children.add(n)
        changed()
        outlineFocus = n.id
    }
    fun outdent(n: MindNode, parent: MindNode?, grand: MindNode?) {
        val p = parent ?: return
        val g = grand ?: return
        keep()
        p.children.remove(n)
        g.children.add(g.children.indexOf(p) + 1, n)
        changed()
        outlineFocus = n.id
    }
    fun step(from: ArrayList<String>, to: ArrayList<String>) {
        if (editing != null) finishEdit()
        val s = from.removeLastOrNull() ?: return
        to.add(doc.toJson())
        mindFromJson(s)?.let { doc = it }
        if (selected?.let { doc.root.find(it) } == null) selected = null
        history++
        save()
        feel(Feel.Tick)
    }
    fun export(kind: String) {
        val copy = mindFromJson(doc.toJson()) ?: return
        val name = safeName(doc.root.text, fallback)
        val dark = c.dark
        scope.launch {
            val file = withContext(Dispatchers.Default) {
                runCatching { File(context.cacheDir, "map-${System.nanoTime()}.$kind").also { writeMind(copy, d, dark, kind, it, rootHint, hint) } }.getOrNull()
            }
            val mime = when (kind) {
                "pdf" -> "application/pdf"
                "png" -> "image/png"
                else -> "text/x-opml"
            }
            if (file == null) vm.tell(context.getString(R.string.common_file_unreadable)) else vm.deliverFile(file, "$name.$kind", mime)
        }
    }

    BackHandler {
        when {
            searching -> {
                searching = false
                query = ""
            }
            outlining -> outlining = false
            editing != null -> finishEdit()
            selected != null -> {
                selected = null
                palette = false
            }
            else -> vm.closeNote()
        }
    }
    LaunchedEffect(view) {
        if (!fitted && view.width > 0) {
            fit(animated = false)
            fitted = true
        }
    }
    // a new map starts by naming its centre
    LaunchedEffect(id) {
        if (doc.root.text.isBlank() && doc.root.children.isEmpty()) startEdit(doc.root, isNew = true)
    }
    // the keyboard put away ends the typing, so no cursor is left blinking in the bar
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    val imeOpen = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    var imeWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(imeOpen) {
        if (imeOpen) imeWasOpen = true
        else if (imeWasOpen) {
            // a moment's grace: going on to the next idea hands the keyboard from one field to the next
            delay(350)
            imeWasOpen = false
            if (editing != null) finishEdit()
        }
    }
    LaunchedEffect(editing) {
        val n = editing?.let { current.doc.root.find(it) } ?: return@LaunchedEffect
        // the new idea's field takes the keyboard at once, then the map glides to it
        runCatching { focus.requestFocus() }
        keyboard?.show()
        show(n)
    }

    val chosen = selected?.let { doc.root.find(it) }?.takeIf { s -> layout.nodes.any { it === s } }
    // the ideas holding what is looked for, ringed on the map
    val found = remember(query, version, searching, doc) { if (searching) matches().map { it.id }.toSet() else emptySet() }
    val ground = if (c.dark) Color(0xFF14152B) else Color(0xFFF6F7FB)
    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize().background(ground)) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { view = it }
                    .pointerInput(id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            glide?.cancel()
                            val lay = current
                            val start = (down.position - pan) / scale
                            val badge = lay.badgeAt(start.x, start.y)
                            val hit = if (badge == null) lay.hit(start.x, start.y, 4f * d) else null
                            // the centre doesn't move: dragging it looks around, as the paper does
                            val moving = hit?.takeIf { it !== lay.doc.root }
                            var dragged = false
                            var pinched = false
                            var travel = 0f
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.count { it.pressed }
                                if (pressed >= 2) {
                                    pinched = true
                                    if (target != null) target = null
                                    val zoom = event.calculateZoom()
                                    val move = event.calculatePan()
                                    val centre = event.calculateCentroid()
                                    val next = (scale * zoom).coerceIn(0.2f, 3f)
                                    pan = (pan - centre) * (next / scale) + centre + move
                                    scale = next
                                    event.changes.forEach { it.consume() }
                                } else if (!pinched) {
                                    val ch = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                                    val delta = ch.positionChange()
                                    travel += delta.getDistance()
                                    if (!dragged && travel > viewConfiguration.touchSlop) {
                                        dragged = true
                                        if (moving != null) {
                                            if (editing != null) finishEdit()
                                            keep()
                                            selected = moving.id
                                            palette = false
                                            feel(Feel.Press)
                                        }
                                    }
                                    if (dragged) {
                                        if (moving != null) {
                                            moving.dx += delta.x / scale / d
                                            moving.dy += delta.y / scale / d
                                            version++
                                            val at = (ch.position - pan) / scale
                                            val over = current.hit(at.x, at.y, 0f, skip = moving)?.takeIf { it !== moving.parent }
                                            if (over?.id != target) {
                                                target = over?.id
                                                if (over != null) feel(Feel.Tick)
                                            }
                                        } else {
                                            pan += delta
                                        }
                                        ch.consume()
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                            when {
                                moving != null && dragged -> {
                                    val into = target?.let { doc.root.find(it) }
                                    if (into != null && !pinched) {
                                        moving.parent?.children?.remove(moving)
                                        moving.dx = 0f
                                        moving.dy = 0f
                                        into.collapsed = false
                                        into.children.add(moving)
                                        feel(Feel.Confirm)
                                    }
                                    target = null
                                    changed()
                                }
                                dragged || pinched -> Unit
                                badge != null -> fold(badge)
                                hit != null -> {
                                    palette = false
                                    when {
                                        editing == hit.id -> Unit
                                        editing != null -> {
                                            finishEdit()
                                            selected = hit.id
                                        }
                                        // a second tap on the picked idea types in it
                                        selected == hit.id -> startEdit(hit, isNew = false)
                                        else -> {
                                            selected = hit.id
                                            feel(Feel.Tick)
                                        }
                                    }
                                }
                                else -> {
                                    if (editing != null) finishEdit()
                                    selected = null
                                    palette = false
                                }
                            }
                        }
                    },
            ) {
                dots(pan, scale, d, (if (c.dark) Color.White else Color(0xFF1E2233)).copy(alpha = if (c.dark) 0.07f else 0.09f))
                drawIntoCanvas { cv ->
                    val nc = cv.nativeCanvas
                    nc.save()
                    nc.translate(pan.x, pan.y)
                    nc.scale(scale, scale)
                    drawMind(nc, layout, c.dark, selected, target, found)
                    nc.restore()
                }
            }
        }

        if (outlining) {
            MindOutline(
                doc = doc, version = version, ground = ground, rootHint = rootHint, hint = hint,
                focusId = outlineFocus, onFocused = { id ->
                    if (outlineAt != id) keep()
                    outlineAt = id
                    outlineFocus = null
                },
                onText = { n, t ->
                    if (n.text != t) {
                        n.text = t
                        version++
                        save()
                    }
                },
                onNext = { n, parent -> outlineNew(n, parent) },
            )
        }

        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            val ink = rememberGlassInk(c.ink)
            // ---- top: back, the map's name, undo, redo, more
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassBackButton({
                    if (editing != null) finishEdit()
                    vm.closeNote()
                }, immediate = true)
                Row(Modifier.weight(1f).height(44.dp).then(ink.modifier).glassBar(edge = ink.edge, lift = ink.lift).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(LucideIcons.Network, null, tint = Candy.Grape, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        doc.root.text.ifBlank { fallback }, style = ClearType.Title.copy(fontSize = 15.sp), color = ink.color,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                val canUndo = history >= 0 && undo.isNotEmpty()
                val canRedo = history >= 0 && redo.isNotEmpty()
                GlassCircle(onClick = { step(undo, redo) }, modifier = Modifier.alpha(if (canUndo) 1f else 0.4f), size = 44.dp, label = stringResource(R.string.common_undo)) {
                    GlassIcon(LucideIcons.Undo2, null, Modifier.size(19.dp))
                }
                GlassCircle(onClick = { step(redo, undo) }, modifier = Modifier.alpha(if (canRedo) 1f else 0.4f), size = 44.dp, label = stringResource(R.string.common_redo)) {
                    GlassIcon(LucideIcons.Redo2, null, Modifier.size(19.dp))
                }
                GlassCircle(
                    onClick = { menuAt = moreAnchor },
                    modifier = Modifier.onGloballyPositioned { moreAnchor = it.boundsInWindow() },
                    size = 44.dp,
                    label = stringResource(R.string.common_more_options),
                ) {
                    GlassIcon(LucideIcons.Ellipsis, null, Modifier.size(19.dp))
                }
            }

            // ---- looking for words in the map (#156)
            if (searching) {
                val hits = remember(query, version, doc) { matches() }
                val searchFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }
                Row(
                    Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 66.dp, start = 14.dp, end = 14.dp).fillMaxWidth().height(48.dp)
                        .then(ink.modifier).glassBar(edge = ink.edge, lift = ink.lift).padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(LucideIcons.Search, null, tint = ink.color.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text(stringResource(R.string.mindmap_search_hint), style = ClearType.Body, color = ink.color.copy(alpha = 0.62f), maxLines = 1)
                        androidx.compose.foundation.text.BasicTextField(
                            value = query,
                            onValueChange = {
                                query = it
                                foundAt = 0
                                matches().firstOrNull()?.let { n -> reveal(n) }
                            },
                            singleLine = true,
                            textStyle = ClearType.Body.copy(color = ink.color),
                            cursorBrush = SolidColor(Candy.Sky),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { keyboard?.hide() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                        )
                    }
                    if (query.isNotBlank()) {
                        Text(
                            if (hits.isEmpty()) stringResource(R.string.mindmap_no_matches) else stringResource(R.string.mindmap_matches, foundAt.coerceAtMost(hits.size - 1) + 1, hits.size),
                            style = ClearType.Label, color = ink.color.copy(alpha = 0.68f), maxLines = 1, modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                    val step = { by: Int ->
                        if (hits.isNotEmpty()) {
                            foundAt = ((foundAt + by) % hits.size + hits.size) % hits.size
                            reveal(hits[foundAt])
                        }
                    }
                    BarGlyphButton({ step(-1) }, stringResource(R.string.viewer_find_previous), size = 44.dp, enabled = hits.size > 1) {
                        Icon(LucideIcons.ChevronUp, null, tint = ink.color, modifier = Modifier.size(19.dp))
                    }
                    BarGlyphButton({ step(1) }, stringResource(R.string.viewer_find_next), size = 44.dp, enabled = hits.size > 1) {
                        Icon(LucideIcons.ChevronDown, null, tint = ink.color, modifier = Modifier.size(19.dp))
                    }
                    BarGlyphButton({
                        searching = false
                        query = ""
                        keyboard?.hide()
                    }, stringResource(R.string.mindmap_search_close), size = 44.dp) {
                        Icon(LucideIcons.X, null, tint = ink.color, modifier = Modifier.size(19.dp))
                    }
                }
            }

            // ---- the outline's own keys, over the keyboard (#156)
            if (outlining) {
                val rows = remember(doc, version) { outlineRows(doc) }
                val at = rows.firstOrNull { it.node.id == outlineAt }
                Row(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 10.dp)
                        .then(ink.modifier).glassBar(edge = ink.edge, lift = ink.lift).padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompositionLocalProvider(LocalGlassInk provides ink.color) {
                        if (at != null && at.parent != null) {
                            MapTool(LucideIcons.IndentLess, stringResource(R.string.mindmap_outdent)) { outdent(at.node, at.parent, at.grand) }
                            MapTool(LucideIcons.IndentMore, stringResource(R.string.mindmap_indent)) { indent(at.node, at.parent) }
                        }
                        if (at != null) MapTool(LucideIcons.Plus, stringResource(R.string.mindmap_sibling)) { outlineNew(at.node, at.parent) }
                        if (at != null && at.parent != null) MapTool(LucideIcons.Trash2, stringResource(R.string.common_delete), tint = Candy.Tomato) {
                            delete(at.node)
                            outlineAt = null
                        }
                        MapTool(LucideIcons.Network, stringResource(R.string.mindmap_map_view)) {
                            outlining = false
                            keyboard?.hide()
                            scope.launch {
                                delay(30)
                                fit()
                            }
                        }
                    }
                }
            }

            // ---- bottom: what can be done with the picked idea, or typing in it
            if (!outlining) Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val typing = editing?.let { doc.root.find(it) }
                when {
                    typing != null -> {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 58.dp).then(ink.modifier).glassBar(RoundedCornerShape29, edge = ink.edge, lift = ink.lift).padding(start = 18.dp, end = 7.dp, top = 7.dp, bottom = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                // Each idea is typed into a field of its own, with its own text and its own
                                // keyboard session: going on to the next idea starts the keyboard afresh, so
                                // nothing typed into the last one carries over into this one.
                                androidx.compose.runtime.key(typing.id) {
                                    val ideaId = typing.id
                                    val words = remember {
                                        androidx.compose.foundation.text.input.TextFieldState(typing.text, TextRange(0, typing.text.length))
                                    }
                                    LaunchedEffect(words) {
                                        androidx.compose.runtime.snapshotFlow { words.text.toString() }.collect { t ->
                                            val n = doc.root.find(ideaId) ?: return@collect
                                            val clean = t.replace('\n', ' ')
                                            if (n.text != clean) {
                                                n.text = clean
                                                version++
                                                save()
                                            }
                                        }
                                    }
                                    if (words.text.isEmpty()) {
                                        Text(if (typing === doc.root) rootHint else hint, style = ClearType.Body, color = ink.color.copy(alpha = 0.45f))
                                    }
                                    BasicTextField(
                                        state = words,
                                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                                        textStyle = ClearType.Body.copy(color = ink.color),
                                        cursorBrush = SolidColor(Candy.Sky),
                                        lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                                        onKeyboardAction = { finishEdit() },
                                    )
                                }
                            }
                            // straight on to the next idea: under this one, or beside it
                            GlassCircle(onClick = {
                                val at = typing.id
                                finishEdit()
                                doc.root.find(at)?.let { addChild(it) }
                            }, size = 44.dp, label = stringResource(R.string.mindmap_child)) {
                                GlassIcon(LucideIcons.Plus, null, Modifier.size(19.dp))
                            }
                            if (typing !== doc.root) {
                                GlassCircle(onClick = {
                                    val at = typing.id
                                    finishEdit()
                                    doc.root.find(at)?.let { addSibling(it) }
                                }, size = 44.dp, label = stringResource(R.string.mindmap_sibling)) {
                                    GlassIcon(LucideIcons.RowsAdd, null, Modifier.size(19.dp))
                                }
                            }
                            GlassCircle(onClick = { finishEdit() }, size = 44.dp, tint = Candy.Sky, label = stringResource(R.string.common_done)) {
                                GlassIcon(LucideIcons.Check, null, Modifier.size(20.dp))
                            }
                        }
                    }
                    chosen != null -> {
                        AnimatedVisibility(palette, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // the words: weight, slant, lines, size and alignment (#156)
                                val boldNow = chosen.bold ?: (chosen.depth <= 1)
                                Row(
                                    Modifier.then(ink.modifier).glassBar(edge = ink.edge, lift = ink.lift).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CompositionLocalProvider(LocalGlassInk provides ink.color) {
                                        StyleKey(LucideIcons.Bold, stringResource(R.string.mindmap_bold), boldNow) { style(chosen) { bold = !boldNow } }
                                        StyleKey(LucideIcons.Italic, stringResource(R.string.mindmap_italic), chosen.italic) { style(chosen) { italic = !italic } }
                                        StyleKey(LucideIcons.Underline, stringResource(R.string.mindmap_underline), chosen.underline) { style(chosen) { underline = !underline } }
                                        StyleKey(LucideIcons.Strikethrough, stringResource(R.string.mindmap_strike), chosen.strike) { style(chosen) { strike = !strike } }
                                        StyleKey(LucideIcons.AArrowDown, stringResource(R.string.mindmap_smaller), false, enabled = chosen.size > SizeSteps.first()) {
                                            style(chosen) { size = SizeSteps.lastOrNull { it < size - 0.01f } ?: size }
                                        }
                                        StyleKey(LucideIcons.AArrowUp, stringResource(R.string.mindmap_larger), false, enabled = chosen.size < SizeSteps.last()) {
                                            style(chosen) { size = SizeSteps.firstOrNull { it > size + 0.01f } ?: size }
                                        }
                                        StyleKey(
                                            when (chosen.align) {
                                                -1 -> LucideIcons.AlignLeft
                                                1 -> LucideIcons.AlignRight
                                                else -> LucideIcons.AlignCenter
                                            },
                                            stringResource(R.string.mindmap_align), chosen.align != 0,
                                        ) { style(chosen) { align = if (align == 0) -1 else if (align == -1) 1 else 0 } }
                                    }
                                }
                                // the words' colour, then a highlight behind them
                                Row(
                                    Modifier.then(ink.modifier).glassBar(edge = ink.edge, lift = ink.lift).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Swatch(SolidColor(ink.color), chosen.ink == 0, ink.color, stringResource(R.string.mindmap_text_colour)) { style(chosen) { this.ink = 0 } }
                                    TextInks.forEach { col ->
                                        Swatch(SolidColor(col), chosen.ink == col.toArgb(), ink.color, null) { style(chosen) { this.ink = col.toArgb() } }
                                    }
                                    Box(Modifier.width(1.dp).height(26.dp).background(ink.color.copy(alpha = 0.2f)))
                                    Swatch(SolidColor(Color.Transparent), chosen.mark == 0, ink.color, stringResource(R.string.mindmap_highlight)) { style(chosen) { mark = 0 } }
                                    Highlights.forEach { col ->
                                        Swatch(SolidColor(col), chosen.mark == col.toArgb(), ink.color, null) { style(chosen) { mark = col.toArgb() } }
                                    }
                                }
                                // the branch's colour (not the centre's)
                                if (chosen !== doc.root) Row(
                                    Modifier.then(ink.modifier).glassBar(edge = ink.edge, lift = ink.lift).padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Swatch(Brush.sweepGradient(BranchColors + BranchColors.first()), chosen.color < 0, ink.color, stringResource(R.string.mindmap_branch_colour)) { colour(chosen, -1) }
                                    BranchColors.forEachIndexed { i, col ->
                                        Swatch(SolidColor(col), chosen.color == i, ink.color, null) { colour(chosen, i) }
                                    }
                                }
                            }
                        }
                        Row(
                            Modifier.then(ink.modifier).glassBar(edge = ink.edge, lift = ink.lift).padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CompositionLocalProvider(LocalGlassInk provides ink.color) {
                                MapTool(LucideIcons.Plus, stringResource(R.string.mindmap_child)) { addChild(chosen) }
                                if (chosen !== doc.root) MapTool(LucideIcons.RowsAdd, stringResource(R.string.mindmap_sibling)) { addSibling(chosen) }
                                MapTool(LucideIcons.Pencil, stringResource(R.string.mindmap_edit)) { startEdit(chosen, isNew = false) }
                                MapTool(LucideIcons.Palette, stringResource(R.string.mindmap_style), lit = palette) { palette = !palette }
                                if (chosen.children.isNotEmpty()) {
                                    MapTool(if (chosen.collapsed) LucideIcons.Eye else LucideIcons.EyeOff, stringResource(if (chosen.collapsed) R.string.mindmap_unfold else R.string.mindmap_fold)) { fold(chosen) }
                                }
                                if (chosen !== doc.root) MapTool(LucideIcons.Trash2, stringResource(R.string.common_delete), tint = Candy.Tomato) { delete(chosen) }
                            }
                        }
                    }
                    else -> {
                        Text(
                            stringResource(R.string.mindmap_hint), style = ClearType.Caption, color = c.inkSoft, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            GlassPill(onClick = { addChild(doc.root) }, tint = Candy.Sky, height = 48.dp, label = stringResource(R.string.mindmap_add_idea)) {
                                Icon(LucideIcons.Plus, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.mindmap_add_idea), style = ClearType.BodyStrong, color = Color.White)
                            }
                            GlassCircle(onClick = { fit() }, size = 48.dp, label = stringResource(R.string.mindmap_fit)) {
                                GlassIcon(LucideIcons.Maximize, null, Modifier.size(19.dp))
                            }
                        }
                    }
                }
            }
        }

        menuAt?.let { anchor ->
            GlassMenu(
                anchor = anchor,
                onDismiss = { menuAt = null },
                items = listOf(
                    GlassMenuItem(stringResource(R.string.mindmap_fit), LucideIcons.Maximize) { fit() },
                    GlassMenuItem(stringResource(R.string.mindmap_search), LucideIcons.Search) {
                        if (editing != null) finishEdit()
                        outlining = false
                        query = ""
                        foundAt = 0
                        searching = true
                    },
                    GlassMenuItem(stringResource(R.string.mindmap_outline), LucideIcons.OutlineList) {
                        if (editing != null) finishEdit()
                        searching = false
                        selected = null
                        palette = false
                        outlining = true
                    },
                    GlassMenuItem(stringResource(R.string.mindmap_layout), LucideIcons.MapLayout) { layoutMenu = true },
                    GlassMenuItem(stringResource(R.string.mindmap_direction), LucideIcons.TextDirection) { directionMenu = true },
                    GlassMenuItem(stringResource(R.string.mindmap_save_image), LucideIcons.ImagePlus) { export("png") },
                    GlassMenuItem(stringResource(R.string.mindmap_save_pdf), LucideIcons.FilePdf) { export("pdf") },
                    GlassMenuItem(stringResource(R.string.mindmap_save_opml), LucideIcons.FileOutput) { export("opml") },
                ),
            )
        }
        // the layouts and directions to choose from, the one in use ticked
        if (layoutMenu) GlassMenu(
            anchor = moreAnchor,
            onDismiss = { layoutMenu = false },
            items = MapLayout.entries.map { l ->
                GlassMenuItem(stringResource(layoutName(l)), if (doc.layout == l) LucideIcons.Check else null) { pickLayout(l) }
            },
        )
        if (directionMenu) GlassMenu(
            anchor = moreAnchor,
            onDismiss = { directionMenu = false },
            items = MapDirection.entries.map { dir ->
                GlassMenuItem(stringResource(directionName(dir)), if (doc.direction == dir) LucideIcons.Check else null) { pickDirection(dir) }
            },
        )
    }
}

private val RoundedCornerShape29 = androidx.compose.foundation.shape.RoundedCornerShape(29.dp)

/** One of the picked idea's tools: an icon over its name. */
@Composable
private fun MapTool(icon: ImageVector, label: String, tint: Color = LocalGlassInk.current, lit: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.width(58.dp).height(58.dp).pressable(onClick, pressedScale = 0.88f).padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(if (lit) Candy.Sky else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (lit) Color.White else tint, modifier = Modifier.size(19.dp))
        }
        Text(label, style = ClearType.Label.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium), color = tint.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A colour to pick: a disc, ringed when it is the one in use. */
@Composable
private fun Swatch(fill: Brush, on: Boolean, ink: Color, label: String?, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .border(if (on) 2.dp else 0.dp, if (on) ink else Color.Transparent, CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .background(fill)
            .pressable(onClick, pressedScale = 0.85f)
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
    )
}

// ---- a written note, seen as a map -----------------------------------------------------------------

/**
 * A text note seen as a mind map: its title in the middle, its outline branching out. Drag to look
 * around and pinch to zoom; save it as a picture or a PDF, or make it a mind-map note to work on.
 */
@Composable
fun MindMapScreen(vm: AppViewModel, title: String, body: String, onClose: () -> Unit) {
    val fallback = stringResource(R.string.mindmap_title)
    val doc = remember(title, body) { MindDoc(parseOutline(title.ifBlank { fallback }, body)) }
    MindMapPreview(vm, doc, title, onClose = onClose)
}

/**
 * A mind map to look at: a note's outline drawn as a map, or an OPML file opened from Files or
 * another app. Drag to look around and pinch to zoom; save it as a picture or a PDF, or make it a
 * mind-map note to work on.
 */
@Composable
internal fun MindMapPreview(
    vm: AppViewModel,
    doc: MindDoc,
    title: String,
    /** Make it a mind-map note; by default the preview closes and the new note opens. */
    onMake: (() -> Unit)? = null,
    onClose: () -> Unit,
) {
    val c = clear
    val d = LocalDensity.current.density
    val context = LocalContext.current
    remember(context) { MapType.load(context) }
    val scope = rememberCoroutineScope()
    val fallback = stringResource(R.string.mindmap_title)
    val rootHint = stringResource(R.string.mindmap_root_hint)
    val hint = stringResource(R.string.mindmap_node_hint)
    val layout = remember(doc, d) { layoutMind(doc, d, rootHint, hint) }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var fitted by remember(layout) { mutableStateOf(false) }
    val empty = doc.root.children.isEmpty()
    BackHandler(onBack = onClose)

    fun save(kind: String) {
        val copy = mindFromJson(doc.toJson()) ?: return
        val name = safeName(title, fallback)
        val dark = c.dark
        scope.launch {
            val file = withContext(Dispatchers.Default) {
                runCatching { File(context.cacheDir, "map-${System.nanoTime()}.$kind").also { writeMind(copy, d, dark, kind, it, rootHint, hint) } }.getOrNull()
            }
            if (file == null) vm.tell(context.getString(R.string.common_file_unreadable))
            else vm.deliverFile(file, "$name.$kind", if (kind == "pdf") "application/pdf" else "image/png")
        }
    }

    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize().background(if (c.dark) Color(0xFF14152B) else Color(0xFFF6F7FB))) {
        Box(
            Modifier.fillMaxSize().layerBackdrop(backdrop).onSizeChanged { size ->
                if (!fitted && size.width > 0) {
                    val b = layout.bounds
                    val s = min(size.width * 0.9f / b.width().coerceAtLeast(1f), size.height * 0.7f / b.height().coerceAtLeast(1f)).coerceIn(0.25f, 1.2f)
                    scale = s
                    pan = Offset(size.width / 2f - b.centerX() * s, size.height / 2f - b.centerY() * s)
                    fitted = true
                }
            },
        ) {
            if (empty) {
                Text(
                    stringResource(R.string.mindmap_empty), style = ClearType.Body, color = c.inkSoft, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(40.dp),
                )
            } else {
                Canvas(
                    Modifier.fillMaxSize().pointerInput(layout) {
                        detectTransformGestures { centroid, move, zoom, _ ->
                            val next = (scale * zoom).coerceIn(0.2f, 3f)
                            pan = (pan - centroid) * (next / scale) + centroid + move
                            scale = next
                        }
                    },
                ) {
                    dots(pan, scale, d, (if (c.dark) Color.White else Color(0xFF1E2233)).copy(alpha = if (c.dark) 0.07f else 0.09f))
                    drawIntoCanvas { cv ->
                        val nc = cv.nativeCanvas
                        nc.save()
                        nc.translate(pan.x, pan.y)
                        nc.scale(scale, scale)
                        drawMind(nc, layout, c.dark)
                        nc.restore()
                    }
                }
            }
        }
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlassBackButton(onClose, immediate = true)
                Text(
                    title.ifBlank { fallback }, style = ClearType.Title, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                )
                if (!empty) {
                    GlassCircle(onClick = { save("png") }, label = stringResource(R.string.mindmap_save_image)) {
                        GlassIcon(LucideIcons.ImagePlus, null, Modifier.size(20.dp))
                    }
                    GlassCircle(onClick = { save("pdf") }, label = stringResource(R.string.mindmap_save_pdf)) {
                        GlassIcon(LucideIcons.FilePdf, null, Modifier.size(20.dp))
                    }
                }
            }
            if (!empty) {
                GlassPill(
                    onClick = {
                        if (onMake != null) {
                            onMake()
                        } else {
                            onClose()
                            vm.newMindMap(doc)
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 18.dp),
                    tint = Candy.Grape,
                    height = 50.dp,
                    label = stringResource(R.string.mindmap_make),
                ) {
                    Icon(LucideIcons.Network, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.mindmap_make), style = ClearType.BodyStrong, color = Color.White)
                }
            }
        }
    }
}


/** The sizes an idea's words step through (#156): a step on its depth's own. */
private val SizeSteps = listOf(0.8f, 1f, 1.25f, 1.5f, 1.85f)

/** Colours for an idea's words, readable on the cards in either theme. */
private val TextInks = listOf(Candy.Tomato, Candy.Tangerine, Color(0xFF2E9E57), Candy.Sky, Candy.Grape, Candy.Bubblegum)

/**
 * Highlights behind an idea's words: solid pastels, as a highlighter's, so they read the same on a
 * white card and on a first idea's coloured one (see-through, they went muddy over its colour).
 */
private val Highlights = listOf(Color(0xFFFFF09A), Color(0xFFB9F2DF), Color(0xFFC6E2FF), Color(0xFFFFD0E4))

@androidx.annotation.StringRes
private fun layoutName(l: MapLayout): Int = when (l) {
    MapLayout.Balanced -> R.string.mindmap_layout_balanced
    MapLayout.Logic -> R.string.mindmap_layout_logic
    MapLayout.Org -> R.string.mindmap_layout_org
    MapLayout.Tree -> R.string.mindmap_layout_tree
    MapLayout.Timeline -> R.string.mindmap_layout_timeline
    MapLayout.Radial -> R.string.mindmap_layout_radial
}

@androidx.annotation.StringRes
private fun directionName(dir: MapDirection): Int = when (dir) {
    MapDirection.Auto -> R.string.mindmap_dir_auto
    MapDirection.Ltr -> R.string.mindmap_dir_ltr
    MapDirection.Rtl -> R.string.mindmap_dir_rtl
}

/** A style key in the Style panel: its glyph, lit while the style is on. */
@Composable
private fun StyleKey(icon: ImageVector, label: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val tint = LocalGlassInk.current
    Box(
        Modifier.size(48.dp).pressable(onClick, enabled = enabled, pressedScale = 0.88f).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(if (on) Candy.Sky else Color.Transparent))
        Icon(icon, null, tint = if (on) Color.White else tint.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(20.dp))
    }
}

/** A row of the outline: an idea, how deep it sits, the idea it is under and that one's own. */
internal class OutlineRow(val node: MindNode, val depth: Int, val parent: MindNode?, val grand: MindNode?)

/** Every idea of [doc] in the map's order, folded branches too: the outline's rows. */
internal fun outlineRows(doc: MindDoc): List<OutlineRow> {
    val out = ArrayList<OutlineRow>()
    fun walk(n: MindNode, depth: Int, parent: MindNode?, grand: MindNode?) {
        out.add(OutlineRow(n, depth, parent, grand))
        n.children.forEach { walk(it, depth + 1, n, parent) }
    }
    walk(doc.root, 0, null, null)
    return out
}

/**
 * The map as an outline (#156): every idea a line, stepped in under the one it belongs to, typed into
 * where it is. Writing here changes the map, and the map's changes are here. A new line comes with the
 * keyboard's Next; the keys over the keyboard step a line in or out. A right-to-left map's outline
 * runs from the right.
 */
@Composable
private fun MindOutline(
    doc: MindDoc,
    version: Int,
    ground: Color,
    rootHint: String,
    hint: String,
    focusId: String?,
    onFocused: (String) -> Unit,
    onText: (MindNode, String) -> Unit,
    onNext: (MindNode, MindNode?) -> Unit,
) {
    val c = clear
    val rows = remember(doc, version) { outlineRows(doc) }
    val focusers = remember(doc) { HashMap<String, FocusRequester>() }
    val direction = if (doc.rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.platform.LocalLayoutDirection.current
    LaunchedEffect(focusId, rows) {
        val id = focusId ?: return@LaunchedEffect
        delay(30)
        runCatching { focusers[id]?.requestFocus() }
    }
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides direction) {
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxSize().background(ground).statusBarsPadding().imePadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 74.dp, bottom = 120.dp),
        ) {
            items(rows.size, key = { rows[it].node.id }) { i ->
                val row = rows[i]
                val n = row.node
                Row(
                    Modifier.fillMaxWidth().padding(start = (18 + row.depth * 22).dp, end = 18.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        Modifier.padding(top = if (row.depth == 0) 11.dp else 8.dp).size(if (row.depth == 0) 10.dp else 7.dp).clip(CircleShape)
                            .background(if (row.depth == 0) c.ink else branchColor(n.tint)),
                    )
                    Spacer(Modifier.width(12.dp))
                    androidx.compose.runtime.key(n.id) {
                        val focuser = focusers.getOrPut(n.id) { FocusRequester() }
                        val words = remember { androidx.compose.foundation.text.input.TextFieldState(n.text) }
                        LaunchedEffect(words) {
                            androidx.compose.runtime.snapshotFlow { words.text.toString() }.collect { t -> onText(n, t.replace('\n', ' ')) }
                        }
                        Box(Modifier.weight(1f)) {
                            val type = if (row.depth == 0) ClearType.Title.copy(fontSize = 20.sp, color = c.ink) else ClearType.Body.copy(fontSize = 16.sp, color = c.ink)
                            if (words.text.isEmpty()) Text(if (row.depth == 0) rootHint else hint, style = type, color = c.ink.copy(alpha = 0.45f))
                            BasicTextField(
                                state = words,
                                modifier = Modifier.fillMaxWidth().focusRequester(focuser)
                                    .onFocusChanged { if (it.isFocused) onFocused(n.id) },
                                textStyle = type,
                                cursorBrush = SolidColor(Candy.Sky),
                                lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                                onKeyboardAction = { onNext(n, row.parent) },
                            )
                        }
                    }
                }
            }
        }
    }
}
