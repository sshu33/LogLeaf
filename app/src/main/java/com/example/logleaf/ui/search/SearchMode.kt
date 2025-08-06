package com.example.logleaf.ui.search

/**
 * 検索のモードを定義する。
 * - ALL: 本文とタグの両方を対象とする
 * - TEXT_ONLY: 本文のみを対象とする
 * - TAG_ONLY: タグのみを対象とする
 */
enum class SearchMode {
    ALL,
    TEXT_ONLY,
    TAG_ONLY
}