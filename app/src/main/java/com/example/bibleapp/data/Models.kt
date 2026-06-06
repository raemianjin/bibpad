package com.example.bibleapp.data

/** 성경 한 권 */
data class Book(
    val index: Int,        // 1..66
    val nameEn: String,
    val nameKo: String,
    val chapters: List<List<String>>  // chapters[ch][verse]
) {
    val chapterCount: Int get() = chapters.size
    val isOldTestament: Boolean get() = index <= 39
}


/** 북마크된 구절 */
data class Bookmark(
    val bookIndex: Int,
    val bookNameKo: String,
    val chapter: Int,       // 1-based
    val verse: Int,         // 1-based
    val text: String
) {
    val reference: String get() = "$bookNameKo $chapter:$verse"
    val key: String get() = "$bookIndex-$chapter-$verse"
}

/** 구절 검색 결과 */
data class SearchResult(
    val bookIndex: Int,
    val bookName: String,
    val chapter: Int,
    val verse: Int,
    val text: String
)

/** 구절 메모 */
data class VerseNote(
    val bookIndex: Int,
    val bookNameKo: String,
    val chapter: Int,       // 1-based
    val verse: Int,         // 1-based
    val text: String,
    val createdAt: Long,    // epoch ms
    val updatedAt: Long,    // epoch ms
    val photoPaths: List<String> = emptyList()  // filesDir 내 경로
) {
    val key: String get() = "$bookIndex-$chapter-$verse"
    val reference: String get() = "$bookNameKo $chapter:$verse"
}
