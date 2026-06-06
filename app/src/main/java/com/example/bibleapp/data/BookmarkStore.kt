package com.example.bibleapp.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object BookmarkStore {
    private const val PREF               = "bookmark_store"
    private const val KEY_BM             = "bookmarks"
    private const val KEY_FONT           = "font_size"
    private const val KEY_LINE           = "line_spacing"
    private const val KEY_LAST_BOOK      = "last_book"
    private const val KEY_LAST_CHAPTER   = "last_chapter"
    private const val KEY_LAST_VERSE     = "last_verse"
    private const val KEY_SHOW_PARALLEL  = "show_parallel"
    private const val KEY_KO_PRIMARY     = "ko_primary"
    private const val KEY_BULLETIN_URL   = "bulletin_url"
    private const val KEY_SEARCH_HISTORY = "search_history"
    private const val DEFAULT_BULLETIN   = "https://www.sarang.org/info/sun_bulletin.asp"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ─── 북마크 ───
    fun all(ctx: Context): MutableList<Bookmark> {
        val raw = prefs(ctx).getString(KEY_BM, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return ArrayList<Bookmark>(arr.length()).also { list ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(Bookmark(o.getInt("bookIndex"), o.getString("bookNameKo"),
                    o.getInt("chapter"), o.getInt("verse"), o.getString("text")))
            }
        }
    }
    private fun persist(ctx: Context, list: List<Bookmark>) {
        val arr = JSONArray()
        for (b in list) arr.put(JSONObject().apply {
            put("bookIndex", b.bookIndex); put("bookNameKo", b.bookNameKo)
            put("chapter", b.chapter); put("verse", b.verse); put("text", b.text)
        })
        prefs(ctx).edit().putString(KEY_BM, arr.toString()).apply()
    }
    fun isBookmarked(ctx: Context, key: String) = all(ctx).any { it.key == key }
    fun toggle(ctx: Context, bm: Bookmark): Boolean {
        val list = all(ctx)
        val ex = list.find { it.key == bm.key }
        return if (ex != null) { list.remove(ex); persist(ctx, list); false }
        else { list.add(0, bm); persist(ctx, list); true }
    }
    fun remove(ctx: Context, key: String) { persist(ctx, all(ctx).filter { it.key != key }) }

    // ─── 읽기 설정 ───
    fun getFontSize(ctx: Context)    = prefs(ctx).getFloat(KEY_FONT, 17f)
    fun setFontSize(ctx: Context, v: Float) = prefs(ctx).edit().putFloat(KEY_FONT, v).apply()
    fun getLineSpacing(ctx: Context) = prefs(ctx).getFloat(KEY_LINE, 6f)
    fun setLineSpacing(ctx: Context, v: Float) = prefs(ctx).edit().putFloat(KEY_LINE, v).apply()

    // ─── 마지막 읽기 위치 (book + chapter + verse) ───
    fun saveLastPosition(ctx: Context, bookIndex: Int, chapter: Int) =
        prefs(ctx).edit().putInt(KEY_LAST_BOOK, bookIndex).putInt(KEY_LAST_CHAPTER, chapter).apply()
    fun saveLastVerse(ctx: Context, verse: Int) =
        prefs(ctx).edit().putInt(KEY_LAST_VERSE, verse).apply()
    fun getLastBook(ctx: Context)    = prefs(ctx).getInt(KEY_LAST_BOOK, 0)
    fun getLastChapter(ctx: Context) = prefs(ctx).getInt(KEY_LAST_CHAPTER, 0)
    fun getLastVerse(ctx: Context)   = prefs(ctx).getInt(KEY_LAST_VERSE, -1)

    // ─── 병행 설정 ───
    fun getShowParallel(ctx: Context) = prefs(ctx).getBoolean(KEY_SHOW_PARALLEL, false)
    fun setShowParallel(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_SHOW_PARALLEL, v).apply()
    fun getKoPrimary(ctx: Context) = prefs(ctx).getBoolean(KEY_KO_PRIMARY, true)
    fun setKoPrimary(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_KO_PRIMARY, v).apply()

    // ─── 주보 URL ───
    fun getBulletinUrl(ctx: Context): String =
        prefs(ctx).getString(KEY_BULLETIN_URL, DEFAULT_BULLETIN) ?: DEFAULT_BULLETIN
    fun setBulletinUrl(ctx: Context, url: String) =
        prefs(ctx).edit().putString(KEY_BULLETIN_URL, url).apply()

    // ─── 검색 기록 (최대 5개) ───
    fun getSearchHistory(ctx: Context): List<String> {
        val raw = prefs(ctx).getString(KEY_SEARCH_HISTORY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { arr.getString(it) }
    }
    fun addSearchHistory(ctx: Context, query: String) {
        if (query.isBlank()) return
        val list = getSearchHistory(ctx).toMutableList()
        list.remove(query); list.add(0, query)
        val arr = JSONArray(); list.take(5).forEach { arr.put(it) }
        prefs(ctx).edit().putString(KEY_SEARCH_HISTORY, arr.toString()).apply()
    }
    fun removeSearchHistory(ctx: Context, query: String) {
        val list = getSearchHistory(ctx).filter { it != query }
        val arr = JSONArray(); list.forEach { arr.put(it) }
        prefs(ctx).edit().putString(KEY_SEARCH_HISTORY, arr.toString()).apply()
    }
    fun clearSearchHistory(ctx: Context) =
        prefs(ctx).edit().putString(KEY_SEARCH_HISTORY, "[]").apply()

    // ─── 메모 장표 기본 템플릿 (NoteTemplate.ordinal) ───
    private const val KEY_NOTE_TEMPLATE = "note_template"
    fun getNoteTemplate(ctx: Context) = prefs(ctx).getInt(KEY_NOTE_TEMPLATE, 0)
    fun setNoteTemplate(ctx: Context, ordinal: Int) =
        prefs(ctx).edit().putInt(KEY_NOTE_TEMPLATE, ordinal).apply()
}
