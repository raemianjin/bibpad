package com.example.bibleapp.ui

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.bibleapp.R
import com.example.bibleapp.data.BibleRepository
import com.example.bibleapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val bibleFragment    by lazy { BibleFragment() }
    private val bookmarkFragment by lazy { BookmarkFragment() }
    private val noteFragment     by lazy { NoteFragment() }
    private val settingsFragment by lazy { SettingsFragment() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        BibleRepository.load(applicationContext)
        if (savedInstanceState == null) showFrag(bibleFragment, R.string.tab_bible)

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_bible     -> { showFrag(bibleFragment,    R.string.tab_bible);    true }
                R.id.nav_bookmark  -> { showFrag(bookmarkFragment, R.string.tab_bookmark); true }
                R.id.nav_note      -> { showFrag(noteFragment,     R.string.tab_note);     true }
                R.id.nav_settings  -> { showFrag(settingsFragment, R.string.tab_settings); true }
                else -> false
            }
        }
    }

    private fun showFrag(fragment: Fragment, titleRes: Int) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment).commit()
        binding.toolbar.setTitle(titleRes)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // BibleFragment 내 검색 오버레이 먼저 닫기
        val cur = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (cur is BibleFragment && cur.isSearchOpen()) {
            cur.closeSearch(); return
        }
        AlertDialog.Builder(this)
            .setTitle("앱 종료")
            .setMessage("한영Bible을 종료하시겠습니까?")
            .setPositiveButton("종료") { _, _ -> finish() }
            .setNegativeButton("취소", null).show()
    }
}
