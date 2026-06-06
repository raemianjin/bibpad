package com.example.bibleapp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.example.bibleapp.R
import com.example.bibleapp.databinding.ItemVerseBinding

class VerseAdapter(
    private var fontSize: Float,
    private var lineSpacing: Float,
    private val reference: () -> String,   // "창세기 1장" 형태
    private val onLongClick: (verseIndex: Int) -> Unit,
    private val onBookmark: (verseIndex: Int) -> Unit,
    private val onNote: (verseIndex: Int) -> Unit,
    private var showKjv: Boolean = false,
    private var kjvVerses: List<String> = emptyList()
) : RecyclerView.Adapter<VerseAdapter.VH>() {

    private var verses: List<String> = emptyList()
    private var bookmarked: Set<Int> = emptySet()
    private var noted: Set<Int> = emptySet()

    fun submit(verses: List<String>, bookmarkedVerses: Set<Int> = emptySet(), notedVerses: Set<Int> = emptySet()) {
        this.verses = verses
        this.bookmarked = bookmarkedVerses
        this.noted = notedVerses
        notifyDataSetChanged()
    }

    fun setFontSize(size: Float) { fontSize = size; notifyDataSetChanged() }
    fun setLineSpacing(v: Float) { lineSpacing = v; notifyDataSetChanged() }
    fun setShowKjv(show: Boolean, kjv: List<String>) {
        showKjv = show; kjvVerses = kjv; notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemVerseBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = verses.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(position, verses[position])
    }

    inner class VH(private val b: ItemVerseBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(index: Int, text: String) {
            val verseNum = index + 1
            b.verseNum.text = verseNum.toString()
            b.verseText.text = text
            b.verseText.textSize = fontSize
            b.verseText.setLineSpacing(lineSpacing * b.root.context.resources.displayMetrics.density, 1f)

            if (showKjv && index < kjvVerses.size) {
                b.verseTextKjv.visibility = View.VISIBLE
                b.verseTextKjv.text = kjvVerses[index]
                b.verseTextKjv.textSize = (fontSize * 0.80f).coerceAtLeast(11f)
            } else {
                b.verseTextKjv.visibility = View.GONE
            }

            val ctx = b.root.context
            val isBm   = bookmarked.contains(verseNum)
            val hasNote = noted.contains(verseNum)

            b.verseRoot.setBackgroundColor(
                if (isBm) ctx.getColor(R.color.verse_highlight)
                else android.graphics.Color.TRANSPARENT
            )

            b.noteDot.visibility = if (hasNote) View.VISIBLE else View.GONE

            b.root.setOnClickListener { showMenu(ctx, index, text, isBm, hasNote) }
            b.root.setOnLongClickListener { onLongClick(index); true }
        }

        // 아이콘 없이 폰트/색상만으로 세련되게 구성한 커스텀 메뉴
        private fun showMenu(ctx: Context, index: Int, text: String, isBm: Boolean, hasNote: Boolean) {
            val ref      = "${reference()} ${index + 1}절"
            val fullText = "$ref\n$text"

            val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_verse_menu, null)
            view.findViewById<TextView>(R.id.menu_ref).text = ref

            val dialog = AlertDialog.Builder(ctx).setView(view).create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            view.findViewById<TextView>(R.id.menu_copy).setOnClickListener {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("verse", fullText))
                Toast.makeText(ctx, R.string.copied, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            view.findViewById<TextView>(R.id.menu_share).setOnClickListener {
                ctx.startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, fullText)
                    }, "공유"))
                dialog.dismiss()
            }
            val bmTv = view.findViewById<TextView>(R.id.menu_bookmark)
            bmTv.text = if (isBm) "북마크 제거" else "북마크 추가"
            bmTv.setOnClickListener { onBookmark(index); dialog.dismiss() }

            val noteTv = view.findViewById<TextView>(R.id.menu_note)
            noteTv.text = if (hasNote) "메모 보기 / 수정" else "메모 추가"
            noteTv.setOnClickListener { onNote(index); dialog.dismiss() }
            view.findViewById<TextView>(R.id.menu_search).setOnClickListener {
                val q = Uri.encode(fullText.take(100))
                ctx.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=$q")))
                dialog.dismiss()
            }

            dialog.show()
            // 하단에서 올라오는 시트 느낌
            dialog.window?.apply {
                setGravity(Gravity.BOTTOM)
                val lp = attributes
                lp.width = WindowManager.LayoutParams.MATCH_PARENT
                lp.y = (16 * ctx.resources.displayMetrics.density).toInt()
                attributes = lp
            }
        }
    }
}
