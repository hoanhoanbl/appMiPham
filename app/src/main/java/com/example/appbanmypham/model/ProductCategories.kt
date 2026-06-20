package com.example.appbanmypham.model

object ProductCategories {
    val VALUES = listOf(
        "Tẩy trang",
        "Sữa rửa mặt",
        "Toner",
        "Serum",
        "Kem dưỡng",
        "Kem chống nắng",
        "Mặt nạ",
        "Trị mụn",
        "Tẩy tế bào chết",
        "Chống lão hóa",
        "Dưỡng mắt",
        "Dưỡng môi"
    )

    fun normalize(rawCategory: String, fallbackText: String = ""): String {
        val source = normalizeKey("$rawCategory $fallbackText")
        return when {
            source.contains("taytrang") || source.contains("cleansing") || source.contains("micellar") -> "Tẩy trang"
            source.contains("suaruamat") || source.contains("cleanser") || source.contains("facialwash") -> "Sữa rửa mặt"
            source.contains("toner") || source.contains("nuochoahong") -> "Toner"
            source.contains("serum") || source.contains("essence") || source.contains("ampoule") -> "Serum"
            source.contains("kemchongnang") || source.contains("chongnang") || source.contains("sunscreen") || source.contains("spf") -> "Kem chống nắng"
            source.contains("matna") || source.contains("mask") -> "Mặt nạ"
            source.contains("trimun") || source.contains("mun") || source.contains("acne") -> "Trị mụn"
            source.contains("taytebaochet") || source.contains("exfol") || source.contains("scrub") || source.contains("peeling") -> "Tẩy tế bào chết"
            source.contains("chonglaohoa") || source.contains("laohoa") || source.contains("antiaging") || source.contains("retinol") -> "Chống lão hóa"
            source.contains("duongmat") || source.contains("eyecare") || source.contains("eyecream") -> "Dưỡng mắt"
            source.contains("duongmoi") || source.contains("lip") -> "Dưỡng môi"
            source.contains("kemduong") || source.contains("moistur") || source.contains("cream") || source.contains("lotion") -> "Kem dưỡng"
            else -> VALUES.first()
        }
    }

    private fun normalizeKey(value: String): String {
        val lower = value.lowercase()
        val withoutAccent = buildString(lower.length) {
            lower.forEach { c ->
                append(
                    when (c) {
                        'à', 'á', 'ạ', 'ả', 'ã', 'â', 'ầ', 'ấ', 'ậ', 'ẩ', 'ẫ', 'ă', 'ằ', 'ắ', 'ặ', 'ẳ', 'ẵ' -> 'a'
                        'è', 'é', 'ẹ', 'ẻ', 'ẽ', 'ê', 'ề', 'ế', 'ệ', 'ể', 'ễ' -> 'e'
                        'ì', 'í', 'ị', 'ỉ', 'ĩ' -> 'i'
                        'ò', 'ó', 'ọ', 'ỏ', 'õ', 'ô', 'ồ', 'ố', 'ộ', 'ổ', 'ỗ', 'ơ', 'ờ', 'ớ', 'ợ', 'ở', 'ỡ' -> 'o'
                        'ù', 'ú', 'ụ', 'ủ', 'ũ', 'ư', 'ừ', 'ứ', 'ự', 'ử', 'ữ' -> 'u'
                        'ỳ', 'ý', 'ỵ', 'ỷ', 'ỹ' -> 'y'
                        'đ' -> 'd'
                        else -> c
                    }
                )
            }
        }
        return withoutAccent.filter { it.isLetterOrDigit() }
    }
}
