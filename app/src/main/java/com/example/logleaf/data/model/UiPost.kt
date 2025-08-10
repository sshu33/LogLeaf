package com.example.logleaf.data.model

import com.example.logleaf.utils.removeTrailingHashtags

/**
 * UI表示に特化したPostデータクラス。
 * ViewModelでPostWithTagsAndImagesからこのクラスに変換してUIに渡す。
 */
data class UiPost(
    val postWithTagsAndImages: PostWithTagsAndImages,
    val displayText: String // 文末ハッシュタグを削除した、表示用のテキスト
) {
    // コンストラクタで、元のテキストから表示用テキストを自動生成する
    constructor(postWithTagsAndImages: PostWithTagsAndImages) : this(
        postWithTagsAndImages = postWithTagsAndImages,
        displayText = postWithTagsAndImages.post.text.removeTrailingHashtags()
    )
}