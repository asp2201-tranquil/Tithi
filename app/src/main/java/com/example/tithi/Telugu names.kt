package com.example.tithi

/**
 * Maps English names scraped from prokerala to Telugu script.
 * Matching still happens on the English string; only display uses Telugu.
 */
object TeluguNames {

    private val tithi = mapOf(
        "pratipada"    to "పాడ్యమి",
        "padyami"      to "పాడ్యమి",
        "dwitiya"      to "విదియ",
        "vidiya"       to "విదియ",
        "tritiya"      to "తదియ",
        "thadiya"      to "తదియ",
        "chaturthi"    to "చవితి",
        "chavithi"     to "చవితి",
        "panchami"     to "పంచమి",
        "shashti"      to "షష్ఠి",
        "sashti"       to "షష్ఠి",
        "saptami"      to "సప్తమి",
        "ashtami"      to "అష్టమి",
        "navami"       to "నవమి",
        "dashami"      to "దశమి",
        "dasami"       to "దశమి",
        "ekadashi"     to "ఏకాదశి",
        "ekadasi"      to "ఏకాదశి",
        "dwadashi"     to "ద్వాదశి",
        "dvadasi"      to "ద్వాదశి",
        "trayodashi"   to "త్రయోదశి",
        "chaturdashi"  to "చతుర్దశి",
        "chaturdasi"   to "చతుర్దశి",
        "purnima"      to "పౌర్ణమి",
        "paurnami"     to "పౌర్ణమి",
        "amavasya"     to "అమావాస్య"
    )

    private val masa = mapOf(
        "chaitra"      to "చైత్రం",
        "vaishakha"    to "వైశాఖం",
        "vaisakha"     to "వైశాఖం",
        "jyeshtha"     to "జ్యేష్ఠం",
        "jyeshta"      to "జ్యేష్ఠం",
        "ashada"       to "ఆషాఢం",
        "ashadha"      to "ఆషాఢం",
        "shravana"     to "శ్రావణం",
        "sravana"      to "శ్రావణం",
        "bhadrapada"   to "భాద్రపదం",
        "bhadrapad"    to "భాద్రపదం",
        "ashvina"      to "ఆశ్వయుజం",
        "ashwina"      to "ఆశ్వయుజం",
        "ashwayuja"    to "ఆశ్వయుజం",
        "kartika"      to "కార్తీకం",
        "kartik"       to "కార్తీకం",
        "margashira"   to "మార్గశిరం",
        "margashirsha" to "మార్గశిరం",
        "pushya"       to "పుష్యం",
        "pausha"       to "పుష్యం",
        "magha"        to "మాఘం",
        "phalguna"     to "ఫాల్గుణం",
        "phalgun"      to "ఫాల్గుణం"
    )

    /** "Sukla Paksha Chavithi" → "శుక్ల పక్షం చవితి" */
    fun tithiToTelugu(english: String): String {
        val trimmed = english.trim()
        val lower = trimmed.lowercase()

        val paksha = when {
            lower.startsWith("sukla") || lower.startsWith("shukla") -> "శుక్ల పక్షం"
            lower.startsWith("krishna") -> "కృష్ణ పక్షం"
            else -> ""
        }

        val lastWord = lower.substringAfterLast(' ').replace(Regex("[^a-z]"), "")
        val tithiTelugu = tithi[lastWord] ?: return trimmed

        return if (paksha.isEmpty()) tithiTelugu else "$paksha $tithiTelugu"
    }

    /** "Kartika" → "కార్తీకం", "Adhika Ashada" → "అధిక ఆషాఢం" */
    fun masaToTelugu(english: String): String {
        val trimmed = english.trim()
        if (trimmed.isEmpty()) return ""

        val parts = trimmed.split(" ")
        val prefix = if (parts.size > 1 && parts[0].equals("Adhika", true)) "అధిక " else ""
        val last = parts.last().lowercase().replace(Regex("[^a-z]"), "")
        val name = masa[last] ?: return trimmed
        return "$prefix$name"
    }
}