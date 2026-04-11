package com.example.cuoiky

// ============================================================
// FlashMind - Ứng dụng học Flashcard với thuật toán SM-2
// MỘT FILE DUY NHẤT: MainActivity.kt
//
// HƯỚNG DẪN:
// 1. Android Studio → New Project → Empty Views Activity → Kotlin
// 2. Package name: com.flashmind  |  Min SDK: API 26
// 3. Xóa MainActivity.kt cũ, thay bằng file này
// 4. Thêm dependencies vào app/build.gradle (xem cuối file)
// 5. Sync Gradle → Run
// ============================================================

import android.animation.*
import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.tts.*
import android.text.*
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.room.*
import androidx.work.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────
// ROOM ENTITIES
// ─────────────────────────────────────────────

@Entity(tableName = "decks")
data class Deck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val description: String = "",
    val colorHex: String = "#7C4DFF", val emoji: String = "📚",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cards", foreignKeys = [ForeignKey(
    entity = Deck::class, parentColumns = ["id"], childColumns = ["deckId"],
    onDelete = ForeignKey.CASCADE)], indices = [Index("deckId")])
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckId: Long, val front: String, val back: String,
    val phonetic: String = "", val example: String = "", val tags: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sm2")
data class Sm2(
    @PrimaryKey val cardId: Long,
    val n: Int = 0, val ef: Double = 2.5, val interval: Int = 1,
    val due: Long = System.currentTimeMillis(), val lastQ: Int = 0
)

@Entity(tableName = "logs")
data class ReviewLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long, val deckId: Long, val quality: Int,
    val at: Long = System.currentTimeMillis()
)

// ─────────────────────────────────────────────
// DAOs
// ─────────────────────────────────────────────

@Dao interface DeckDao {
    @Query("SELECT * FROM decks ORDER BY createdAt DESC") fun all(): LiveData<List<Deck>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(d: Deck): Long
    @Update suspend fun update(d: Deck)
    @Delete suspend fun delete(d: Deck)
}

@Dao interface CardDao {
    @Query("SELECT * FROM cards WHERE deckId=:did ORDER BY createdAt ASC") fun byDeck(did: Long): LiveData<List<Card>>
    @Query("SELECT * FROM cards WHERE deckId=:did") suspend fun byDeckSync(did: Long): List<Card>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(c: Card): Long
    @Delete suspend fun delete(c: Card)
}

@Dao interface Sm2Dao {
    @Query("SELECT * FROM sm2 WHERE cardId=:id") suspend fun get(id: Long): Sm2?
    @Query("SELECT * FROM sm2 WHERE cardId IN (:ids)") suspend fun getMany(ids: List<Long>): List<Sm2>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(s: Sm2)
    @Query("SELECT COUNT(*) FROM sm2 WHERE cardId IN (:ids) AND n>=5") suspend fun learnedCount(ids: List<Long>): Int
}

@Dao interface LogDao {
    @Insert suspend fun insert(l: ReviewLog)
    @Query("SELECT COUNT(*) FROM logs") fun total(): LiveData<Int>
    @Query("SELECT * FROM logs WHERE at>=:since") suspend fun since(since: Long): List<ReviewLog>
}

// ─────────────────────────────────────────────
// DATABASE
// ─────────────────────────────────────────────

@Database(entities = [Deck::class, Card::class, Sm2::class, ReviewLog::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun decks(): DeckDao; abstract fun cards(): CardDao
    abstract fun sm2(): Sm2Dao; abstract fun logs(): LogDao

    companion object {
        @Volatile private var INSTANCE: AppDb? = null
        fun get(ctx: Context): AppDb = INSTANCE ?: synchronized(this) {
            Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "flashmind.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch { seed(get(ctx)) }
                    }
                }).build().also { INSTANCE = it }
        }

        private suspend fun seed(db: AppDb) {
            val d1 = db.decks().insert(Deck(name = "Tiếng Anh Nâng Cao", description = "Academic vocabulary", colorHex = "#7C4DFF", emoji = "🇬🇧"))
            val d2 = db.decks().insert(Deck(name = "Kanji N5", description = "Chữ Hán JLPT N5", colorHex = "#E53935", emoji = "🇯🇵"))
            val d3 = db.decks().insert(Deck(name = "Từ Vựng IELTS", description = "Band 7+ vocabulary", colorHex = "#1E88E5", emoji = "📖"))
            listOf(
                Card(deckId=d1,front="Serendipity",phonetic="/ˌserənˈdɪpɪti/",back="May mắn bất ngờ",example="Finding this book was pure serendipity.",tags="noun"),
                Card(deckId=d1,front="Ephemeral",phonetic="/ɪˈfemərəl/",back="Phù du, thoáng qua",example="Youth is ephemeral.",tags="adjective"),
                Card(deckId=d1,front="Perseverance",phonetic="/ˌpɜːrsɪˈvɪərəns/",back="Sự kiên trì",example="Success requires perseverance.",tags="noun"),
                Card(deckId=d1,front="Eloquent",phonetic="/ˈeləkwənt/",back="Hùng hồn, diễn đạt tốt",example="She gave an eloquent speech.",tags="adjective"),
                Card(deckId=d1,front="Resilience",phonetic="/rɪˈzɪliəns/",back="Sự kiên cường",example="Resilience is key to success.",tags="noun"),
                Card(deckId=d2,front="日",phonetic="にち / ひ",back="Mặt trời / Ngày",example="今日は晴れです。",tags="kanji"),
                Card(deckId=d2,front="山",phonetic="さん / やま",back="Núi",example="富士山は美しい。",tags="kanji"),
                Card(deckId=d2,front="水",phonetic="すい / みず",back="Nước",example="水が飲みたい。",tags="kanji"),
                Card(deckId=d2,front="火",phonetic="か / ひ",back="Lửa / Thứ Ba",example="火曜日は火曜。",tags="kanji"),
                Card(deckId=d3,front="Mitigate",phonetic="/ˈmɪtɪɡeɪt/",back="Giảm nhẹ, làm dịu",example="Measures to mitigate climate change.",tags="verb"),
                Card(deckId=d3,front="Ubiquitous",phonetic="/juːˈbɪkwɪtəs/",back="Có mặt khắp nơi",example="Smartphones are ubiquitous.",tags="adjective"),
                Card(deckId=d3,front="Paradigm",phonetic="/ˈpærədaɪm/",back="Mô hình tư duy",example="A paradigm shift in thinking.",tags="noun"),
                Card(deckId=d3,front="Empirical",phonetic="/ɪmˈpɪrɪkəl/",back="Dựa trên thực nghiệm",example="Empirical evidence supports this.",tags="adjective"),
            ).forEach { db.cards().insert(it) }
        }
    }
}

// ─────────────────────────────────────────────
// SM-2 ALGORITHM
// ─────────────────────────────────────────────

object SM2 {
    data class Result(val n: Int, val ef: Double, val interval: Int, val due: Long)

    fun calculate(cur: Sm2?, quality: Int): Result {
        val n = cur?.n ?: 0; val ef = cur?.ef ?: 2.5; val iv = cur?.interval ?: 1
        val newN: Int; val newIv: Int
        if (quality >= 3) {
            newN = n + 1
            newIv = when (n) { 0 -> 1; 1 -> 6; else -> (iv * ef).toInt().coerceAtLeast(1) }
        } else { newN = 0; newIv = 1 }
        val newEf = (ef + 0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)).coerceAtLeast(1.3)
        val due = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(newIv.toLong())
        return Result(newN, newEf, newIv, due)
    }

    fun isDue(sm2: Sm2?) = sm2 == null || System.currentTimeMillis() >= sm2.due
    fun nextIv(cur: Sm2?, q: Int) = calculate(cur, q).interval
}

// ─────────────────────────────────────────────
// REPOSITORY
// ─────────────────────────────────────────────

class Repo(private val db: AppDb) {
    fun allDecks() = db.decks().all()
    suspend fun insertDeck(d: Deck) = db.decks().insert(d)
    suspend fun deleteDeck(d: Deck) = db.decks().delete(d)
    fun cardsByDeck(id: Long) = db.cards().byDeck(id)
    suspend fun cardsByDeckSync(id: Long) = db.cards().byDeckSync(id)
    suspend fun insertCard(c: Card) = db.cards().insert(c)
    fun totalReviews() = db.logs().total()

    suspend fun dueCards(deckId: Long): List<Card> {
        val cards = db.cards().byDeckSync(deckId)
        val sm2Map = if (cards.isEmpty()) emptyMap() else db.sm2().getMany(cards.map { it.id }).associateBy { it.cardId }
        return cards.filter { SM2.isDue(sm2Map[it.id]) }
    }

    suspend fun processReview(cardId: Long, deckId: Long, quality: Int) {
        val cur = db.sm2().get(cardId)
        val r = SM2.calculate(cur, quality)
        db.sm2().upsert(Sm2(cardId, r.n, r.ef, r.interval, r.due, quality))
        db.logs().insert(ReviewLog(cardId = cardId, deckId = deckId, quality = quality))
    }

    suspend fun getSm2(cardId: Long) = db.sm2().get(cardId)

    suspend fun learnedCount(deckId: Long): Int {
        val ids = db.cards().byDeckSync(deckId).map { it.id }
        return if (ids.isEmpty()) 0 else db.sm2().learnedCount(ids)
    }

    suspend fun heatmap(days: Int): Map<String, Int> {
        val since = System.currentTimeMillis() - days * 86400000L
        return db.logs().since(since).groupBy {
            Calendar.getInstance().apply { timeInMillis = it.at }.let {
                "${it.get(Calendar.YEAR)}-${it.get(Calendar.MONTH) + 1}-${it.get(Calendar.DAY_OF_MONTH)}"
            }
        }.mapValues { it.value.size }
    }

    suspend fun reviewsToday(): Int {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return db.logs().since(start).size
    }
}

// ─────────────────────────────────────────────
// WORK MANAGER
// ─────────────────────────────────────────────

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    companion object {
        const val CH = "fm_remind"
        fun schedule(ctx: Context) {
            val now = Calendar.getInstance()
            val t = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 8); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(t.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("daily", ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }

    override suspend fun doWork(): Result {
        val pi = PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(applicationContext, CH)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⏰ Đến giờ ôn thẻ rồi!")
            .setContentText("Bạn có thẻ cần ôn hôm nay. Dành 5 phút để không quên nhé!")
            .setContentIntent(pi).setAutoCancel(true).build()
        (applicationContext.getSystemService(NotificationManager::class.java)).notify(101, n)
        return Result.success()
    }
}

// ─────────────────────────────────────────────
// HEATMAP CUSTOM VIEW
// ─────────────────────────────────────────────

class HeatmapView(ctx: Context, private val data: Map<String, Int>) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = (ctx.resources.displayMetrics.density)
    private val cell = (d * 18).toInt(); private val gap = (d * 4).toInt()
    private val cols = 14; private val rows = 2

    override fun onMeasure(ws: Int, hs: Int) = setMeasuredDimension(
        MeasureSpec.getSize(ws), rows * cell + (rows - 1) * gap + (d * 30).toInt()
    )

    override fun onDraw(c: Canvas) {
        repeat(cols * rows) { i ->
            val day = cols * rows - 1 - i
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -day) }
            val key = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)+1}-${cal.get(Calendar.DAY_OF_MONTH)}"
            val count = data[key] ?: 0
            val col = i % cols; val row = i / cols
            val x = col * (cell + gap).toFloat(); val y = row * (cell + gap).toFloat()
            p.color = when { count == 0 -> Color.parseColor("#1E1E35"); count < 3 -> Color.argb(80,124,77,255); count < 6 -> Color.argb(150,124,77,255); count < 10 -> Color.argb(210,124,77,255); else -> Color.parseColor("#7C4DFF") }
            c.drawRoundRect(RectF(x, y, x + cell, y + cell), d * 3, d * 3, p)
        }
        p.color = Color.parseColor("#8892A4"); p.textSize = d * 10
        val sdf = SimpleDateFormat("dd/M", Locale.getDefault())
        listOf(0, 7, 13).forEach { ci ->
            val day = cols * rows - 1 - ci
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -day) }
            c.drawText(sdf.format(cal.time), ci * (cell + gap).toFloat(), (rows * cell + (rows - 1) * gap + d * 16), p)
        }
    }
}

// ─────────────────────────────────────────────
// MAIN ACTIVITY
// ─────────────────────────────────────────────

class MainActivity : AppCompatActivity() {

    private lateinit var repo: Repo
    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    // Color palette – dark premium theme
    private val BG = Color.parseColor("#0D0D1A")
    private val SURFACE = Color.parseColor("#14142B")
    private val CARD_BG = Color.parseColor("#1A1A35")
    private val BORDER = Color.parseColor("#2A2A50")
    private val ACCENT = Color.parseColor("#7C4DFF")
    private val ACCENT_DIM = Color.argb(35, 124, 77, 255)
    private val TEXT = Color.parseColor("#EAEAF4")
    private val MUTED = Color.parseColor("#7A7A9A")
    private val C_GREEN = Color.parseColor("#00E676")
    private val C_ORANGE = Color.parseColor("#FF9100")
    private val C_RED = Color.parseColor("#FF5252")
    private val C_BLUE = Color.parseColor("#40C4FF")

    // Study state
    private var studyDeckId = 0L; private var studyDeckName = ""
    private var studyQueue = mutableListOf<Card>()
    private var studyIdx = 0
    private var sAgain = 0; private var sHard = 0; private var sGood = 0; private var sEasy = 0

    private lateinit var root: FrameLayout
    private var currentTab = "decks"

    // ─── LIFECYCLE ───────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG; window.navigationBarColor = SURFACE
        repo = Repo(AppDb.get(this))
        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) ttsReady = true }
        (getSystemService(NotificationManager::class.java)).createNotificationChannel(
            NotificationChannel(ReminderWorker.CH, "Nhắc nhở học tập", NotificationManager.IMPORTANCE_DEFAULT)
        )
        ReminderWorker.schedule(this)
        root = FrameLayout(this).apply { setBackgroundColor(BG) }
        setContentView(root)
        showDecks()
    }

    override fun onBackPressed() { if (currentTab != "decks") showDecks() else super.onBackPressed() }
    override fun onDestroy() { tts.stop(); tts.shutdown(); super.onDestroy() }

    // ─── HELPERS ─────────────────────────────

    private val d get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    private fun gd(color: Int = CARD_BG, radius: Float = 16f, stroke: Int = 0, strokeColor: Int = BORDER) =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius.toInt()).toFloat(); if (stroke > 0) setStroke(stroke, strokeColor) }

    private fun tv(t: String = "", sz: Float = 14f, col: Int = TEXT, bold: Boolean = false, center: Boolean = false) =
        TextView(this).apply {
            text = t; textSize = sz; setTextColor(col)
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
            if (center) gravity = Gravity.CENTER
        }

    private fun row(action: LinearLayout.() -> Unit) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; action() }
    private fun col(action: LinearLayout.() -> Unit) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; action() }

    private fun surface(radiusDp: Int = 16, strokeW: Int = 1, padding: Int = 16, action: LinearLayout.() -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = gd(CARD_BG, radiusDp.toFloat(), strokeW, BORDER)
            setPadding(dp(padding), dp(padding), dp(padding), dp(padding)); action()
        }

    private fun pill(label: String, color: Int, dimColor: Int, action: (() -> Unit)? = null) =
        TextView(this).apply {
            text = label; textSize = 12f; setTextColor(color); gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = gd(dimColor, 8f, 1, color)
            action?.let { setOnClickListener { it() } }
        }

    private fun bigBtn(label: String, bg: Int = ACCENT, tc: Int = Color.WHITE, action: () -> Unit) =
        TextView(this).apply {
            text = label; textSize = 15f; setTextColor(tc)
            setTypeface(null, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER
            background = gd(bg, 14f); setPadding(dp(20), dp(15), dp(20), dp(15))
            setOnClickListener { action() }
        }

    private fun input(hint: String, lines: Int = 1) = EditText(this).apply {
        this.hint = hint; setHintTextColor(MUTED); setTextColor(TEXT); textSize = 15f
        background = gd(Color.parseColor("#12122A"), 10f, 1); setPadding(dp(14), dp(12), dp(14), dp(12))
        if (lines > 1) { minLines = lines; maxLines = lines + 2; isSingleLine = false }
    }

    private fun divider() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { topMargin = dp(12); bottomMargin = dp(12) }
        setBackgroundColor(BORDER)
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    private fun speak(text: String, lang: Locale = Locale.US) {
        if (!ttsReady) return; tts.language = lang
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u_${System.currentTimeMillis()}")
    }

    // ─── BOTTOM NAV ──────────────────────────

    private fun buildNav(): LinearLayout {
        data class N(val key: String, val label: String)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(SURFACE)
            listOf(N("decks","Bộ thẻ"), N("add","+ Thêm"), N("stats","Thống kê")).forEach { n ->
                col {
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    val active = n.key == currentTab
                    val col = if (active) ACCENT else MUTED
                    val dot = View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(5), dp(5)).apply { topMargin = dp(8); bottomMargin = dp(4) }
                        background = gd(if (active) ACCENT else Color.TRANSPARENT, 3f)
                    }
                    addView(dot)
                    addView(tv(n.label, 12f, col, bold = active, center = true))
                    setPadding(0, 0, 0, dp(8))
                    setOnClickListener { when (n.key) { "decks" -> showDecks(); "add" -> showAdd(); "stats" -> showStats() } }
                }.also { addView(it) }
            }
        }
    }

    private fun screen(build: LinearLayout.() -> Unit): View {
        val navH = dp(64)
        return FrameLayout(this).apply {
            setBackgroundColor(BG)
            val scroll = ScrollView(context).apply {
                isVerticalScrollBarEnabled = false
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply { bottomMargin = navH }
                addView(col {
                    setPadding(dp(16), dp(52), dp(16), dp(16))
                    build()
                })
            }
            val nav = buildNav().apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, navH, Gravity.BOTTOM)
            }
            addView(scroll); addView(nav)
        }
    }

    private fun show(v: View) { root.removeAllViews(); root.addView(v) }

    // ─── DECKS SCREEN ────────────────────────

    private fun showDecks() {
        currentTab = "decks"
        val page = screen {
            // Header
            addView(tv("FlashMind", 30f, TEXT, bold = true))
            addView(tv("Học thông minh với thuật toán SM-2", 13f, MUTED).apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(20) })

            val listContainer = col {}
            addView(listContainer)

            repo.allDecks().observe(this@MainActivity) { decks ->
                listContainer.removeAllViews()
                if (decks.isEmpty()) {
                    listContainer.addView(tv("Chưa có bộ thẻ nào\nNhấn nút bên dưới để tạo!", 15f, MUTED, center = true).apply {
                        (layoutParams as LinearLayout.LayoutParams).apply { topMargin = dp(50); bottomMargin = dp(20) }
                    })
                } else {
                    CoroutineScope(Dispatchers.Main).launch {
                        decks.forEach { deck ->
                            val due = withContext(Dispatchers.IO) { repo.dueCards(deck.id) }
                            val total = withContext(Dispatchers.IO) { repo.cardsByDeckSync(deck.id).size }
                            val learned = withContext(Dispatchers.IO) { repo.learnedCount(deck.id) }
                            val dc = try { Color.parseColor(deck.colorHex) } catch (e: Exception) { ACCENT }
                            val dimC = Color.argb(25, Color.red(dc), Color.green(dc), Color.blue(dc))

                            surface {
                                // Top color strip
                                addView(View(context).apply {
                                    background = gd(dc, 12f)
                                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4)).apply { bottomMargin = dp(14); leftMargin = -dp(16); rightMargin = -dp(16); topMargin = -dp(16) }
                                })
                                // Emoji + name row
                                addView(row {
                                    addView(tv(deck.emoji, 28f).apply {
                                        background = gd(dimC, 12f); setPadding(dp(8), dp(4), dp(8), dp(4))
                                        (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(12)
                                    })
                                    addView(col {
                                        addView(tv(deck.name, 17f, TEXT, bold = true))
                                        addView(tv(deck.description.ifEmpty { "$total thẻ" }, 12f, MUTED))
                                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                                    })
                                }.apply { gravity = Gravity.CENTER_VERTICAL; (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(12) })

                                // Stats
                                addView(row {
                                    listOf(Triple("$total","Thẻ",TEXT), Triple("$learned","Thuộc",C_GREEN), Triple("${due.size}","Cần ôn",if(due.isNotEmpty()) ACCENT else MUTED)).forEach { (n,l,c) ->
                                        col {
                                            addView(tv(n, 18f, c, bold = true, center = true))
                                            addView(tv(l, 11f, MUTED, center = true))
                                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                                        }.also { addView(it) }
                                    }
                                }.apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin = if (due.isNotEmpty()) dp(12) else 0 })

                                // Study button
                                if (due.isNotEmpty()) {
                                    addView(tv("▶  Bắt đầu ôn ${due.size} thẻ hôm nay", 13f, dc, bold = true, center = true).apply {
                                        background = gd(dimC, 10f, 1, dc); setPadding(0, dp(10), 0, dp(10))
                                        setOnClickListener { startStudy(deck.id, deck.name) }
                                    })
                                } else {
                                    addView(tv("✓ Hoàn thành hôm nay!", 12f, C_GREEN, center = true).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(4) })
                                }

                                setOnClickListener { if (due.isEmpty()) toast("🎉 Không có thẻ cần ôn hôm nay!") else startStudy(deck.id, deck.name) }
                                setOnLongClickListener {
                                    AlertDialog.Builder(context).setTitle("Xóa bộ thẻ \"${deck.name}\"?")
                                        .setMessage("Toàn bộ thẻ trong bộ này sẽ bị xóa.")
                                        .setPositiveButton("Xóa") { _, _ -> CoroutineScope(Dispatchers.IO).launch { repo.deleteDeck(deck) } }
                                        .setNegativeButton("Hủy", null).show(); true
                                }
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) }
                            }.also { listContainer.addView(it) }
                        }
                        // Create button
                        listContainer.addView(tv("＋  Tạo bộ thẻ mới", 15f, ACCENT, bold = true, center = true).apply {
                            background = gd(Color.TRANSPARENT, 14f, 2, ACCENT); setPadding(0, dp(14), 0, dp(14))
                            setOnClickListener { showCreateDeckDialog() }
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        })
                    }
                }
            }
        }
        show(page)
    }

    private fun showCreateDeckDialog() {
        val nameEt = input("Tên bộ thẻ *")
        val descEt = input("Mô tả (tuỳ chọn)")
        val lv = col {
            setPadding(dp(20), dp(16), dp(20), dp(8))
            addView(tv("Tên bộ thẻ", 12f, MUTED).apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(6) })
            addView(nameEt.apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(14) })
            addView(tv("Mô tả", 12f, MUTED).apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(6) })
            addView(descEt)
        }
        AlertDialog.Builder(this).setTitle("Tạo bộ thẻ mới").setView(lv)
            .setPositiveButton("Tạo") { _, _ ->
                val name = nameEt.text.toString().trim()
                if (name.isEmpty()) { toast("Nhập tên bộ thẻ!"); return@setPositiveButton }
                val colors = listOf("#7C4DFF","#E53935","#1E88E5","#2E7D32","#E65100","#AD1457")
                val emojis = listOf("📚","🇯🇵","📖","🔬","🎨","🧮")
                val i = (0..5).random()
                CoroutineScope(Dispatchers.IO).launch {
                    repo.insertDeck(Deck(name=name, description=descEt.text.toString().trim(), colorHex=colors[i], emoji=emojis[i]))
                }
            }.setNegativeButton("Hủy", null).show()
    }

    // ─── STUDY SCREEN ────────────────────────

    private fun startStudy(deckId: Long, deckName: String) {
        studyDeckId = deckId; studyDeckName = deckName
        CoroutineScope(Dispatchers.Main).launch {
            val due = withContext(Dispatchers.IO) { repo.dueCards(deckId) }
            if (due.isEmpty()) { toast("Không có thẻ cần ôn!"); return@launch }
            studyQueue = due.shuffled().toMutableList()
            studyIdx = 0; sAgain = 0; sHard = 0; sGood = 0; sEasy = 0
            showStudyCard()
        }
    }

    private fun showStudyCard() {
        if (studyIdx >= studyQueue.size) { showDone(); return }
        val card = studyQueue[studyIdx]
        val isJP = card.front.any { it.code in 0x3040..0x9FFF }

        CoroutineScope(Dispatchers.Main).launch {
            val sm2 = withContext(Dispatchers.IO) { repo.getSm2(card.id) }

            // Build full-screen study layout
            val page = FrameLayout(this@MainActivity).apply { setBackgroundColor(BG) }
            val content = col {
                setPadding(dp(16), dp(48), dp(16), dp(24))
            }
            page.addView(ScrollView(this@MainActivity).apply { isVerticalScrollBarEnabled = false; addView(content) })

            // — Back button + title
            content.addView(row {
                addView(tv("←", 22f, MUTED).apply { setPadding(0,0,dp(12),0); setOnClickListener { showDecks() } })
                addView(tv(studyDeckName, 17f, TEXT, bold=true).apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
                addView(tv("${studyIdx+1}/${studyQueue.size}", 13f, MUTED))
                gravity = Gravity.CENTER_VERTICAL
                (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(14)
            })

            // — Progress bar
            content.addView(FrameLayout(this@MainActivity).apply {
                addView(View(context).apply { background = gd(BORDER, 4f); layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(6)) })
                val pct = studyIdx.toFloat() / studyQueue.size
                val w = ((resources.displayMetrics.widthPixels - dp(32)) * pct).toInt()
                addView(View(context).apply { background = gd(ACCENT, 4f); layoutParams = FrameLayout.LayoutParams(w.coerceAtLeast(dp(8)), dp(6)) })
                (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(14)
            })

            // — SM-2 strip
            content.addView(surface(12, 1, 12) {
                addView(row {
                    listOf(Triple("${sm2?.n ?: 0}","Lần ôn",ACCENT), Triple(String.format("%.1f", sm2?.ef ?: 2.5),"Hệ số EF",C_BLUE), Triple("${sm2?.interval ?: 0}d","Khoảng cách",C_GREEN)).forEach { (v,l,c) ->
                        col {
                            addView(tv(v, 15f, c, bold=true, center=true))
                            addView(tv(l, 10f, MUTED, center=true))
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        }.also { addView(it) }
                    }
                })
                (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(16)
            })

            // — Flashcard (flip animation)
            val cardH = dp(220)
            val cardFrame = FrameLayout(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, cardH).apply { bottomMargin = dp(16) }
            }

            val frontCard = surface(20, 1, 20) {
                background = gd(Color.parseColor("#1A0E3A"), 20f, 1, ACCENT)
                gravity = Gravity.CENTER
                addView(tv("NHẤN ĐỂ XEM ĐÁP ÁN", 9f, MUTED, center=true).apply { letterSpacing = 0.12f; (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(8) })
                addView(tv(card.front, if(card.front.length < 8) 34f else 24f, TEXT, bold=true, center=true).apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(6) })
                if (card.phonetic.isNotEmpty()) addView(tv(card.phonetic, 14f, MUTED, center=true).apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(10) })
                addView(pill("🔊  Nghe phát âm", C_BLUE, Color.argb(25,64,196,255)) { speak(card.front, if(isJP) Locale.JAPANESE else Locale.US) })
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, cardH)
            }

            val backCard = surface(20, 1, 20) {
                background = gd(Color.parseColor("#0A1E40"), 20f, 1, C_BLUE)
                gravity = Gravity.CENTER; visibility = View.GONE
                addView(tv("NGHĨA", 9f, MUTED, center=true).apply { letterSpacing = 0.12f; (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(8) })
                addView(tv(card.back, if(card.back.length < 12) 28f else 20f, TEXT, bold=true, center=true).apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(8) })
                if (card.example.isNotEmpty()) addView(tv("\"${card.example}\"", 13f, MUTED, center=true).apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(8) })
                if (card.tags.isNotEmpty()) addView(pill(card.tags, ACCENT, ACCENT_DIM))
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, cardH)
            }

            cardFrame.addView(frontCard); cardFrame.addView(backCard)

            // Rating buttons (hidden until flipped)
            val ratingRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL; visibility = View.GONE
                (layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = dp(16)
            }

            val flipBtn = tv("   Xem đáp án   ", 15f, Color.WHITE, bold=true, center=true).apply {
                background = gd(ACCENT, 14f); setPadding(0, dp(15), 0, dp(15))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)).apply { bottomMargin = dp(16) }
            }

            var flipped = false
            fun doFlip() {
                flipped = !flipped
                val hide = if (flipped) frontCard else backCard
                val show = if (flipped) backCard else frontCard
                val outA = ObjectAnimator.ofFloat(hide, "rotationY", 0f, 90f).apply { duration = 160; interpolator = AccelerateDecelerateInterpolator() }
                outA.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: Animator) {
                        hide.visibility = View.GONE; show.visibility = View.VISIBLE
                        ObjectAnimator.ofFloat(show, "rotationY", -90f, 0f).apply { duration = 160; interpolator = AccelerateDecelerateInterpolator() }.start()
                    }
                })
                outA.start()
                flipBtn.visibility = if (flipped) View.GONE else View.VISIBLE
                ratingRow.visibility = if (flipped) View.VISIBLE else View.GONE
                if (flipped) speak(card.front, if(isJP) Locale.JAPANESE else Locale.US)
            }

            cardFrame.setOnClickListener { doFlip() }
            flipBtn.setOnClickListener { doFlip() }

            // Build 4 rating buttons
            data class RB(val label: String, val q: Int, val c: Int, val bg: Int)
            listOf(
                RB("✗\nQuên\n+1d", 1, C_RED, Color.argb(30,255,82,82)),
                RB("△\nKhó\n+${SM2.nextIv(sm2,3)}d", 3, C_ORANGE, Color.argb(30,255,145,0)),
                RB("○\nTốt\n+${SM2.nextIv(sm2,4)}d", 4, C_BLUE, Color.argb(30,64,196,255)),
                RB("★\nDễ\n+${SM2.nextIv(sm2,5)}d", 5, C_GREEN, Color.argb(30,0,230,118))
            ).forEachIndexed { i, rb ->
                ratingRow.addView(tv(rb.label, 11f, rb.c, bold=true, center=true).apply {
                    background = gd(rb.bg, 12f, 1, rb.c); setPadding(dp(4), dp(10), dp(4), dp(10))
                    layoutParams = LinearLayout.LayoutParams(0, dp(72), 1f).apply { if (i > 0) leftMargin = dp(6) }
                    setOnClickListener {
                        CoroutineScope(Dispatchers.Main).launch {
                            withContext(Dispatchers.IO) { repo.processReview(card.id, studyDeckId, rb.q) }
                            when (rb.q) { 1->sAgain++; 3->sHard++; 4->sGood++; 5->sEasy++ }
                            if (rb.q <= 2) studyQueue.add(card)
                            studyIdx++; showStudyCard()
                        }
                    }
                })
            }

            content.addView(cardFrame)
            content.addView(flipBtn)
            content.addView(ratingRow.apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(16) } })

            show(page)
        }
    }

    private fun showDone() {
        val total = sAgain + sHard + sGood + sEasy
        val pct = if (total == 0) 0 else (sGood + sEasy) * 100 / total
        val emoji = when { pct >= 90 -> "🏆"; pct >= 70 -> "🎉"; pct >= 60 -> "👍"; else -> "💪" }
        val msgColor = when { pct >= 70 -> C_GREEN; pct >= 50 -> C_ORANGE; else -> MUTED }

        val page = screen {
            addView(tv(emoji, 72f, TEXT, center=true).apply { gravity = Gravity.CENTER; (layoutParams as LinearLayout.LayoutParams).apply { topMargin = dp(32); gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(12) } })
            addView(tv("Phiên học hoàn thành!", 26f, TEXT, bold=true, center=true).apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(8) })
            addView(tv("Tỉ lệ ghi nhớ: $pct%", 16f, msgColor, bold=true, center=true).apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(24) })

            addView(surface {
                addView(row {
                    listOf(Triple("$sAgain","Quên",C_RED), Triple("$sHard","Khó",C_ORANGE), Triple("$sGood","Tốt",C_BLUE), Triple("$sEasy","Dễ",C_GREEN)).forEach { (n,l,c) ->
                        col {
                            addView(tv(n, 24f, c, bold=true, center=true))
                            addView(tv(l, 12f, MUTED, center=true))
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        }.also { addView(it) }
                    }
                })
                (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(20)
            })

            addView(bigBtn("  Về trang chính") { showDecks() }.apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)).apply { bottomMargin = dp(12) }
            })
            addView(bigBtn("  Học lại bộ thẻ này", Color.TRANSPARENT, ACCENT) { startStudy(studyDeckId, studyDeckName) }.apply {
                background = gd(Color.TRANSPARENT, 14f, 2, ACCENT)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56))
            })
        }
        show(page)
    }

    // ─── ADD CARD SCREEN ─────────────────────

    private fun showAdd() {
        currentTab = "add"
        var selectedDeckId = -1L; var decksList = listOf<Deck>()

        val page = screen {
            addView(tv("Thêm thẻ mới", 26f, TEXT, bold=true).apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(20) })

            // Deck selector
            val deckSpinner = Spinner(context).apply {
                background = gd(Color.parseColor("#12122A"), 10f, 1); setPadding(dp(14),0,dp(14),0)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply { bottomMargin = dp(16) }
            }
            addView(tv("Chọn bộ thẻ", 12f, MUTED).apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(6) })
            addView(deckSpinner)

            repo.allDecks().observe(this@MainActivity) { dl ->
                decksList = dl
                if (dl.isNotEmpty()) selectedDeckId = dl[0].id
                deckSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, dl.map { it.name }).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                deckSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { selectedDeckId = decksList[pos].id }
                    override fun onNothingSelected(p: AdapterView<*>?) {}
                }
            }

            fun field(label: String, hint: String, lines: Int = 1): EditText {
                addView(tv(label, 12f, MUTED).apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(6) })
                return input(hint, lines).also { addView(it.apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) } }) }
            }

            val frontEt = field("Mặt trước *", "Từ / câu hỏi...")
            val phoneticEt = field("Phiên âm", "/fəˈnetɪk/ hoặc にち/ひ")
            val backEt = field("Mặt sau *", "Nghĩa / đáp án...", 2)
            val exampleEt = field("Ví dụ", "Câu ví dụ...", 2)
            val tagsEt = field("Tags", "noun, verb, N5, adjective...")

            // Live preview card
            val prevFront = tv("?", 26f, TEXT, bold=true, center=true)
            val prevBack = tv("", 16f, MUTED, center=true)
            addView(surface(16, 1, 16) {
                background = gd(Color.parseColor("#1A0E3A"), 16f, 1, ACCENT)
                gravity = Gravity.CENTER
                addView(tv("xem trước", 10f, MUTED, center=true).apply { letterSpacing = 0.1f; (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(8) })
                addView(prevFront.apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(6) })
                addView(prevBack)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(130)).apply { bottomMargin = dp(20) }
            })

            frontEt.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { prevFront.text = s?.toString()?.ifEmpty { "?" } ?: "?" }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
            backEt.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { prevBack.text = s?.toString() ?: "" }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })

            addView(bigBtn("  Thêm thẻ  ") {
                val front = frontEt.text.toString().trim(); val back = backEt.text.toString().trim()
                if (front.isEmpty() || back.isEmpty()) { toast("Nhập mặt trước và mặt sau!"); return@bigBtn }
                if (selectedDeckId == -1L) { toast("Chọn bộ thẻ!"); return@bigBtn }
                CoroutineScope(Dispatchers.IO).launch {
                    repo.insertCard(Card(deckId=selectedDeckId, front=front, back=back, phonetic=phoneticEt.text.toString().trim(), example=exampleEt.text.toString().trim(), tags=tagsEt.text.toString().trim()))
                }
                frontEt.text.clear(); phoneticEt.text.clear(); backEt.text.clear(); exampleEt.text.clear(); tagsEt.text.clear()
                toast("✓ Đã thêm thẻ thành công!")
            }.apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)) })
        }
        show(page)
    }

    // ─── STATS SCREEN ────────────────────────

    private fun showStats() {
        currentTab = "stats"
        val page = screen {
            addView(tv("Thống kê", 26f, TEXT, bold=true).apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(20) })
            val container = col {}; addView(container)

            repo.totalReviews().observe(this@MainActivity) { totalReviews ->
                CoroutineScope(Dispatchers.Main).launch {
                    container.removeAllViews()
                    val today = withContext(Dispatchers.IO) { repo.reviewsToday() }
                    val heat = withContext(Dispatchers.IO) { repo.heatmap(28) }

                    // Grid stats
                    container.addView(row {
                        listOf(Triple("$totalReviews","Tổng lần ôn",ACCENT), Triple("$today","Ôn hôm nay",C_GREEN)).forEach { (n,l,c) ->
                            surface(14, 1, 16) {
                                addView(tv(n, 30f, c, bold=true, center=true))
                                addView(tv(l, 12f, MUTED, center=true))
                                gravity = Gravity.CENTER
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { if (l == "Ôn hôm nay") leftMargin = dp(12) }
                            }.also { addView(it) }
                        }
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(16)
                    })

                    // Heatmap
                    container.addView(surface {
                        addView(tv("Lịch sử 28 ngày gần nhất", 14f, TEXT, bold=true).apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(12) })
                        addView(HeatmapView(context, heat).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(70)) })
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(16)
                    })

                    // SM-2 info
                    container.addView(surface {
                        addView(tv("Về thuật toán SM-2", 15f, ACCENT, bold=true).apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(10) })
                        listOf(
                            "Hệ số EF (Ease Factor): bắt đầu 2.5, tự điều chỉnh theo độ khó.",
                            "Đánh giá Quên → ôn lại ngay hôm nay.",
                            "Đánh giá Dễ → khoảng cách tăng dần theo EF.",
                            "Thẻ được học xen kẽ ngẫu nhiên để tránh hiệu ứng vị trí."
                        ).forEach { tip ->
                            addView(row {
                                addView(tv("•", 14f, ACCENT).apply { setPadding(0,0,dp(8),0) })
                                addView(tv(tip, 13f, MUTED).apply { lineSpacingMultiplier = 1.5f; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
                                (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(6)
                            })
                        }
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(16)
                    })

                    // Reminder
                    container.addView(surface {
                        addView(row {
                            addView(col {
                                addView(tv("Nhắc nhở hàng ngày", 14f, TEXT, bold=true))
                                addView(tv("Thông báo lúc 8:00 sáng", 12f, MUTED))
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            })
                            addView(pill("  Bật  ", ACCENT, ACCENT_DIM) { ReminderWorker.schedule(this@MainActivity); toast("✓ Đã bật nhắc nhở!") }.apply {
                                (layoutParams as LinearLayout.LayoutParams).apply { marginStart = dp(12); height = dp(38) }
                            })
                            gravity = Gravity.CENTER_VERTICAL
                        })
                    })
                }
            }
        }
        show(page)
    }
}

// ============================================================
// THÊM VÀO app/build.gradle:
// ============================================================
//
//  plugins (đầu file):
//    id 'com.google.devtools.ksp' version '1.9.22-1.0.17'
//
//  dependencies {
//    implementation 'androidx.room:room-runtime:2.6.1'
//    implementation 'androidx.room:room-ktx:2.6.1'
//    ksp 'androidx.room:room-compiler:2.6.1'
//    implementation 'androidx.lifecycle:lifecycle-livedata-ktx:2.7.0'
//    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0'
//    implementation 'androidx.work:work-runtime-ktx:2.9.0'
//    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
//  }
//
//  settings.gradle - thêm vào plugins block:
//    id 'com.google.devtools.ksp' version '1.9.22-1.0.17' apply false
//
// ============================================================