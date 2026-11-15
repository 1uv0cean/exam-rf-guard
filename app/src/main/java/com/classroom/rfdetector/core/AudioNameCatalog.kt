package com.classroom.rfdetector.core

/** Name-derived categories describe a candidate, never an authenticated product. */
enum class AudioKind(val label: String) {
    EARPHONES("이어폰 후보"), HEADPHONES("헤드폰 후보"), AUDIO("오디오 후보"),
}

fun audioEvidenceNote(audio: AudioNameMatch?, manufacturerIds: Set<Int>): String? {
    if (audio == null) return null
    val brands = buildSet {
        if (76 in manufacturerIds) addAll(listOf("Apple", "Beats"))
        if (117 in manufacturerIds) add("Samsung")
        if (224 in manufacturerIds || 398 in manufacturerIds) add("Google")
    }
    return if (brands.isNotEmpty() && audio.brand !in brands)
        "이름과 광고 회사의 브랜드 근거가 다릅니다. 상세 정보를 함께 확인하세요." else null
}

data class AudioNameMatch(val brand: String, val model: String, val kind: AudioKind, val brandAliases: List<String>) {
    val searchModel: String get() = model.replace("에어팟", "AirPods").replace("갤럭시", "Galaxy").replace("버즈", "Buds")
        .replace("프로", "Pro").replace("맥스", "Max").replace("라이브", "Live").replace("플러스", "Plus").replace("픽셀", "Pixel")
}

object AudioNameCatalog {
    private data class Rule(val brand: String, val aliases: List<String>, val kind: AudioKind, val expression: String) {
        val pattern = Regex("(?:(?<![\\p{L}\\p{N}])|(?<=의))(?:$expression)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    }
    private val generation = "(?:\\d+(?:\\s*(?:(?:st|nd|rd|th)?\\s*(?:generation|gen)|세대))?|Gen\\s*\\d+)"
    private val generationSuffix = "(?:\\s*$generation|\\s*\\(\\s*$generation\\s*\\))?"
    private val rules = listOf(
        Rule("Apple", listOf("애플", "AirPods", "에어팟"), AudioKind.HEADPHONES, "AirPods\\s+Max$generationSuffix|에어팟\\s*맥스$generationSuffix"),
        Rule("Apple", listOf("애플", "AirPods", "에어팟"), AudioKind.EARPHONES, "AirPods(?:\\s+Pro)?$generationSuffix|에어팟(?:\\s*프로)?$generationSuffix"),
        Rule("Samsung", listOf("삼성", "삼성전자", "Galaxy Buds", "갤럭시 버즈"), AudioKind.EARPHONES, "Galaxy\\s+Buds\\s*\\d*(?:\\s*(?:Pro|Live|FE|Core|Plus|\\+))?|갤럭시\\s*버즈\\s*\\d*(?:\\s*(?:프로|라이브|플러스|FE))?"),
        Rule("Sony", listOf("소니"), AudioKind.EARPHONES, "(?:WF|WI)-[A-Z0-9]{3,16}"),
        Rule("Sony", listOf("소니"), AudioKind.HEADPHONES, "WH-[A-Z0-9]{3,16}"),
        Rule("Bose", listOf("보스"), AudioKind.EARPHONES, "(?:Bose\\s+)?QuietComfort(?:\\s+Ultra)?\\s+Earbuds(?:\\s+(?:II|2))?|(?:Bose\\s+)?SoundSport(?:\\s+(?:Wireless|Free))?"),
        Rule("Bose", listOf("보스"), AudioKind.HEADPHONES, "(?:Bose\\s+)?QuietComfort\\s+(?:Ultra\\s+Headphones|35(?:\\s+II)?|45)"),
        Rule("Bose", listOf("보스"), AudioKind.AUDIO, "(?:Bose\\s+)?QuietComfort(?:\\s+Ultra)?"),
        Rule("Google", listOf("구글"), AudioKind.EARPHONES, "Pixel\\s+Buds(?:\\s+(?:Pro(?:\\s*\\d+)?|A-Series|\\d+a?))?|픽셀\\s*버즈(?:\\s*프로)?(?:\\s*\\d+)?"),
        Rule("Jabra", listOf("자브라"), AudioKind.EARPHONES, "Jabra\\s+Elite\\s+(?:Active\\s+)?\\d+[A-Z]?(?:\\s+(?:Active|Gen\\s*2))?"),
        Rule("Beats", listOf("비츠", "Apple", "애플"), AudioKind.EARPHONES, "Beats\\s+(?:Fit\\s+Pro|Studio\\s+Buds(?:\\s*\\+)?|Solo\\s+Buds)|Powerbeats(?:\\s+Pro)?(?:\\s+\\d+)?"),
        Rule("Beats", listOf("비츠", "Apple", "애플"), AudioKind.HEADPHONES, "Beats\\s+(?:Solo\\s*\\d+|Studio(?:\\s*\\d+|\\s+Pro))"),
        Rule("Nothing", listOf("낫싱"), AudioKind.EARPHONES, "Nothing\\s+Ear(?:\\s*\\((?:a|\\d+)\\))?"),
        Rule("soundcore", listOf("사운드코어", "Anker", "앤커"), AudioKind.EARPHONES, "soundcore\\s+(?:Liberty\\s+\\d+(?:\\s+(?:Pro|NC))?|Space\\s+A40)"),
        Rule("Sennheiser", listOf("젠하이저"), AudioKind.EARPHONES, "(?:Sennheiser\\s+)?MOMENTUM\\s+True\\s+Wireless(?:\\s+\\d+)?"),
    )

    fun match(name: String?): AudioNameMatch? {
        if (name.isNullOrBlank()) return null
        val normalized = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFKC)
        return rules.firstNotNullOfOrNull { rule -> rule.pattern.find(normalized)?.let { result ->
            AudioNameMatch(rule.brand, result.value.trim(), rule.kind, rule.aliases)
        } }
    }
}

internal fun normalizedSearch(value: String): String = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
    .lowercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() }

internal fun fieldsMatchQuery(query: String, fields: List<String>): Boolean {
    if (query.isBlank()) return true
    val terms = query.trim().split(Regex("\\s+")).map(::normalizedSearch).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return false
    val normalizedFields = fields.map(::normalizedSearch)
    return terms.all { term -> normalizedFields.any { it.contains(term) } }
}
