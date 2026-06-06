package com.example.bibleapp.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object NoteStore {

    private const val PREF = "note_store"
    private const val KEY_NOTES = "notes"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ─── CRUD ───
    fun all(ctx: Context): MutableList<VerseNote> {
        val raw = prefs(ctx).getString(KEY_NOTES, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return ArrayList<VerseNote>(arr.length()).also { list ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val photos = ArrayList<String>()
                val pa = o.optJSONArray("photos")
                if (pa != null) for (j in 0 until pa.length()) photos.add(pa.getString(j))
                list.add(VerseNote(
                    o.getInt("bookIndex"), o.getString("bookNameKo"),
                    o.getInt("chapter"), o.getInt("verse"),
                    o.getString("text"), o.getLong("createdAt"), o.getLong("updatedAt"),
                    photos
                ))
            }
        }
    }

    private fun persist(ctx: Context, list: List<VerseNote>) {
        val arr = JSONArray()
        for (n in list) arr.put(JSONObject().apply {
            put("bookIndex", n.bookIndex); put("bookNameKo", n.bookNameKo)
            put("chapter", n.chapter); put("verse", n.verse); put("text", n.text)
            put("createdAt", n.createdAt); put("updatedAt", n.updatedAt)
            val pa = JSONArray(); n.photoPaths.forEach { pa.put(it) }; put("photos", pa)
        })
        prefs(ctx).edit().putString(KEY_NOTES, arr.toString()).apply()
    }

    fun get(ctx: Context, key: String): VerseNote? = all(ctx).find { it.key == key }

    fun save(ctx: Context, note: VerseNote) {
        val list = all(ctx)
        val idx = list.indexOfFirst { it.key == note.key }
        if (idx >= 0) list[idx] = note else list.add(0, note)
        persist(ctx, list)
    }

    fun delete(ctx: Context, key: String) {
        val list = all(ctx)
        val note = list.find { it.key == key } ?: return
        // 사진 파일도 삭제
        note.photoPaths.forEach { File(it).delete() }
        persist(ctx, list.filter { it.key != key })
    }

    fun deleteMultiple(ctx: Context, keys: Set<String>) {
        val list = all(ctx)
        keys.forEach { k -> list.find { it.key == k }?.photoPaths?.forEach { File(it).delete() } }
        persist(ctx, list.filter { it.key !in keys })
    }

    // ─── 사진 경로 생성 ───
    fun photoDir(ctx: Context): File =
        File(ctx.filesDir, "memo_photos").also { it.mkdirs() }

    fun newPhotoFile(ctx: Context, noteKey: String): File {
        val safe = noteKey.replace("-", "_")
        return File(photoDir(ctx), "${safe}_${System.currentTimeMillis()}.jpg")
    }

    // ─── 통계 ───
    fun totalBytes(note: VerseNote): Long =
        note.photoPaths.sumOf { File(it).length() } + note.text.length * 2L

    fun hasNotes(ctx: Context, keys: Set<String>): Set<String> {
        val all = all(ctx).map { it.key }.toSet()
        return keys.intersect(all)
    }

    /** 사진을 시계방향 90도 회전하여 같은 파일에 다시 저장 */
    fun rotatePhoto(path: String): Boolean {
        return try {
            val src = android.graphics.BitmapFactory.decodeFile(path) ?: return false
            val matrix = android.graphics.Matrix().apply { postRotate(90f) }
            val rotated = android.graphics.Bitmap.createBitmap(
                src, 0, 0, src.width, src.height, matrix, true)
            java.io.FileOutputStream(File(path)).use {
                rotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it)
            }
            if (rotated != src) src.recycle()
            true
        } catch (e: Exception) {
            false
        }
    }
}
