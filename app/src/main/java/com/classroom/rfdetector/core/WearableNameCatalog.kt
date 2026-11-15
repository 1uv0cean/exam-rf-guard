package com.classroom.rfdetector.core

/** A recognizable advertised name is a candidate, not proof of hardware or AI use. */
enum class WearableKind(val label: String) {
    SMART_GLASSES("스마트 안경 후보"), SMARTWATCH("스마트워치 후보"), CAMERA("카메라 후보"),
}

data class WearableNameMatch(
    val brand: String,
    val model: String,
    val kind: WearableKind,
    val aliases: List<String>,
)

object WearableNameCatalog {
    private data class Rule(val brand: String, val kind: WearableKind, val aliases: List<String>, val expression: String) {
        val pattern = Regex("(?:(?<![\\p{L}\\p{N}])|(?<=의))(?:$expression)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    }

    private val rules = listOf(
        Rule("Ray-Ban / Meta", WearableKind.SMART_GLASSES, listOf("레이밴", "레이뱅", "메타", "Ray Ban", "스마트 안경", "smart glasses"),
            "Meta\\s+Ray[- ]?Ban\\s+Display|Ray[- ]?Ban\\s+Meta(?:\\s+(?:Audio|Wayfarer|Headliner|Skyler))?(?:\\s*\\(?Gen\\s*\\d+\\)?)?|(?:레이밴|레이뱅)\\s*메타(?:\\s*\\d+세대)?"),
        Rule("Oakley / Meta", WearableKind.SMART_GLASSES, listOf("오클리", "메타", "스마트 안경", "smart glasses"),
            "Oakley\\s+Meta(?:\\s+(?:HSTN|Vanguard))?|오클리\\s*메타(?:\\s*(?:HSTN|뱅가드))?"),
        Rule("Apple", WearableKind.SMARTWATCH, listOf("애플", "스마트워치", "smartwatch"),
            "Apple\\s+Watch(?:\\s+(?:Series\\s*\\d+|Ultra(?:\\s*\\d+)?|SE(?:\\s*\\d+)?))?|애플\\s*워치(?:\\s*(?:시리즈\\s*\\d+|울트라(?:\\s*\\d+)?|SE(?:\\s*\\d+)?|\\d+))?"),
        Rule("Samsung", WearableKind.SMARTWATCH, listOf("삼성", "갤럭시", "스마트워치", "smartwatch"),
            "Galaxy\\s+Watch(?:\\s*(?:Ultra(?:\\s*\\d+)?|\\d+(?:\\s*(?:Classic|Pro))?|FE))?|갤럭시\\s*워치(?:\\s*(?:울트라(?:\\s*\\d+)?|\\d+(?:\\s*(?:클래식|프로))?|FE))?"),
        Rule("GoPro", WearableKind.CAMERA, listOf("고프로", "카메라", "camera"),
            "GoPro\\s+HERO\\s*\\d+(?:\\s+Black)?|고프로\\s*히어로\\s*\\d+(?:\\s*블랙)?"),
    )

    fun match(name: String?): WearableNameMatch? {
        if (name.isNullOrBlank()) return null
        val normalized = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFKC)
        return rules.firstNotNullOfOrNull { rule ->
            rule.pattern.find(normalized)?.let { WearableNameMatch(rule.brand, it.value.trim(), rule.kind, rule.aliases) }
        }
    }
}
