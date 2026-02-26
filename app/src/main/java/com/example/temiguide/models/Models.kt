package com.example.temiguide.models

// The detailed JSON structure we expect from OpenAI, now used for internal mapping
data class TemiActionResponse(
    val reply: String,
    val actions: List<com.example.temiguide.ActionQueue.ActionItem>? = null,
    // 後方互換: 古い形式もパースできるようにする
    val action: String? = null,
    val location: String? = null,
    val language: String? = "ja" // "ja", "en", "zh"
)

// ==================== Location Info ====================
val locationInfo = mapOf(
    "メンズトップス" to "メンズトップス",
    "メンズボトムス" to "メンズボトムス",
    "レディーストップス" to "レディーストップス",
    "レディースボトムス" to "レディースボトムス",
    "アウター" to "アウター",
    "インナー" to "インナー・肌着",
    "キッズベビー" to "キッズ・ベビー",
    "小物" to "小物・アクセサリー",
    "試着室" to "試着室",
    "レジ" to "レジ",
    "トイレ" to "お手洗い",
    "出口" to "出口"
)
