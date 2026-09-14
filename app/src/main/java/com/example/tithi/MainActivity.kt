package com.example.tithi

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale
import java.util.concurrent.TimeUnit

data class TithiPeriod(
    val name: String,
    val teluguName: String,
    val start: LocalDateTime,
    val end: LocalDateTime
)

data class PanchangamDay(
    val date: LocalDate,
    val masa: String,
    val sunrise: LocalTime?,
    val tithis: List<TithiPeriod>
)

class MainActivity : AppCompatActivity() {

    private lateinit var dobButton: Button
    private lateinit var timeButton: Button
    private lateinit var yearInput: EditText
    private lateinit var findButton: Button
    private lateinit var debugButton: Button
    private lateinit var resultText: TextView
    private lateinit var birthTithiText: TextView

    private var dob: LocalDate? = null
    private var tob: LocalTime? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val cache = mutableMapOf<String, PanchangamDay>()
    private val rawCache = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dobButton = findViewById(R.id.dobButton)
        timeButton = findViewById(R.id.timeButton)
        yearInput = findViewById(R.id.yearInput)
        findButton = findViewById(R.id.findButton)
        debugButton = findViewById(R.id.debugButton)
        resultText = findViewById(R.id.resultText)
        birthTithiText = findViewById(R.id.birthTithiText)

        dobButton.setOnClickListener { pickDate() }
        timeButton.setOnClickListener { pickTime() }
        findButton.setOnClickListener { onFindClicked() }
        debugButton.setOnClickListener { onDebugClicked() }
    }

    private fun pickDate() {
        val now = LocalDate.now()
        DatePickerDialog(
            this,
            { _, y, m, d ->
                dob = LocalDate.of(y, m + 1, d)
                dobButton.text = dob.toString()
                maybeLoadBirthTithi()
            },
            now.year, now.monthValue - 1, now.dayOfMonth
        ).show()
    }

    private fun pickTime() {
        TimePickerDialog(
            this,
            { _, h, mi ->
                tob = LocalTime.of(h, mi)
                timeButton.text = String.format(Locale.US, "%02d:%02d", h, mi)
                maybeLoadBirthTithi()
            },
            12, 0, true
        ).show()
    }

    private fun maybeLoadBirthTithi() {
        val d = dob ?: return
        val t = tob ?: return
        birthTithiText.text = "Loading..."
        lifecycleScope.launch {
            try {
                val day = loadDay(d)
                val moment = LocalDateTime.of(d, t)
                val tithi = day.tithis.firstOrNull {
                    !moment.isBefore(it.start) && moment.isBefore(it.end)
                } ?: day.tithis.firstOrNull()

                val masaTelugu = TeluguNames.masaToTelugu(day.masa).ifEmpty { day.masa }
                val tithiTelugu = tithi?.let { TeluguNames.tithiToTelugu(it.name) } ?: "—"

                birthTithiText.text = "$masaTelugu • $tithiTelugu\n" +
                        "(English: ${day.masa} / ${tithi?.name ?: "?"})"
            } catch (e: Exception) {
                birthTithiText.text = "Could not load birth tithi:\n${e.message}"
            }
        }
    }

    private fun onDebugClicked() {
        val d = dob ?: run {
            resultText.text = "Pick a date first."
            return
        }
        resultText.text = "Fetching raw page..."
        lifecycleScope.launch {
            try {
                val html = fetchRaw(d)
                val text = Jsoup.parse(html).select("body").text()
                    .replace(Regex("\\s+"), " ")
                    .trim()
                resultText.text = "RAW TEXT for $d:\n\n$text"
            } catch (e: Exception) {
                resultText.text = "Error: ${e.message}"
            }
        }
    }

    private fun onFindClicked() {
        val d = dob
        val t = tob
        val year = yearInput.text.toString().toIntOrNull()

        if (d == null || t == null || year == null) {
            resultText.text = "Please pick date, time, and target year."
            return
        }
        if (year !in 1900..2050) {
            resultText.text = "Target year must be between 1900 and 2050."
            return
        }

        findButton.isEnabled = false
        resultText.text = "Searching..."

        lifecycleScope.launch {
            try {
                val result = findBirthday(d, t, year)
                resultText.text = result
            } catch (e: Exception) {
                Log.e("Tithi", "Error", e)
                resultText.text = "Error: ${e.message}"
            } finally {
                findButton.isEnabled = true
            }
        }
    }

    private suspend fun findBirthday(
        dob: LocalDate,
        tob: LocalTime,
        targetYear: Int
    ): String {
        // 1. birth tithi at exact time
        val birthDay = loadDay(dob)
        val birthMoment = LocalDateTime.of(dob, tob)

        val birthTithi = birthDay.tithis.firstOrNull {
            !birthMoment.isBefore(it.start) && birthMoment.isBefore(it.end)
        }?.name ?: birthDay.tithis.firstOrNull()?.name

        if (birthTithi == null) {
            return "Could not determine birth tithi.\n" +
                    "Tap Debug and send me the output."
        }
        val birthMasa = birthDay.masa
        val birthMasaTelugu = TeluguNames.masaToTelugu(birthMasa)
        val birthTithiTelugu = TeluguNames.tithiToTelugu(birthTithi)

        // 2. scan target year at sunrise
        val start = LocalDate.of(targetYear, 1, 1)
        val end = LocalDate.of(targetYear, 12, 31)
        var d = start
        var scanned = 0
        val matches = mutableListOf<Pair<LocalDate, PanchangamDay>>()

        while (!d.isAfter(end)) {
            val day = try {
                loadDay(d)
            } catch (e: Exception) {
                Log.w("Tithi", "skip $d: ${e.message}")
                null
            }
            if (day != null && day.masa == birthMasa) {
                val sunriseTithi = day.tithis.firstOrNull()?.name
                if (sunriseTithi == birthTithi) matches.add(d to day)
            }
            d = d.plusDays(1)
            scanned++
            if (scanned % 20 == 0) {
                withContext(Dispatchers.Main) {
                    resultText.text = "Scanning $targetYear: $scanned/365 done, ${matches.size} match(es)."
                }
            }
            delay(80)
        }

        if (matches.isEmpty()) {
            return "Birth masa: $birthMasaTelugu ($birthMasa)\n" +
                    "Birth tithi: $birthTithiTelugu ($birthTithi)\n\n" +
                    "No day in $targetYear had this masa + tithi at sunrise."
        }

        val lines = matches.joinToString("\n") { (date, day) ->
            val sunriseText = day.sunrise?.toString() ?: "?"
            val tithiTel = day.tithis.firstOrNull()?.teluguName ?: ""
            "$date   •   sunrise $sunriseText   •   $tithiTel"
        }
        return "Birth masa: $birthMasaTelugu ($birthMasa)\n" +
                "Birth tithi: $birthTithiTelugu ($birthTithi)\n\n" +
                "Match(es) in $targetYear:\n$lines"
    }

    private suspend fun loadDay(date: LocalDate): PanchangamDay {
        val key = date.toString()
        cache[key]?.let { return it }
        val html = fetchRaw(date)
        val parsed = parseDay(html, date)
        cache[key] = parsed
        return parsed
    }

    // ---------- HTTP ----------
    private suspend fun fetchRaw(date: LocalDate): String =
        withContext(Dispatchers.IO) {
            val key = date.toString()
            rawCache[key]?.let { return@withContext it }

            val month = monthName(date.monthValue)
            val day = date.dayOfMonth.toString().padStart(2, '0')
            val url = "https://www.prokerala.com/astrology/telugu-panchangam/" +
                    "${date.year}-$month-$day.html"

            val req = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
                )
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val html = client.newCall(req).execute().use { it.body?.string() ?: "" }
            rawCache[key] = html
            html
        }

    private fun monthName(m: Int): String = listOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december"
    )[m - 1]

    // ---------- Parsing ----------
    private fun parseDay(html: String, date: LocalDate): PanchangamDay {
        val doc = Jsoup.parse(html)
        val bodyText = doc.select("body").text()
            .replace(Regex("\\s+"), " ")
            .trim()

        val sunrise = Regex(
            """Sunrise[\s:]*(\d{1,2}):(\d{2})\s*(AM|PM|am|pm)""",
            RegexOption.IGNORE_CASE
        ).find(bodyText)?.let {
            var h = it.groupValues[1].toInt()
            val mi = it.groupValues[2].toInt()
            val ap = it.groupValues[3].uppercase(Locale.US)
            if (ap == "PM" && h < 12) h += 12
            if (ap == "AM" && h == 12) h = 0
            runCatching { LocalTime.of(h, mi) }.getOrNull()
        }

        val masa = run {
            val amanta = Regex(
                """Amanta\s+Month[\s:]*([A-Za-z]+)""",
                RegexOption.IGNORE_CASE
            ).find(bodyText)?.groupValues?.get(1)
            val purnimanta = Regex(
                """Purnimanta\s+Month[\s:]*([A-Za-z]+)""",
                RegexOption.IGNORE_CASE
            ).find(bodyText)?.groupValues?.get(1)
            val anyMonth = Regex(
                """Month[\s:]+([A-Z][a-z]+)"""
            ).find(bodyText)?.groupValues?.get(1)
            amanta ?: purnimanta ?: anyMonth ?: ""
        }

        val tithis = parseTithis(bodyText, date)

        return PanchangamDay(date, masa, sunrise, tithis)
    }

    private fun parseTithis(text: String, pageDate: LocalDate): List<TithiPeriod> {
        val out = mutableListOf<TithiPeriod>()

        val nameRe = Regex(
            """(Sukla|Shukla|Krishna)\s+Paksha\s+([A-Za-z]+)""",
            RegexOption.IGNORE_CASE
        )
        val matches = nameRe.findAll(text).toList()

        for ((i, m) in matches.withIndex()) {
            val pakshaRaw = m.groupValues[1]
            val paksha = if (pakshaRaw.startsWith("K", true)) "Krishna" else "Sukla"
            val name = "$paksha Paksha ${m.groupValues[2]}"

            val from = m.range.last + 1
            val to = matches.getOrNull(i + 1)?.range?.first
                ?: (from + 200).coerceAtMost(text.length)
            val chunk = text.substring(from, to)

            val times = extractTimes(chunk)
            if (times.isNotEmpty()) {
                val start = times[0]
                val end = if (times.size >= 2) times[1] else start.plusHours(24)
                val startDt = LocalDateTime.of(pageDate, start)
                var endDt = LocalDateTime.of(pageDate, end)
                if (!endDt.isAfter(startDt)) endDt = endDt.plusDays(1)
                out.add(
                    TithiPeriod(name, TeluguNames.tithiToTelugu(name), startDt, endDt)
                )
            }
        }

        if (out.isEmpty()) {
            val first = matches.firstOrNull()
            if (first != null) {
                val name = "Sukla Paksha ${first.groupValues[2]}"
                out.add(
                    TithiPeriod(
                        name,
                        TeluguNames.tithiToTelugu(name),
                        LocalDateTime.of(pageDate, LocalTime.MIDNIGHT),
                        LocalDateTime.of(pageDate, LocalTime.MAX)
                    )
                )
            }
        }

        return out
    }

    private fun extractTimes(s: String): List<LocalTime> {
        val re = Regex("""(\d{1,2}):(\d{2})\s*(AM|PM|am|pm)""")
        return re.findAll(s).mapNotNull {
            var h = it.groupValues[1].toInt()
            val mi = it.groupValues[2].toInt()
            val ap = it.groupValues[3].uppercase(Locale.US)
            if (ap == "PM" && h < 12) h += 12
            if (ap == "AM" && h == 12) h = 0
            runCatching { LocalTime.of(h, mi) }.getOrNull()
        }.toList()
    }
}