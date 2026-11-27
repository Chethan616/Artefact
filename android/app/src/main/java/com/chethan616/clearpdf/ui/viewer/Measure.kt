package com.chethan616.clearpdf.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.OpenDocument
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearType

/**
 * What the Measure tool takes (#159): a distance (two taps), a perimeter or an area (taps, then
 * Done), or the drawing's scale from two points a known distance apart.
 */
enum class MeasureMode { Distance, Perimeter, Area, Scale }

/**
 * The points being measured, on one page, and the engine's live reading of them. The engine does
 * the measuring (distance, perimeter, area at the page's scale; see `measure_*`); this only collects
 * the taps, shows the shape and its reading, and adds the measurement when it is complete.
 */
class MeasureState {
    var page by mutableStateOf(-1)
    val points = mutableStateListOf<Offset>()
    var reading by mutableStateOf<String?>(null)
    /** Two points for a scale, waiting for their real distance. */
    var calibrating by mutableStateOf(false)

    fun clear() {
        page = -1
        points.clear()
        reading = null
        calibrating = false
    }
}

private fun MeasureMode.kind() = when (this) {
    MeasureMode.Perimeter -> "perimeter"
    MeasureMode.Area -> "area"
    else -> "distance"
}

/** Adds what [m] has collected as a saved measurement, if it is complete; clears it either way. */
internal fun AppViewModel.finishMeasure(doc: OpenDocument, mode: MeasureMode, m: MeasureState) {
    val pts = m.points.map { listOf(it.x.toDouble(), it.y.toDouble()) }
    val page = m.page
    val enough = when (mode) {
        MeasureMode.Distance -> pts.size >= 2
        MeasureMode.Perimeter -> pts.size >= 2
        MeasureMode.Area -> pts.size >= 3
        MeasureMode.Scale -> false
    }
    m.clear()
    if (!enough || page < 0) return
    runTool(doc, "measure_${mode.kind()}", mapOf("page" to page + 1, "points" to pts))
}

/** Reads the live measurement of [m]'s points (nothing is added to the document). */
private fun AppViewModel.readMeasure(doc: OpenDocument, mode: MeasureMode, m: MeasureState) {
    if (m.points.size < 2 || m.page < 0 || mode == MeasureMode.Scale) {
        m.reading = null
        return
    }
    val pts = m.points.map { listOf(it.x.toDouble(), it.y.toDouble()) }
    runTool(doc, "measure_info", mapOf("page" to m.page + 1, "points" to pts, "type" to mode.kind())) { r ->
        // "m^2" as the eye reads it
        m.reading = r.optJSONObject("reading")?.optString("label")?.takeIf { it.isNotBlank() }?.replace("^2", "²")?.replace("^3", "³")
    }
}

/**
 * The Measure tool over the pages: a tap adds a point (on the page the first one was on), the shape
 * is drawn as it grows with its reading beside the last point, and a distance is added as soon as it
 * has its two points. Two fingers still scroll and zoom.
 */
@Composable
fun MeasureLayer(vm: AppViewModel, doc: OpenDocument) {
    val ctl = doc.controller
    val m = ctl.measure
    val mode = ctl.measureMode
    LaunchedEffect(mode) { m.clear() }
    // leaving the tool adds a perimeter or area still being drawn, if it can be one
    DisposableEffect(doc) {
        doc.commitTool = { vm.finishMeasure(doc, ctl.measureMode, ctl.measure) }
        onDispose { }
    }
    Box(
        Modifier.fillMaxSize().toolGestures(
            doc,
            onDown = {},
            onMove = {},
            onUp = { p, moved ->
                if (moved) return@toolGestures
                val at = doc.viewer.screenToPage(p) ?: return@toolGestures
                if (m.page >= 0 && at.page != m.page) return@toolGestures
                m.page = at.page
                m.points.add(Offset(at.x, at.y))
                // the mode now, not the one this gesture handler was made with (it isn't remade on a switch)
                val now = ctl.measureMode
                when {
                    now == MeasureMode.Distance && m.points.size >= 2 -> vm.finishMeasure(doc, now, m)
                    now == MeasureMode.Scale && m.points.size >= 2 -> m.calibrating = true
                    else -> vm.readMeasure(doc, now, m)
                }
            },
            onCancel = {},
        ),
    )
    // the shape so far, and its reading
    val accent = if (mode == MeasureMode.Scale) Candy.Grape else Candy.Tomato
    Canvas(Modifier.fillMaxSize()) {
        if (m.page < 0 || m.points.isEmpty()) return@Canvas
        val v = doc.viewer
        val pts = m.points.map { v.pageToScreen(m.page, it.x, it.y) }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (q in pts.drop(1)) lineTo(q.x, q.y)
            if (mode == MeasureMode.Area && pts.size >= 3) close()
        }
        if (mode == MeasureMode.Area && pts.size >= 3) drawPath(path, accent.copy(alpha = 0.16f))
        drawPath(path, accent, style = Stroke(2.dp.toPx(), pathEffect = if (mode == MeasureMode.Scale) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null))
        for (q in pts) {
            drawCircle(Color.White, 6.dp.toPx(), q)
            drawCircle(accent, 4.dp.toPx(), q)
        }
    }
    m.reading?.let { label ->
        val last = m.points.lastOrNull()
        if (last != null && m.page >= 0) {
            val at = doc.viewer.pageToScreen(m.page, last.x, last.y)
            val density = androidx.compose.ui.platform.LocalDensity.current
            Box(
                Modifier.padding(start = with(density) { (at.x + 12f).toDp() }, top = with(density) { (at.y - 40f).coerceAtLeast(0f).toDp() })
                    .then(com.chethan616.clearpdf.ui.glass.washPanel())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) { Text(label, style = ClearType.BodyStrong, color = com.chethan616.clearpdf.ui.theme.clear.ink) }
        }
    }
    if (m.calibrating) ScaleDialog(vm, doc, m)
}

/** Two points a known distance apart: how far they are, in which unit, sets the page's scale. */
@Composable
private fun ScaleDialog(vm: AppViewModel, doc: OpenDocument, m: MeasureState) {
    var distance by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("m") }
    val units = listOf("mm", "cm", "m", "in", "ft")
    val value = distance.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    val done = stringResource(R.string.measure_scale_set)
    com.chethan616.clearpdf.ui.glass.GlassDialog(
        onDismiss = { m.clear() },
        title = stringResource(R.string.measure_scale_title),
        confirm = stringResource(R.string.common_done),
        confirmEnabled = value != null,
        onConfirm = {
            val pts = m.points.take(2).map { listOf(it.x.toDouble(), it.y.toDouble()) }
            val page = m.page
            m.clear()
            if (value != null && page >= 0) {
                vm.runTool(doc, "measure_scale", mapOf("page" to page + 1, "points" to pts, "distance" to value, "unit" to unit)) { vm.tell(done) }
            }
        },
        dismiss = stringResource(R.string.common_cancel),
        onDismissButton = { m.clear() },
        icon = LucideIcons.Ruler,
    ) {
        Text(stringResource(R.string.measure_scale_body), style = ClearType.Body, color = com.chethan616.clearpdf.ui.theme.clear.inkSoft)
        Spacer(Modifier.padding(top = 12.dp))
        com.chethan616.clearpdf.ui.glass.GlassTextField(
            value = distance, onValueChange = { distance = it },
            placeholder = stringResource(R.string.measure_scale_distance),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            for (u in units) {
                com.chethan616.clearpdf.ui.glass.GlassChip(
                    onClick = { unit = u }, height = 40.dp,
                    fill = { if (u == unit) Candy.Sky.copy(alpha = 0.85f) else Color.Transparent },
                    ink = if (u == unit) Color.White else com.chethan616.clearpdf.ui.theme.clear.ink,
                ) { Text(u, style = ClearType.Label, color = if (u == unit) Color.White else com.chethan616.clearpdf.ui.theme.clear.ink) }
                Spacer(Modifier.width(6.dp))
            }
        }
    }
}
