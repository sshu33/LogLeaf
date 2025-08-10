package com.example.logleaf.utils

import android.util.Log

/**
 * 投稿テキストからハッシュタグを自動抽出するユーティリティクラス
 */
object HashtagExtractor {

    // ハッシュタグ抽出用の正規表現（実用重視版）
    // #で始まり、6文字以内、ひらがなの助詞で自然に区切る
    private val hashtagPattern = """#([ぁ-んァ-ヶー一-龠a-zA-Z0-9_]{1,6})(?=[あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをん]|[、。！？\s]|$)""".toRegex()

    /**
     * 投稿テキストからハッシュタグを抽出します
     *
     * @param text 投稿のテキスト
     * @param maxTags 抽出する最大タグ数（デフォルト5個）
     * @return 抽出されたタグ名のリスト（#は除く、前から順、重複なし）
     */
    fun extractHashtags(text: String, maxTags: Int = 5): List<String> {
        if (text.isBlank()) return emptyList()

        // シンプルな抽出：スペースか句読点で区切られた#〇〇を抽出
        val simplePattern = """#([ぁ-んァ-ヶー一-龠a-zA-Z0-9_]+)""".toRegex()

        return simplePattern
            .findAll(text)
            .map { it.groupValues[1] }
            .map { cleanupTag(it) }  // タグをクリーンアップ
            .filter { it.length in 1..15 && it.isNotBlank() }  // 1-15文字のみ
            .distinct()
            .take(maxTags)
            .toList()
    }

    /**
     * 抽出したタグから不要な助詞を除去
     */
    private fun cleanupTag(tag: String): String {
        // よくある助詞パターンを除去（長いものから順番にチェック）
        val suffixesToRemove = listOf(
            "している", "されている", "させる", "される",
            "して", "した", "する", "され", "から", "まで",
            "では", "でも", "には", "にも", "との", "での", "でき"
        )

        var cleaned = tag
        for (suffix in suffixesToRemove) {
            if (cleaned.endsWith(suffix) && cleaned.length > suffix.length) {
                cleaned = cleaned.removeSuffix(suffix)
                break // 最初にマッチした助詞だけ除去
            }
        }

        return cleaned
    }

    /**
     * ハッシュタグ抽出のテスト用関数
     * 開発中の動作確認に使用
     */
    fun test() {
        val testCases = listOf(
            "今日は#散歩して#カフェでコーヒー飲んだ",
            "#朝活 #読書 #勉強 おはよう！",
            "文中に#混在している#テスト文章です",
            "#今日はとてもいい天気で散歩日和でした", // 長すぎるタグ
            "ハッシュタグなしの普通の投稿",
            "#散歩 #カフェ #読書 #コーヒー #朝活 #仕事", // 6個（5個に制限される）
            "#散歩した後に#カフェで#読書", // 助詞で区切られるケース
        )

        testCases.forEach { text ->
            val extracted = extractHashtags(text)
            Log.d("HashtagTest", "入力: $text")
            Log.d("HashtagTest", "抽出: $extracted")
            Log.d("HashtagTest", "---")
        }
    }
}