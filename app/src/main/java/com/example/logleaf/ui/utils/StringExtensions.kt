package com.example.logleaf.ui.utils

import java.util.regex.Pattern

/**
 * 文字列の末尾にあるハッシュタグのブロックを削除します。
 * 例: "本文 #タグ1 #タグ2" -> "本文"
 * 例: "今日は #朝活 した" -> "今日は #朝活 した" (文中は削除しない)
 * 例: "#タグ のみ" -> "#タグ のみ" (文頭は削除しない)
 */
fun String.removeTrailingHashtags(): String {
    // パターン: (1つ以上の空白 + #で始まる単語)が1回以上続き、それが文字列の末尾にある
    val regex = """((?:\s*#\S+)+)$""".toRegex()
    return this.replace(regex, "").trim()
}