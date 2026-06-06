package com.example.bibleapp.ui

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bibleapp.R
import com.example.bibleapp.data.*
import com.example.bibleapp.databinding.ActivityReaderBinding
import com.example.bibleapp.util.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding
    private lateinit var book: Book
    private var chapter = 0
    private var targetVerse = -1

    private lateinit var verseAdapter: VerseAdapter
    private lateinit var gestureDetector: GestureDetector

    // 병행 표기 상태
    private var parallelOn = false
    private var koPrimary  = true   // true=KO메인, false=EN메인

    // 메모 다이얼로그 상태
    private var noteVerseIndex   = -1          // 현재 열린 메모 구절 (0-based)
    private var activeNoteDialog: AlertDialog? = null
    private var activePhotoContainer: LinearLayout? = null

    companion object {
        const val EXTRA_BOOK    = "book_index"
        const val EXTRA_CHAPTER = "chapter_index"
        const val EXTRA_VERSE   = "verse_index"   // 0-based
        private const val REQ_GALLERY = 902
    }

    // ════════════════════════════════════════════════════
    // onCreate
    // ════════════════════════════════════════════════════
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityReaderBinding.inflate(layoutInflater)
            setContentView(binding.root)

            if (!BibleRepository.isLoaded) BibleRepository.load(applicationContext)

            val bookIndex = intent.getIntExtra(EXTRA_BOOK, 1)
            targetVerse   = intent.getIntExtra(EXTRA_VERSE, -1)
            val found = BibleRepository.bookByIndex(bookIndex)
            if (found == null) { finish(); return }
            book    = found
            chapter = intent.getIntExtra(EXTRA_CHAPTER, 0).coerceIn(0, book.chapterCount - 1)

            // 뒤로가기
            binding.btnBack.setOnClickListener { finish() }

            // 병행 초기 상태 복원
            parallelOn = BookmarkStore.getShowParallel(this)
            koPrimary  = BookmarkStore.getKoPrimary(this)
            applyToggleUI()

            // ── 노랑 토글: 병행 ON/OFF
            binding.btnParallel.setOnClickListener {
                parallelOn = !parallelOn
                if (!parallelOn) {
                    // 병행 끄면 swap도 리셋
                    koPrimary = (BibleRepository.currentVersion == BibleRepository.Version.KRV)
                }
                BookmarkStore.setShowParallel(this, parallelOn)
                BookmarkStore.setKoPrimary(this, koPrimary)
                applyToggleUI()
                showChapter()
            }

            // ── 주황 토글: 메인/서브 전환 (병행 ON일 때만)
            binding.btnSwap.setOnClickListener {
                if (!parallelOn) return@setOnClickListener
                koPrimary = !koPrimary
                BookmarkStore.setKoPrimary(this, koPrimary)
                applyToggleUI()
                showChapter()
            }

            // ── 폰트 버튼
            binding.btnFont.setOnClickListener { showReadingSettings() }

            // ── VerseAdapter 초기화
            verseAdapter = VerseAdapter(
                fontSize    = BookmarkStore.getFontSize(this),
                lineSpacing = BookmarkStore.getLineSpacing(this),
                reference   = { "${book.nameKo} ${chapter + 1}장" },
                onLongClick = { toggleBookmark(it) },
                onBookmark  = { toggleBookmark(it) },
                onNote      = { openNoteDialog(it) }
            )
            binding.verseList.layoutManager = LinearLayoutManager(this)
            binding.verseList.adapter = verseAdapter

            // ── 태블릿(패드): 본문 줄 길이를 읽기 좋은 폭으로 제한하고 가운데 정렬
            if (com.example.bibleapp.util.DeviceUtil.isTablet(this)) {
                val dp = resources.displayMetrics.density
                val maxColPx = (720 * dp).toInt()
                val screenPx = resources.displayMetrics.widthPixels
                val side = ((screenPx - maxColPx) / 2).coerceAtLeast((16 * dp).toInt())
                binding.verseList.setPadding(
                    side, (8 * dp).toInt(), side, (80 * dp).toInt()
                )
                binding.verseList.clipToPadding = false
            }

            // ── 스와이프 제스처 (좌우 장 이동)
            gestureDetector = GestureDetector(this,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {
                        val dx = (e2.x - (e1?.x ?: e2.x))
                        val dy = (e2.y - (e1?.y ?: e2.y))
                        if (abs(dx) > abs(dy) * 1.2f && abs(dx) > 80 && abs(vX) > 200) {
                            if (dx < 0) moveChapterAnim(chapter + 1, true)
                            else        moveChapterAnim(chapter - 1, false)
                            return true
                        }
                        return false
                    }
                })
            binding.verseList.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    gestureDetector.onTouchEvent(e); return false
                }
            })

            // ── 스크롤이 멈출 때마다 화면 최상단 절을 마지막 위치로 저장
            binding.verseList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        val lm = rv.layoutManager as? LinearLayoutManager ?: return
                        val first = lm.findFirstVisibleItemPosition()
                        if (first >= 0) {
                            BookmarkStore.saveLastPosition(this@ReaderActivity, book.index, chapter)
                            BookmarkStore.saveLastVerse(this@ReaderActivity, first)
                        }
                    }
                }
            })

            binding.btnPrev.setOnClickListener { moveChapterAnim(chapter - 1, false) }
            binding.btnNext.setOnClickListener { moveChapterAnim(chapter + 1, true) }

            buildChapterBar()
            showChapter(scrollToVerse = targetVerse)

        } catch (e: Exception) {
            AppLogger.e("Reader", "onCreate: ${e.message}")
            finish()
        }
    }

    // ════════════════════════════════════════════════════
    // 토글 UI 적용
    // ════════════════════════════════════════════════════
    private fun applyToggleUI() {
        if (parallelOn) {
            // 노랑 활성
            binding.btnParallel.setBackgroundResource(R.drawable.bg_toggle_yellow)
            binding.btnParallel.setTextColor(0xFF222222.toInt())
            // 주황: koPrimary 여부에 따라
            binding.btnSwap.setBackgroundResource(R.drawable.bg_toggle_orange)
            binding.btnSwap.setTextColor(0xFFFFFFFF.toInt())
            binding.btnSwap.alpha = 1f
            binding.btnSwap.isClickable = true
        } else {
            // 노랑 비활성
            binding.btnParallel.setBackgroundResource(R.drawable.bg_toggle_off)
            binding.btnParallel.setTextColor(0xCCFFFFFF.toInt())
            // 주황 비활성
            binding.btnSwap.setBackgroundResource(R.drawable.bg_toggle_off)
            binding.btnSwap.setTextColor(0xCCFFFFFF.toInt())
            binding.btnSwap.alpha = 0.35f
            binding.btnSwap.isClickable = false
        }
    }

    // ════════════════════════════════════════════════════
    // 장 이동 (애니메이션)
    // ════════════════════════════════════════════════════
    private fun moveChapterAnim(target: Int, toRight: Boolean) {
        val w = binding.verseList.width.toFloat().takeIf { it > 0 } ?: 800f
        when {
            target in 0 until book.chapterCount -> {
                val outX = if (toRight) -w * 0.35f else w * 0.35f
                val inX  = if (toRight)  w * 0.35f else -w * 0.35f
                ObjectAnimator.ofFloat(binding.verseList, "translationX", 0f, outX)
                    .apply { duration = 140 }.start()
                binding.verseList.postDelayed({
                    chapter = target
                    showChapter()
                    binding.verseList.translationX = inX
                    ObjectAnimator.ofFloat(binding.verseList, "translationX", inX, 0f)
                        .apply { duration = 140 }.start()
                }, 140)
            }
            target < 0 -> {
                val prev = BibleRepository.bookByIndex(book.index - 1) ?: return
                book = prev; chapter = book.chapterCount - 1
                buildChapterBar(); showChapter()
                Toast.makeText(this, book.nameKo, Toast.LENGTH_SHORT).show()
            }
            else -> {
                val next = BibleRepository.bookByIndex(book.index + 1) ?: return
                book = next; chapter = 0
                buildChapterBar(); showChapter()
                Toast.makeText(this, book.nameKo, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ════════════════════════════════════════════════════
    // 장 칩 바 빌드
    // ════════════════════════════════════════════════════
    private fun buildChapterBar() {
        binding.chapterBar.removeAllViews()
        val dp = resources.displayMetrics.density
        for (c in 0 until book.chapterCount) {
            val chip = TextView(this).apply {
                text = (c + 1).toString(); textSize = 13f; gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.bg_chapter_chip)
                val hPad = (10 * dp).toInt(); val vPad = (5 * dp).toInt()
                setPadding(hPad, vPad, hPad, vPad)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (5 * dp).toInt() }
                setOnClickListener { moveChapterAnim(c, c > chapter) }
            }
            binding.chapterBar.addView(chip)
        }
    }

    // ════════════════════════════════════════════════════
    // 장 표시 (핵심)
    // ════════════════════════════════════════════════════
    private fun showChapter(scrollToVerse: Int = -1) {
        binding.toolbarTitle.text = "${book.nameKo} ${chapter + 1}장"

        val krvList = BibleRepository.getKrvBook(book.index)?.chapters?.getOrNull(chapter) ?: emptyList()
        val kjvList = BibleRepository.getKjvBook(book.index)?.chapters?.getOrNull(chapter) ?: emptyList()

        val primaryVerses: List<String>
        val secondaryVerses: List<String>

        when {
            !parallelOn -> {
                // 병행 OFF: 현재 버전 단일 표시
                primaryVerses   = if (BibleRepository.currentVersion == BibleRepository.Version.KRV) krvList else kjvList
                secondaryVerses = emptyList()
            }
            koPrimary -> {
                // 병행 ON, 한국어 메인
                primaryVerses   = krvList
                secondaryVerses = kjvList
            }
            else -> {
                // 병행 ON, 영어 메인
                primaryVerses   = kjvList
                secondaryVerses = krvList
            }
        }

        // 메모가 있는 구절 번호 (1-based) 수집
        val chapterNoted = NoteStore.all(this)
            .filter { it.bookIndex == book.index && it.chapter == chapter + 1 }
            .map { it.verse }
            .toSet()

        verseAdapter.submit(primaryVerses, bookmarkedVerses(), chapterNoted)
        verseAdapter.setShowKjv(
            parallelOn && secondaryVerses.isNotEmpty() && secondaryVerses !== primaryVerses,
            secondaryVerses
        )

        // 마지막 위치 저장
        BookmarkStore.saveLastPosition(this, book.index, chapter)

        // 칩 하이라이트 + 스크롤
        highlightChip()
        binding.chapterScroll.post {
            val child = binding.chapterBar.getChildAt(chapter) ?: return@post
            val cx = child.left - (binding.chapterScroll.width / 2) + (child.width / 2)
            binding.chapterScroll.smoothScrollTo(cx.coerceAtLeast(0), 0)
        }

        // ── 절 위치로 스크롤 (이어서 읽기 or 검색 결과)
        if (scrollToVerse in 0 until primaryVerses.size) {
            BookmarkStore.saveLastVerse(this, scrollToVerse)
            binding.verseList.post {
                val lm = binding.verseList.layoutManager as LinearLayoutManager
                val offset = (binding.verseList.height * 0.20f).toInt()
                lm.scrollToPositionWithOffset(scrollToVerse, offset)
                // 깜빡임 하이라이트
                binding.verseList.postDelayed({
                    val vh = binding.verseList.findViewHolderForAdapterPosition(scrollToVerse)
                    vh?.itemView?.animate()
                        ?.setDuration(180)?.alpha(0.25f)
                        ?.withEndAction {
                            vh.itemView.animate().setDuration(280).alpha(1f).start()
                        }?.start()
                }, 80)
            }
        } else {
            // 일반 진입: 기존 저장 위치를 덮어쓰지 않음 (스크롤 리스너가 관리)
            binding.verseList.scrollToPosition(0)
        }
    }

    private fun highlightChip() {
        for (i in 0 until binding.chapterBar.childCount) {
            val tv = binding.chapterBar.getChildAt(i) as? TextView ?: continue
            if (i == chapter) {
                tv.setTextColor(getColor(R.color.white))
                tv.setBackgroundColor(getColor(R.color.indigo))
            } else {
                tv.setBackgroundResource(R.drawable.bg_chapter_chip)
                tv.setTextColor(getColor(R.color.ink_soft))
            }
        }
    }

    private fun bookmarkedVerses(): Set<Int> {
        val prefix = "${book.index}-${chapter + 1}-"
        return BookmarkStore.all(this).filter { it.key.startsWith(prefix) }
            .map { it.verse }.toSet()
    }

    // ════════════════════════════════════════════════════
    // 북마크 토글
    // ════════════════════════════════════════════════════
    private fun toggleBookmark(verseIndex: Int) {
        val krvList = BibleRepository.getKrvBook(book.index)?.chapters?.getOrNull(chapter) ?: emptyList()
        val kjvList = BibleRepository.getKjvBook(book.index)?.chapters?.getOrNull(chapter) ?: emptyList()
        val primList = when {
            !parallelOn -> if (BibleRepository.currentVersion == BibleRepository.Version.KRV) krvList else kjvList
            koPrimary   -> krvList
            else        -> kjvList
        }
        val text = primList.getOrNull(verseIndex) ?: return
        val bm = Bookmark(book.index, book.nameKo, chapter + 1, verseIndex + 1, text)
        val added = BookmarkStore.toggle(this, bm)
        showChapter()
        Toast.makeText(this,
            if (added) R.string.bookmarked else R.string.bookmark_removed,
            Toast.LENGTH_SHORT).show()
    }

    // ════════════════════════════════════════════════════
    // 메모 다이얼로그
    // ════════════════════════════════════════════════════
    private fun openNoteDialog(verseIndex: Int) {
        noteVerseIndex = verseIndex
        val verseNum = verseIndex + 1
        val noteKey  = "${book.index}-${chapter + 1}-$verseNum"
        val existing = NoteStore.get(this, noteKey)
        val now = System.currentTimeMillis()
        val dtFmt = SimpleDateFormat("yyyy년 MM월 dd일 HH:mm:ss", Locale.KOREA)

        val dv = layoutInflater.inflate(R.layout.dialog_note, null)
        val editTv   = dv.findViewById<EditText>(R.id.note_edit)
        val countTv  = dv.findViewById<TextView>(R.id.note_count)
        val dtTv     = dv.findViewById<TextView>(R.id.note_datetime)
        val refTv    = dv.findViewById<TextView>(R.id.note_ref)
        val photoBox = dv.findViewById<LinearLayout>(R.id.photo_container)
        val btnGal   = dv.findViewById<Button>(R.id.btn_gallery)
        val btnPub   = dv.findViewById<Button>(R.id.btn_publish)
        val btnCopy  = dv.findViewById<Button>(R.id.btn_copy_all)
        val btnDel   = dv.findViewById<Button>(R.id.btn_delete_note)

        activePhotoContainer = photoBox

        refTv.text = "${book.nameKo} ${chapter + 1}:$verseNum"
        // 첫 줄: 일시 (별도 라인). 신규는 현재 시각, 기존은 최종수정 시각
        dtTv.text = if (existing != null)
            dtFmt.format(Date(existing.updatedAt))
        else
            dtFmt.format(Date(now))

        if (existing != null) editTv.setText(existing.text)
        countTv.text = "${editTv.text.length} / 1000"

        editTv.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                countTv.text = "${s?.length ?: 0} / 1000"
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        refreshPhotos(photoBox, existing?.photoPaths ?: emptyList(), noteKey)

        // 갤러리에서만 사진 추가
        btnGal.setOnClickListener {
            val cur = NoteStore.get(this, noteKey)
            if ((cur?.photoPaths?.size ?: 0) >= 4) {
                Toast.makeText(this, "사진은 최대 4장까지 가능합니다", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivityForResult(
                Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI), REQ_GALLERY)
        }

        btnCopy.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("note", editTv.text.toString()))
            Toast.makeText(this, "메모가 복사됐습니다", Toast.LENGTH_SHORT).show()
        }

        btnDel.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("메모 삭제")
                .setMessage("이 구절의 메모와 사진을 모두 삭제하시겠습니까?")
                .setPositiveButton("삭제") { _, _ ->
                    NoteStore.delete(this, noteKey)
                    activeNoteDialog?.dismiss()
                    showChapter()
                }
                .setNegativeButton("취소", null)
                .show()
        }

        // 저장 처리 헬퍼
        fun persistNote(): Boolean {
            val txt = editTv.text.toString()
            val photos = NoteStore.get(this, noteKey)?.photoPaths ?: emptyList()
            return if (txt.isNotBlank() || photos.isNotEmpty()) {
                NoteStore.save(this, VerseNote(
                    bookIndex   = book.index,
                    bookNameKo  = book.nameKo,
                    chapter     = chapter + 1,
                    verse       = verseNum,
                    text        = txt,
                    createdAt   = existing?.createdAt ?: now,
                    updatedAt   = System.currentTimeMillis(),
                    photoPaths  = photos
                ))
                true
            } else false
        }

        // 발행 — 저장 후 뷰잉 장표로 이동
        btnPub.setOnClickListener {
            if (persistNote()) {
                activeNoteDialog?.dismiss()
                showChapter()
                startActivity(Intent(this, NoteViewActivity::class.java).apply {
                    putExtra(NoteViewActivity.EXTRA_KEY, noteKey)
                })
            } else {
                Toast.makeText(this, "메모나 사진을 입력하세요", Toast.LENGTH_SHORT).show()
            }
        }

        activeNoteDialog = AlertDialog.Builder(this)
            .setView(dv)
            .setPositiveButton("저장") { _, _ ->
                persistNote()
                showChapter()
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    // ── 사진 썸네일 갱신
    private fun refreshPhotos(container: LinearLayout, paths: List<String>, noteKey: String) {
        container.removeAllViews()
        val dp     = resources.displayMetrics.density
        val size   = (76 * dp).toInt()
        val margin = (8 * dp).toInt()
        val btnSz  = (24 * dp).toInt()

        val valid = paths.filter { File(it).exists() }

        for (path in valid) {
            // 사진 + 하단 컨트롤(회전/삭제)을 담는 세로 컨테이너
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = margin }
                gravity = Gravity.CENTER_HORIZONTAL
            }

            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFF12152A.toInt())
                try { setImageBitmap(BitmapFactory.decodeFile(path)) } catch (_: Exception) {}
            }

            // 컨트롤 바: 회전 / 삭제
            val ctrl = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (4 * dp).toInt() }
                gravity = Gravity.CENTER
            }

            val rotateBtn = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(btnSz, btnSz)
                    .apply { marginEnd = (10 * dp).toInt() }
                text = "↻"; textSize = 14f; gravity = Gravity.CENTER
                setTextColor(0xFFC8CEE5.toInt())
                setBackgroundColor(0x33FFFFFF)
                setOnClickListener {
                    if (NoteStore.rotatePhoto(path)) {
                        // 같은 파일을 다시 디코딩하여 갱신
                        BitmapFactory.decodeFile(path)?.let { iv.setImageBitmap(it) }
                        // 메모 updatedAt 갱신
                        NoteStore.get(this@ReaderActivity, noteKey)?.let { cur ->
                            NoteStore.save(this@ReaderActivity,
                                cur.copy(updatedAt = System.currentTimeMillis()))
                        }
                    } else {
                        Toast.makeText(this@ReaderActivity, "회전 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            val delBtn = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(btnSz, btnSz)
                text = "✕"; textSize = 13f; gravity = Gravity.CENTER
                setTextColor(0xFFEF5350.toInt())
                setBackgroundColor(0x33FFFFFF)
                setOnClickListener {
                    File(path).delete()
                    val cur = NoteStore.get(this@ReaderActivity, noteKey) ?: return@setOnClickListener
                    val updated = cur.copy(
                        photoPaths = cur.photoPaths.filter { it != path },
                        updatedAt  = System.currentTimeMillis()
                    )
                    if (updated.text.isNotBlank() || updated.photoPaths.isNotEmpty())
                        NoteStore.save(this@ReaderActivity, updated)
                    else
                        NoteStore.delete(this@ReaderActivity, noteKey)
                    refreshPhotos(container, updated.photoPaths, noteKey)
                }
            }

            ctrl.addView(rotateBtn)
            ctrl.addView(delBtn)
            col.addView(iv)
            col.addView(ctrl)
            container.addView(col)
        }

        // 사진이 하나도 없을 때 안내 (+ 기호 대신 텍스트)
        if (valid.isEmpty()) {
            val hint = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, size)
                gravity = Gravity.CENTER_VERTICAL
                text = "첨부된 사진 없음"
                textSize = 12f
                setTextColor(0xFF7A82A0.toInt())
            }
            container.addView(hint)
        }
    }

    // ════════════════════════════════════════════════════
    // 사진 결과 처리
    // ════════════════════════════════════════════════════
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || noteVerseIndex < 0) return

        val noteKey = "${book.index}-${chapter + 1}-${noteVerseIndex + 1}"
        val existing = NoteStore.get(this, noteKey)
        val photos   = existing?.photoPaths?.toMutableList() ?: mutableListOf()
        if (photos.size >= 4) return

        if (requestCode == REQ_GALLERY) {
            val uri = data?.data ?: return
            val dest = NoteStore.newPhotoFile(this, noteKey)
            try {
                contentResolver.openInputStream(uri)?.use { inp ->
                    FileOutputStream(dest).use { out -> inp.copyTo(out) }
                }
                photos.add(dest.absolutePath)
                val note = existing?.copy(photoPaths = photos, updatedAt = System.currentTimeMillis())
                    ?: VerseNote(book.index, book.nameKo, chapter + 1, noteVerseIndex + 1,
                        "", System.currentTimeMillis(), System.currentTimeMillis(), photos)
                NoteStore.save(this, note)
                activePhotoContainer?.let { refreshPhotos(it, photos, noteKey) }
            } catch (e: Exception) {
                Toast.makeText(this, "사진 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ════════════════════════════════════════════════════
    // 읽기 설정 (폰트 + 줄간격)
    // ════════════════════════════════════════════════════
    private fun showReadingSettings() {
        val fontSizes = floatArrayOf(13f, 15f, 17f, 19f, 22f, 26f)
        val lineGaps  = floatArrayOf(2f, 4f, 6f, 9f, 12f, 16f)

        val v = layoutInflater.inflate(R.layout.dialog_reading_settings, null)
        val preview  = v.findViewById<TextView>(R.id.preview_text)
        val seekFont = v.findViewById<android.widget.SeekBar>(R.id.seek_font)
        val seekLine = v.findViewById<android.widget.SeekBar>(R.id.seek_line)

        seekFont.max = fontSizes.size - 1
        seekLine.max = lineGaps.size - 1
        seekFont.progress = fontSizes.indexOfFirst { it == BookmarkStore.getFontSize(this) }.coerceAtLeast(2)
        seekLine.progress = lineGaps.indexOfFirst { it == BookmarkStore.getLineSpacing(this) }.coerceAtLeast(2)

        fun upd() {
            preview.textSize = fontSizes[seekFont.progress]
            preview.setLineSpacing(lineGaps[seekLine.progress] * resources.displayMetrics.density, 1f)
        }
        upd()

        val listener = object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: android.widget.SeekBar?, p: Int, u: Boolean) = upd()
            override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(s: android.widget.SeekBar?) {}
        }
        seekFont.setOnSeekBarChangeListener(listener)
        seekLine.setOnSeekBarChangeListener(listener)

        AlertDialog.Builder(this).setTitle("읽기 설정").setView(v)
            .setPositiveButton("적용") { _, _ ->
                val fs = fontSizes[seekFont.progress]
                val ls = lineGaps[seekLine.progress]
                BookmarkStore.setFontSize(this, fs)
                BookmarkStore.setLineSpacing(this, ls)
                verseAdapter.setFontSize(fs)
                verseAdapter.setLineSpacing(ls)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
