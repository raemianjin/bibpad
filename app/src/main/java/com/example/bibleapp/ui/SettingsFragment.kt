package com.example.bibleapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.bibleapp.data.BookmarkStore
import com.example.bibleapp.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnUsage.setOnClickListener { showUsageDialog() }
        binding.btnLog.setOnClickListener { showLogDialog() }
        binding.btnBulletinUrl.setOnClickListener { showBulletinDialog() }
    }

    private fun showBulletinDialog() {
        val ctx = requireContext()
        val edit = EditText(ctx).apply {
            setText(BookmarkStore.getBulletinUrl(ctx))
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(ctx).setTitle("이번주 주보 링크 변경").setView(edit)
            .setPositiveButton("저장") { _, _ ->
                val url = edit.text.toString().trim()
                if (url.isNotBlank()) BookmarkStore.setBulletinUrl(ctx, url)
            }
            .setNegativeButton("취소", null).show()
    }

    private fun showUsageDialog() {
        val view = layoutInflater.inflate(com.example.bibleapp.R.layout.dialog_usage, null)
        AlertDialog.Builder(requireContext())
            .setTitle("사용법")
            .setView(view)
            .setPositiveButton("확인", null)
            .show()
    }

    private fun showLogDialog() {
        val log = com.example.bibleapp.util.AppLogger.readAll()
        AlertDialog.Builder(requireContext()).setTitle("진단 로그").setMessage(log)
            .setPositiveButton("공유") { _, _ ->
                startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT, log) },
                    "로그 공유"))
            }
            .setNeutralButton("지우기") { _, _ -> com.example.bibleapp.util.AppLogger.clear() }
            .setNegativeButton("닫기", null).show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
