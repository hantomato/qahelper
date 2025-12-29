package com.md.qahelper.act

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.md.qahelper.QaHelper
import com.md.qahelper.databinding.QalActivityCreateJiraBinding
import com.md.qahelper.dto.ServerResponse
import com.md.qahelper.mgr.FileMgr
import com.md.qahelper.mgr.NetworkMgr
import com.md.qahelper.util.ShowToast
import com.md.qahelper.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 *
 * Created on 2025. 12. 23.
 */
class CreateJiraActivity : ComponentActivity() {

    private val screenshots = mutableListOf<File>()
    private var jiraKey: String? = null
    private val binding by lazy { QalActivityCreateJiraBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        Utils.applyWindowInsetsPadding(binding.root)
        Utils.setSystemBarsBlack(this)

        loadScreenshots()
        setListeners()
    }

    private fun loadScreenshots() {
        val files = FileMgr.getScreenshotDir(this).listFiles()
        screenshots.clear()

        if (files != null && files.isNotEmpty()) {
            val sortedFiles = files.sortedBy { it.lastModified() }  // 가장 먼저 생성된게 먼저 표시되도록
            screenshots.addAll(sortedFiles)
        }
    }

    private fun setListeners() {
        binding.ivUpload.setOnClickListener {

            Utils.hideKeyboard(this)
            createJiraTicket()
        }

        binding.ivClose.setOnClickListener {
            finish()
        }

        // 나중에 작업 예정
//        binding.btnOpen.setOnClickListener {
//            openJiraTicket()
//        }
    }

    private fun createJiraTicket() {
        // 제목 유효성 검사
        val title = binding.editTitle.text.toString().trim()
        if (title.isEmpty()) {
            ShowToast(this, "제목을 입력해주세요")
            binding.editTitle.requestFocus()
            return
        }

        val inputDesc = binding.editDes.text.toString().trim()
        val desc = QaHelper.descPrefix + inputDesc

        lifecycleScope.launch {
            try {
                showLoading(true)
                binding.ivUpload.isEnabled = false

                val response = withContext(Dispatchers.IO) {
                    NetworkMgr.postUpload(
                        targetUrl = QaHelper.serverUrl,
                        title = title,
                        desc = desc,
                        files = screenshots
                    )
                }

                showLoading(false)

                if (response != null) {
                    jiraKey = response.jiraKey
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
                binding.ivUpload.isEnabled = true
            }
        }
    }

    private fun displaySuccess(response: ServerResponse) {
        val url = "${QaHelper.jiraBaseUrl}/browse/${response.jiraKey}"
        val resultText = buildString {
            appendLine("=== Jira 티켓 생성 완료 ===\n")
            response.jiraKey?.let { appendLine("Jira Key: $it") }
            response.totalUploadRequest?.let { appendLine("Total upload file request: $it") }
            response.uploadedCount?.let { appendLine("Uploaded file count: $it") }
            response.uploadStatus?.let { appendLine("Upload status: $it") }
            response.uploadStatus?.let { appendLine("url: $url") }
        }

        binding.tvResult.text = resultText

        // Enable open button if jiraKey is available
//        binding.btnOpen.isEnabled = !response.jiraKey.isNullOrEmpty()

        ShowToast(this, "Jira 티켓이 생성되었습니다")
    }

    private fun displayError(errorMessage: String) {
        binding.tvResult.text = "=== 오류 발생 ===\n\n$errorMessage"
//        binding.btnOpen.isEnabled = false
        ShowToast(this, "티켓 생성 실패")
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.tvResult.text = if (show) "Jira 티켓 생성 중..." else binding.tvResult.text
    }

    private fun openJiraTicket() {
        val key = jiraKey
        if (key.isNullOrEmpty()) {
            ShowToast(this, "Jira 키가 없습니다")
            return
        }

        try {
            val url = "${QaHelper.jiraBaseUrl}/browse/$key"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            ShowToast(this, "브라우저를 열 수 없습니다: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun deleteUploadedFiles() {
        screenshots.forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        screenshots.clear()
    }
}
