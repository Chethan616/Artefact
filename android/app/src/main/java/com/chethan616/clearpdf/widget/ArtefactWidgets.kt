package com.chethan616.clearpdf.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.MainActivity
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.notes.Note
import com.chethan616.clearpdf.notes.NoteFile
import com.chethan616.clearpdf.notes.NoteKind
import com.chethan616.clearpdf.notes.Notes
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.ThemeChoice
import com.chethan616.clearpdf.ui.glass.Feel
import com.chethan616.clearpdf.ui.glass.GlassSettings
import com.chethan616.clearpdf.ui.glass.Haptics
import com.chethan616.clearpdf.ui.home.CardShape
import com.chethan616.clearpdf.ui.home.cardPaint
import com.chethan616.clearpdf.ui.home.ListCard
import com.chethan616.clearpdf.ui.home.LocalHomeVm
import com.chethan616.clearpdf.ui.home.NoteCard
import com.chethan616.clearpdf.ui.home.RecentCard
import com.chethan616.clearpdf.ui.theme.ClearTheme
import com.chethan616.clearpdf.ui.theme.clear
import java.io.File

/** What a widget tap asks the app to open: a note by its id. */
const val EXTRA_OPEN_NOTE = "com.chethan616.clearpdf.OPEN_NOTE"

/** What a widget tap asks the app to open: a recent document by its uri. */
const val EXTRA_OPEN_RECENT = "com.chethan616.clearpdf.OPEN_RECENT"

/** A widget tap that asks for Files (Recent). */
const val EXTRA_OPEN_FILES = "com.chethan616.clearpdf.OPEN_FILES"

/**
 * The home screen widgets. Each is the desk's very card: the app's own `ListCard`, `NoteCard` and
 * `RecentCard` composables, liquid glass and all, under the user's glass settings, rendered off
 * screen ([ComposeSnapshot]) at the widget's own size, with tap targets laid exactly over the list's
 * pills. (A home screen can only show a widget's pictures and send its taps; the glass can't run
 * live there, so a tap answers with a press light, the app's haptic and the card redrawn.)
 */
object Widgets {
    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<() -> Unit>()
    private var busy = false

    /** Runs the renders one after another on the main thread (one virtual display at a time). */
    internal fun enqueue(job: (next: () -> Unit) -> Unit) {
        main.post {
            queue.addLast {
                job {
                    main.post {
                        busy = false
                        drain()
                    }
                }
            }
            drain()
        }
    }

    private fun drain() {
        if (busy) return
        val job = queue.removeFirstOrNull() ?: return
        busy = true
        job()
    }

    private val soon = Runnable { pendingCtx?.let { refreshAll(it) }; pendingCtx = null }
    @Volatile private var pendingCtx: Context? = null

    /**
     * Redraw the notes widgets once the notes have been still for a moment: drawing a widget card runs
     * on the main thread, so doing it on every tick stuttered the very tap that ticked. Any thread.
     */
    fun refreshSoon(ctx: Context) {
        val app = ctx.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        if (mgr.getAppWidgetIds(ComponentName(app, TodoWidget::class.java)).isEmpty() &&
            mgr.getAppWidgetIds(ComponentName(app, NoteWidget::class.java)).isEmpty()) return
        pendingCtx = app
        main.removeCallbacks(soon)
        main.postDelayed(soon, 1500L)
    }

    /** Redraw the notes widgets (the notes changed). Any thread. */
    fun refreshAll(ctx: Context, then: (() -> Unit)? = null) {
        val app = ctx.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        val todo = mgr.getAppWidgetIds(ComponentName(app, TodoWidget::class.java))
        val notes = mgr.getAppWidgetIds(ComponentName(app, NoteWidget::class.java))
        var left = todo.size + notes.size
        if (left == 0) {
            then?.invoke()
            return
        }
        val one = { if (--left == 0) then?.invoke() }
        todo.forEach { id -> TodoWidget.update(app, mgr, id, one) }
        notes.forEach { id -> NoteWidget.update(app, mgr, id, one) }
    }

    /** The widget's size in pixels (its current size on the home screen, portrait or landscape). */
    internal fun size(ctx: Context, options: Bundle): Pair<Int, Int> {
        val dm = ctx.resources.displayMetrics
        // Android 12+: the sizes the launcher really lays the widget out at (the min/max pair can be off)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val sizes = androidx.core.os.BundleCompat.getParcelableArrayList(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, android.util.SizeF::class.java)
            val portrait = ctx.resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val pick = sizes?.let { list -> if (portrait) list.minByOrNull { it.width } else list.maxByOrNull { it.width } }
            if (pick != null && pick.width > 0f && pick.height > 0f) {
                fun px(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, dm).toInt()
                return px(pick.width) to px(pick.height)
            }
        }
        val portrait = ctx.resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val wDp = options.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 160)
        val hDp = options.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 220)
        fun px(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.coerceAtLeast(40).toFloat(), dm).toInt()
        return px(wDp) to px(hDp)
    }

    internal fun activity(ctx: Context, requestKey: String, extras: Intent.() -> Unit): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java)
            .setData(Uri.parse("artefact://widget/$requestKey"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply(extras)
        return PendingIntent.getActivity(ctx, requestKey.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    internal fun open(ctx: Context, noteId: String?, requestKey: String): PendingIntent =
        activity(ctx, requestKey) { if (noteId != null) putExtra(EXTRA_OPEN_NOTE, noteId) }

    internal fun mediaFile(ctx: Context, name: String) = File(File(File(ctx.filesDir, "notes"), "media"), name)

    internal fun cornerPx(ctx: Context) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30f, ctx.resources.displayMetrics)

    internal fun toDp(ctx: Context, px: Int): Dp = (px / ctx.resources.displayMetrics.density).dp

    internal fun themeChoice(ctx: Context): ThemeChoice =
        runCatching { ThemeChoice.valueOf(ctx.getSharedPreferences("app", Context.MODE_PRIVATE).getString("theme", "System") ?: "System") }.getOrDefault(ThemeChoice.System)

    /** A desk card's width: the desk is two columns, 16 dp from the screen's edges and 12 dp apart. */
    internal fun deskWidth(ctx: Context): Dp = ((ctx.resources.configuration.screenWidthDp - 44) / 2f).coerceIn(120f, 240f).dp

    /** How far [SquareCard] widens a card at each try. */
    internal fun stepPx(ctx: Context) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6f, ctx.resources.displayMetrics).toInt().coerceAtLeast(1)

    /** Room to lay a card out in before it is cut to its own height: twice the widget's height. */
    internal fun canvasHeight(boxH: Int) = boxH * 2

    /**
     * The card as the desk lays it out, as wide as the widget and at least as tall, on what the desk
     * shows behind it (the wallpaper's gradient), in the app's theme; [placed] gets its bounds.
     */
    @Composable
    internal fun Desk(choice: ThemeChoice, placed: (android.graphics.Rect) -> Unit, content: @Composable () -> Unit) {
        ClearTheme(choice) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxWidth().wrapContentHeight().clip(CardShape).background(Brush.verticalGradient(clear.wall))
                        .onGloballyPositioned { placed(it.boundsInRoot().toRect()) },
                ) { content() }
            }
        }
    }

    internal fun androidx.compose.ui.geometry.Rect.toRect() = android.graphics.Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

    /** How the card sits in the widget (fit inside, centred, never enlarged): its scale and where its corner lands. */
    internal class Fit(val scale: Float, val dx: Float, val dy: Float)

    internal fun fit(boxW: Int, boxH: Int, cardW: Int, cardH: Int): Fit {
        val s = minOf(boxW / cardW.toFloat(), boxH / cardH.toFloat())
        // centred across, resting on the bottom: the card meets the label the launcher writes under it
        return Fit(s, (boxW - cardW * s) / 2f, boxH - cardH * s)
    }

    /** [card] drawn into a picture of the widget's own size where [fit] puts it (the image view shows it as is). */
    internal fun onBox(card: Bitmap, boxW: Int, boxH: Int): Bitmap {
        if (boxW <= 0 || boxH <= 0) return card
        val f = fit(boxW, boxH, card.width, card.height)
        val out = Bitmap.createBitmap(boxW, boxH, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(out).drawBitmap(
            card, null,
            android.graphics.RectF(f.dx, f.dy, f.dx + card.width * f.scale, f.dy + card.height * f.scale),
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG),
        )
        return out
    }

    /** Lays the whole-card tap target over the card itself (not the clear room around it). */
    internal fun placeCardTarget(views: RemoteViews, f: Fit, cardW: Int, cardH: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        views.setViewLayoutWidth(R.id.card_open, cardW * f.scale, TypedValue.COMPLEX_UNIT_PX)
        views.setViewLayoutHeight(R.id.card_open, cardH * f.scale, TypedValue.COMPLEX_UNIT_PX)
        views.setViewLayoutMargin(R.id.card_open, RemoteViews.MARGIN_LEFT, f.dx, TypedValue.COMPLEX_UNIT_PX)
        views.setViewLayoutMargin(R.id.card_open, RemoteViews.MARGIN_TOP, f.dy, TypedValue.COMPLEX_UNIT_PX)
    }

    internal val ROWS = intArrayOf(R.id.row0, R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5, R.id.row6, R.id.row7)

    /** A row target laid over [r] (card pixels) of the fitted card. */
    internal fun placeRow(views: RemoteViews, rowId: Int, f: Fit, r: android.graphics.RectF) {
        views.setViewVisibility(rowId, android.view.View.VISIBLE)
        views.setViewLayoutWidth(rowId, r.width() * f.scale, TypedValue.COMPLEX_UNIT_PX)
        views.setViewLayoutHeight(rowId, r.height() * f.scale, TypedValue.COMPLEX_UNIT_PX)
        views.setViewLayoutMargin(rowId, RemoteViews.MARGIN_LEFT, f.dx + r.left * f.scale, TypedValue.COMPLEX_UNIT_PX)
        views.setViewLayoutMargin(rowId, RemoteViews.MARGIN_TOP, f.dy + r.top * f.scale, TypedValue.COMPLEX_UNIT_PX)
    }

    // ---- Recent: drawn by the app (its thumbnails live there), kept for when the app is away ------

    private fun recentFile(ctx: Context) = File(ctx.cacheDir, "widget_recent.png")

    private fun recentTopFile(ctx: Context) = File(ctx.cacheDir, "widget_recent_top.txt")

    /** The app's view model while it runs: Recent's thumbnails come from it. */
    @Volatile
    internal var liveVm: java.lang.ref.WeakReference<AppViewModel>? = null

    /** The app's recent documents changed (or a Recent widget was resized): draw the Recent card for every Recent widget. */
    fun refreshRecent(vm: AppViewModel, force: Boolean = false) {
        liveVm = java.lang.ref.WeakReference(vm)
        val app = vm.getApplication<android.app.Application>()
        val mgr = AppWidgetManager.getInstance(app)
        val ids = mgr.getAppWidgetIds(ComponentName(app, RecentWidget::class.java))
        if (ids.isEmpty()) return
        // only when the list really changed (every return to the app asks), and not during the moment
        // the app is coming back, when its own screens are animating
        val sig = vm.recentList.joinToString("|") { it.uri + it.opened } + "#" + ids.joinToString(",")
        if (!force && sig == lastRecentSig) return
        lastRecentSig = sig
        main.removeCallbacks(recentSoon)
        recentVm = java.lang.ref.WeakReference(vm)
        main.postDelayed(recentSoon, 1200L)
    }

    @Volatile private var lastRecentSig: String? = null
    @Volatile private var recentVm: java.lang.ref.WeakReference<AppViewModel>? = null
    private val recentSoon = Runnable { recentVm?.get()?.let { drawRecent(it) } }

    private fun drawRecent(vm: AppViewModel) {
        val app = vm.getApplication<android.app.Application>()
        val mgr = AppWidgetManager.getInstance(app)
        val ids = mgr.getAppWidgetIds(ComponentName(app, RecentWidget::class.java))
        if (ids.isEmpty()) return
        val rows = vm.recentRows()
        val top = vm.recentList.firstOrNull()?.uri
        if (rows.isEmpty()) {
            recentFile(app).delete()
            ids.forEach { RecentWidget.show(app, mgr, it, null, null) }
            return
        }
        ids.forEach { id ->
            val (w, h) = size(app, mgr.getAppWidgetOptions(id))
            enqueue { next ->
                GlassSettings.load(app)
                val choice = themeChoice(app)
                // the desk's own Recent card, exactly: its width on the desk (two columns, 16 dp edges,
                // 12 dp apart), its 226 dp, its fan of pages as it is; the widget fits the whole picture
                // to its size, so every size shows the same card, only larger or smaller
                val pad = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, app.resources.displayMetrics).toInt()
                val deskW = ((app.resources.configuration.screenWidthDp - 44) / 2f).coerceIn(120f, 240f).dp
                val deskWPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, deskW.value, app.resources.displayMetrics).toInt()
                val deskHPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 226f, app.resources.displayMetrics).toInt()
                val inner = minOf(w, h)
                ComposeSnapshot.render(
                    app, deskWPx + 2 * pad, deskHPx + 2 * pad, 0f, settleMs = 1100,
                    content = {
                        ClearTheme(choice) {
                            CompositionLocalProvider(LocalHomeVm provides vm) {
                                Box(Modifier.fillMaxSize().padding(toDp(app, pad))) {
                                    RecentCard(vm, rows, onAll = {}, modifier = Modifier.width(deskW))
                                }
                            }
                        }
                    },
                ) { frame ->
                    if (frame != null) runCatching { recentFile(app).outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                    if (top != null) runCatching { recentTopFile(app).writeText(top) }
                    if (inner > 0) RecentWidget.show(app, mgr, id, frame, top)
                    next()
                }
            }
        }
    }

    internal fun savedRecent(ctx: Context): Pair<Bitmap?, String?> =
        (runCatching { BitmapFactory.decodeFile(recentFile(ctx).path) }.getOrNull()) to runCatching { recentTopFile(ctx).readText() }.getOrNull()
}

/** The most recent list (pinned first), its first items ticked right on the home screen. */
class TodoWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        var left = ids.size
        if (left == 0) return pending.finish()
        ids.forEach { update(context, manager, it) { if (--left == 0) pending.finish() } }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        val pending = goAsync()
        update(context, manager, id) { pending.finish() }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE) {
            val note = intent.getStringExtra(EXTRA_NOTE)
            val item = intent.getStringExtra(EXTRA_ITEM)
            if (note == null || item == null) return
            // the app's own feel for ticking a pill, under its haptics setting
            val app = context.applicationContext
            GlassSettings.load(app)
            val wasDone = NoteFile.read(app).firstOrNull { it.id == note }?.items?.firstOrNull { it.id == item }?.done == true
            runCatching { Haptics(app, android.view.View(app)).play(if (wasDone) Feel.ToggleOff else Feel.ToggleOn) }
            // the file is the truth for a tick from the home screen: written at once, then the app (if it
            // runs) reads it again, so its own copy never writes over the tick
            NoteFile.toggle(app, note, item)
            Notes.live?.takeIf { it.isActive && it.loaded }?.let { live -> Handler(Looper.getMainLooper()).post { live.reload() } }
            val pending = goAsync()
            Widgets.refreshAll(app) { pending.finish() }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_TOGGLE = "com.chethan616.clearpdf.widget.TOGGLE"
        private const val EXTRA_NOTE = "note"
        private const val EXTRA_ITEM = "item"

        fun update(ctx: Context, mgr: AppWidgetManager, id: Int, done: () -> Unit = {}) {
            val app = ctx.applicationContext
            val (w, h) = Widgets.size(app, mgr.getAppWidgetOptions(id))
            // the list chosen for this widget, else the latest
            val list = WidgetPicks.resolve(app, id, NoteKind.List)
            val res = app.resources
            val shown = list ?: Note("", NoteKind.List)
            Widgets.enqueue { next ->
                GlassSettings.load(app)
                val choice = Widgets.themeChoice(app)
                // the desk's own list card, exactly as the desk lays it out (its width, its own height,
                // its first four items and "N more"); the widget fits that very picture to its size
                val deskW = Widgets.deskWidth(app)
                val deskWPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, deskW.value, res.displayMetrics).toInt()
                val placed = LinkedHashMap<String, androidx.compose.ui.geometry.Rect>()
                var card: android.graphics.Rect? = null
                ComposeSnapshot.render(app, deskWPx, deskWPx * 3, Widgets.cornerPx(app), crop = { card }, content = {
                    Widgets.Desk(choice, placed = { card = it }) {
                        ListCard(shown, onOpen = {}, onLong = {}, onToggle = {}, modifier = Modifier.width(deskW), onItemPlaced = { item, r -> placed[item.id] = r })
                    }
                }) { frame ->
                    val top = card?.top ?: 0
                    val bitmap = frame ?: WidgetCards(app, w, h).list(
                        shown, res.getString(R.string.notes_untitled_list), res.getString(R.string.notes_list_empty),
                        moreText = { res.getQuantityString(R.plurals.notes_more_items, it, it) },
                    ).bitmap
                    val spots = if (frame != null) placed.entries.map { (itemId, r) -> itemId to android.graphics.RectF(r.left, r.top - top, r.right, r.bottom - top) } else emptyList()
                    val f = Widgets.fit(w, h, bitmap.width, bitmap.height)
                    val views = RemoteViews(app.packageName, R.layout.widget_card)
                    views.setImageViewBitmap(R.id.card_image, Widgets.onBox(bitmap, w, h))
                    views.setContentDescription(R.id.card_image, shown.title.ifBlank { res.getString(R.string.notes_untitled_list) })
                    views.setOnClickPendingIntent(R.id.card_open, Widgets.open(app, list?.id, "list/$id"))
                    Widgets.placeCardTarget(views, f, bitmap.width, bitmap.height)
                    Widgets.ROWS.forEachIndexed { i, rowId ->
                        val spot = if (list != null) spots.getOrNull(i) else null
                        if (spot == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                            views.setViewVisibility(rowId, android.view.View.GONE)
                            return@forEachIndexed
                        }
                        val (itemId, r) = spot
                        Widgets.placeRow(views, rowId, f, r)
                        val toggle = Intent(app, TodoWidget::class.java).setAction(ACTION_TOGGLE)
                            .setData(Uri.parse("artefact://widget/toggle/${list!!.id}/$itemId"))
                            .putExtra(EXTRA_NOTE, list.id).putExtra(EXTRA_ITEM, itemId)
                        views.setOnClickPendingIntent(
                            rowId,
                            PendingIntent.getBroadcast(app, "${list.id}/$itemId".hashCode(), toggle, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                        )
                    }
                    runCatching { mgr.updateAppWidget(id, views) }
                    next()
                    done()
                }
            }
        }
    }
}

/** The most recent text note (pinned first) as the desk's paper card: the starter "Thought of the day" on a fresh install. */
class NoteWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        var left = ids.size
        if (left == 0) return pending.finish()
        ids.forEach { update(context, manager, it) { if (--left == 0) pending.finish() } }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        val pending = goAsync()
        update(context, manager, id) { pending.finish() }
    }

    companion object {
        fun update(ctx: Context, mgr: AppWidgetManager, id: Int, done: () -> Unit = {}) {
            val app = ctx.applicationContext
            val (w, h) = Widgets.size(app, mgr.getAppWidgetOptions(id))
            // the note chosen for this widget, else the latest
            val note = WidgetPicks.resolve(app, id, NoteKind.Text)
            val res = app.resources
            val shown = note ?: Note("", NoteKind.Text, title = res.getString(R.string.thought_title), body = res.getString(R.string.seed_note_body))
            Widgets.enqueue { next ->
                GlassSettings.load(app)
                val choice = Widgets.themeChoice(app)
                val side = minOf(w, h)
                val sideDp = Widgets.toDp(app, side)
                ComposeSnapshot.render(app, side, side, Widgets.cornerPx(app), content = {
                    ClearTheme(choice) {
                        Box(Modifier.size(sideDp).clip(CardShape).background(Brush.verticalGradient(clear.wall))) {
                            NoteCard(shown, onOpen = {}, onLong = {}, modifier = Modifier.fillMaxSize(), fitHeight = true)
                        }
                    }
                }) { frame ->
                    val bitmap = frame ?: WidgetCards(app, w, h).note(shown, res.getString(R.string.notes_untitled_note)) { Widgets.mediaFile(app, it) }
                    val views = RemoteViews(app.packageName, R.layout.widget_card)
                    views.setImageViewBitmap(R.id.card_image, Widgets.onBox(bitmap, w, h))
                    views.setContentDescription(R.id.card_image, shown.title.ifBlank { res.getString(R.string.notes_untitled_note) })
                    views.setOnClickPendingIntent(R.id.card_open, Widgets.open(app, note?.id, "note/$id"))
                    Widgets.placeCardTarget(views, Widgets.fit(w, h, bitmap.width, bitmap.height), bitmap.width, bitmap.height)
                    Widgets.ROWS.forEach { views.setViewVisibility(it, android.view.View.GONE) }
                    runCatching { mgr.updateAppWidget(id, views) }
                    next()
                    done()
                }
            }
        }
    }
}

/** The desk's Recent card: the newest documents' first pages fanned, opening the newest. */
class RecentWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val (bitmap, top) = Widgets.savedRecent(context)
        ids.forEach { show(context, manager, it, bitmap, top) }
        Widgets.liveVm?.get()?.let { vm -> Handler(Looper.getMainLooper()).post { Widgets.refreshRecent(vm, force = true) } }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        val (bitmap, top) = Widgets.savedRecent(context)
        // at once: the last picture fitted to the new size; then, if the app runs, the card drawn anew
        show(context, manager, id, bitmap, top)
        Widgets.liveVm?.get()?.let { vm -> Handler(Looper.getMainLooper()).post { Widgets.refreshRecent(vm, force = true) } }
    }

    companion object {
        fun show(ctx: Context, mgr: AppWidgetManager, id: Int, bitmap: Bitmap?, top: String?) {
            val app = ctx.applicationContext
            val views = RemoteViews(app.packageName, R.layout.widget_card)
            val (w, h) = Widgets.size(app, mgr.getAppWidgetOptions(id))
            if (bitmap != null) views.setImageViewBitmap(R.id.card_image, Widgets.onBox(bitmap, w, h))
            views.setContentDescription(R.id.card_image, app.getString(R.string.desk_recent))
            if (bitmap != null) Widgets.placeCardTarget(views, Widgets.fit(w, h, bitmap.width, bitmap.height), bitmap.width, bitmap.height)
            // the card opens the newest document, as on the desk; without one, the app's Files
            views.setOnClickPendingIntent(
                R.id.card_open,
                Widgets.activity(app, "recent/$id/${top.orEmpty()}") { if (top != null) putExtra(EXTRA_OPEN_RECENT, top) else putExtra(EXTRA_OPEN_FILES, true) },
            )
            Widgets.ROWS.forEach { views.setViewVisibility(it, android.view.View.GONE) }
            runCatching { mgr.updateAppWidget(id, views) }
        }
    }
}

/**
 * The list card as a square of [sidePx], showing as many of its items as fit (four at most, as on
 * the desk), the rest as "N more": each size shows the card at its own scale, as Apple's widgets do.
 */
@Composable
private fun FitList(wPx: Int, hPx: Int, note: Note, onItemPlaced: (com.chethan616.clearpdf.notes.CheckItem, androidx.compose.ui.geometry.Rect) -> Unit) {
    val wall = clear.wall
    SubcomposeLayout { _ ->
        val wDp = wPx.toDp()
        val hDp = hPx.toDp()
        // as many pills as the widget's height holds (the default 2x3 holds three), at most eight
        var k = 8
        while (k > 1) {
            val n = k
            val probe = subcompose("probe$n") { ListCard(note, onOpen = {}, onLong = {}, onToggle = {}, modifier = Modifier.width(wDp), maxItems = n) }
                .firstOrNull()?.measure(Constraints(minWidth = wPx, maxWidth = wPx)) ?: break
            if (probe.height <= hPx) break
            k--
        }
        val shown = k
        val card = subcompose("card") {
            Box(Modifier.size(wDp, hDp).clip(CardShape).background(Brush.verticalGradient(wall))) {
                ListCard(note, onOpen = {}, onLong = {}, onToggle = {}, modifier = Modifier.fillMaxSize(), onItemPlaced = onItemPlaced, maxItems = shown)
            }
        }.firstOrNull()?.measure(Constraints.fixed(wPx, hPx))
        layout(wPx, hPx) { card?.place(0, 0) }
    }
}
