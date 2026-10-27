package com.example.buddy

sealed class Command {
    object Stop : Command()
    data class Alarm(val hour: Int, val minute: Int) : Command()
    data class Timer(val seconds: Int) : Command()
    data class OpenApp(val name: String) : Command()
    object Unknown : Command()
}

object CommandParser {

    // हिंदी और मराठी में शब्दों में बोले गए अंक
    private val numberWords = mapOf(
        "एक" to 1, "दो" to 2, "तीन" to 3, "चार" to 4, "पांच" to 5, "पाँच" to 5,
        "छह" to 6, "छः" to 6, "छे" to 6, "सहा" to 6, "सात" to 7, "आठ" to 8,
        "नौ" to 9, "नऊ" to 9, "दस" to 10, "दहा" to 10, "ग्यारह" to 11, "अकरा" to 11,
        "बारह" to 12, "बारा" to 12, "पंद्रह" to 15, "पंधरा" to 15, "बीस" to 20, "वीस" to 20,
        "तीस" to 30, "चालीस" to 40, "पचास" to 50,
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11,
        "twelve" to 12, "fifteen" to 15, "twenty" to 20, "thirty" to 30
    )

    private val alarmWords = listOf("अलार्म", "एलार्म", "अलार्म्स", "alarm", "गजर")
    private val timerWords = listOf("टाइमर", "टायमर", "timer")
    private val openWords = listOf("खोलो", "खोल", "उघड", "उघडा", "open", "launch")
    private val stopWords = listOf("रुक", "थांब", "stop", "बंद करो", "बंद कर")
    private val cancelWords = listOf("रुकवा", "रद्द", "हटा", "cancel")

    private fun findNumber(t: String): Int? {
        Regex("\\d{1,2}").find(t)?.let { return it.value.toInt() }
        // सबसे लंबे शब्द पहले जाँचें ताकि "पंद्रह" पर "दस" न पकड़ा जाए
        return numberWords.entries.sortedByDescending { it.key.length }
            .firstOrNull { t.contains(it.key) }?.value
    }

    fun parse(raw: String): Command {
        val t = raw.lowercase().trim()

        val cancel = cancelWords.any { t.contains(it) }
        if (!cancel && stopWords.any { t.contains(it) }) return Command.Stop

        if (alarmWords.any { t.contains(it) }) {
            parseAlarm(t)?.let { return it }
        }

        if (timerWords.any { t.contains(it) }) {
            val n = findNumber(t)
            if (n != null) {
                val secs = when {
                    t.contains("घंटा") || t.contains("घंटे") || t.contains("तास") || t.contains("hour") -> n * 3600
                    t.contains("सेकंड") || t.contains("सेकंद") || t.contains("second") -> n
                    else -> n * 60
                }
                return Command.Timer(secs)
            }
        }

        if (openWords.any { t.contains(it) }) {
            var name = t
            openWords.sortedByDescending { it.length }.forEach { name = name.replace(it, "") }
            val filler = setOf("ऐप", "एप", "app", "दो", "दे", "दीजिए", "कर", "करो", "जरा", "ज़रा",
                "प्लीज", "please", "को", "the", "मला", "माझा")
            name = name.split(" ").filter { it.isNotBlank() && it !in filler }.joinToString(" ").trim()
            if (name.isNotEmpty()) return Command.OpenApp(name)
        }

        return Command.Unknown
    }

    private fun parseAlarm(t: String): Command.Alarm? {
        // समय अंक में, जैसे 7:30 या 7.30
        val clock = Regex("(\\d{1,2})[:.](\\d{2})").find(t)
        var h: Int
        var m: Int
        if (clock != null) {
            h = clock.groupValues[1].toInt()
            m = clock.groupValues[2].toInt()
        } else {
            val base = findNumber(t) ?: return null
            h = base
            m = 0
            when {
                t.contains("साढ़े") || t.contains("साडे") || t.contains("साड़े") -> m = 30
                t.contains("सवा") -> m = 15
                t.contains("पौने") || t.contains("पावणे") -> { h = base - 1; m = 45 }
            }
        }
        val pm = t.contains("शाम") || t.contains("रात") || t.contains("दोपहर") ||
                t.contains("संध्याकाळ") || t.contains("pm") || t.contains("evening") || t.contains("night")
        if (pm && h < 12) h += 12
        return if (h in 0..23 && m in 0..59) Command.Alarm(h, m) else null
    }
}
