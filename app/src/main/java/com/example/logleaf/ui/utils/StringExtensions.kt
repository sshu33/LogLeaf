package com.example.logleaf.ui.utils

import java.util.regex.Pattern

/**
 * 文字列の末尾にあるハッシュタグのブロックを削除します。
 * 例: "本文 #タグ1 #タグ2" -> "本文"
 * 例: "今日は #朝活 した" -> "今日は #朝活 した" (文中は削除しない)
 * 例: "#タグ のみ" -> "#タグ のみ" (文頭は削除しない)
 */
fun String.removeTrailingHashtags(): String {
    val regex = """((?:\s*#\S+)+)$""".toRegex()
    val afterRemoval = this.replace(regex, "").trim()

    // もし、ハッシュタグを削除した結果が空文字列になるなら、
    // それは元々ハッシュタグのみの投稿だったということ。
    // その場合は、元の文字列をそのまま返す。
    return if (afterRemoval.isEmpty()) {
        this.trim() // 元の文字列の前後の空白だけは除去して返す
    } else {
        afterRemoval
    }
}