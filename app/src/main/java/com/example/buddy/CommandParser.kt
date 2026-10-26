package com.example.buddy

sealed class Command {
    object Stop : Command()
    data class Alarm(val hour: Int, val minute: Int) : Command()
    data class Timer(val seconds: Int) : Command()
    data class OpenApp(val name: String) : Command()
    object Unknown : Command()
}

object CommandParser {

    fun parse(raw: String): Command {
        val t = raw.lowercase().trim()

        val cancelWords = t.contains("रुकवा") || t.contains("रद्द") || t.contains("हटा")
        if (!cancelWords && (t.contains("रुक") || t.contains("stop") || t.contains("बंद करो"))) return Command.Stop

        val num = Regex("(\\d{1,2})(?:[:.](\\d{2}))?").find(t)

        if (t.contains("अलार्म") || t.contains("alarm")) {
            if (num != null) {
                var h = num.groupValues[1].toInt()
                val m = num.groupValues[2].ifEmpty { "0" }.toInt()
                val pm = t.contains("शाम") || t.contains("रात") || t.contains("दोपहर") || t.contains("pm")
                if (pm && h < 12) h += 12
                if (h in 0..23 && m in 0..59) return Command.Alarm(h, m)
            }
        }

        if (t.contains("टाइमर") || t.contains("timer")) {
            if (num != null) {
                val n = num.groupValues[1].toInt()
                val secs = when {
                    t.contains("घंटा") || t.contains("घंटे") || t.contains("hour") -> n * 3600
                    t.contains("सेकंड") || t.contains("second") -> n
                    else -> n * 60
                }
                return Command.Timer(secs)
            }
        }

        val openWords = listOf("खोलो", "खोल", "open")
        if (openWords.any { t.contains(it) }) {
            var name = t
            openWords.forEach { name = name.replace(it, "") }
            val filler = setOf("ऐप", "एप", "app", "दो", "दे", "दीजिए", "कर", "करो", "जरा", "ज़रा", "प्लीज", "please", "को")
            name = name.split(" ").filter { it.isNotBlank() && it !in filler }.joinToString(" ").trim()
            if (name.isNotEmpty()) return Command.OpenApp(name)
        }

        return Command.Unknown
    }
}
