package com.md.qahelper.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.md.qahelper.QaHelper
import com.md.qahelper.databinding.QalFragmentCreateJiraBinding
import com.md.qahelper.dto.ServerResp
import com.md.qahelper.mgr.FileMgr
import com.md.qahelper.mgr.NetworkMgr
import com.md.qahelper.util.ShowToast
import com.md.qahelper.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 지라 티켓 생성 Fragment
 *
 * Created on 2026. 01. 01.
 */
class CreateJiraFragment : Fragment() {

    private var _binding: QalFragmentCreateJiraBinding? = null
    private val binding get() = _binding!!

    private val screenshots = mutableListOf<File>()
    private var issueKey: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = QalFragmentCreateJiraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadScreenshots()
        setListeners()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadScreenshots() {
        val files = FileMgr.getScreenshotDir(requireContext()).listFiles()
        screenshots.clear()

        if (files != null && files.isNotEmpty()) {
            val sortedFiles = files.sortedBy { it.lastModified() }
            screenshots.addAll(sortedFiles)
        }
    }

    private fun setListeners() {
        binding.layoutUpload.setOnClickListener {
            Utils.hideKeyboard(requireActivity())
            createJiraTicket()
        }
    }

    private fun createJiraTicket() {
        // 제목 유효성 검사
        val title = binding.editTitle.text.toString().trim()
        if (title.isEmpty()) {
            ShowToast(requireContext(), "제목을 입력해주세요")
            binding.editTitle.requestFocus()
            return
        }

        val inputDesc = binding.editDes.text.toString().trim()
        val desc = QaHelper.descPrefix + inputDesc

        lifecycleScope.launch {
            try {
                showLoading(true)
                binding.layoutUpload.isEnabled = false

                val response = withContext(Dispatchers.IO) {
                    NetworkMgr.postUpload(
                        targetUrl = QaHelper.createUrl,
                        projectKey = QaHelper.projectKey,
                        title = title,
                        desc = desc,
                        files = screenshots
                    )
                }

                showLoading(false)

                if (response != null) {
                    issueKey = response.issueKey
                    displaySuccess(response)

                    // Delete uploaded files
                    deleteUploadedFiles()
                } else {
                    displayError("서버 응답이 없거나 요청이 실패했습니다")
                }

            } catch (e: Exception) {
                showLoading(false)
                displayError("오류 발생: ${e.message}")
                e.printStackTrace()
            } finally {
                binding.layoutUpload.isEnabled = true
            }
        }
    }

    private fun displaySuccess(response: ServerResp) {
        val url = "${QaHelper.jiraBaseUrl}/browse/${response.issueKey}"
        val resultText = buildString {
            appendLine("=== Jira 티켓 생성 완료 ===\n")
            response.issueKey?.let { appendLine("Issue Key: $it") }
            response.totalUploadRequest?.let { appendLine("Total upload file request: $it") }
            response.uploadedCount?.let { appendLine("Uploaded file count: $it") }
            response.uploadStatus?.let { appendLine("Upload status: $it") }
            response.uploadStatus?.let { appendLine("url: $url") }
        }

        binding.tvResult.text = resultText
        ShowToast(requireContext(), "Jira 티켓이 생성되었습니다")
    }

    private fun displayError(errorMessage: String) {
        binding.tvResult.text = "=== 오류 발생 ===\n\n$errorMessage"
        ShowToast(requireContext(), "티켓 생성 실패")
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.tvResult.text = if (show) "Jira 티켓 생성 중..." else binding.tvResult.text
    }

    private fun deleteUploadedFiles() {
        FileMgr.deleteAllScreenshots(requireContext())
        screenshots.clear()
    }
}
