package com.md.qahelper.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.md.qahelper.QaHelper
import com.md.qahelper.databinding.QalFragmentSearchJiraBinding
import com.md.qahelper.dto.TicketInfoRes
import com.md.qahelper.mgr.FileMgr
import com.md.qahelper.mgr.NetworkMgr
import com.md.qahelper.util.ShowToast
import com.md.qahelper.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId

/**
 * 지라 티켓 조회 Fragment
 *
 * Created on 2026. 01. 01.
 */
class SearchJiraFragment : Fragment() {

    private var _binding: QalFragmentSearchJiraBinding? = null
    private val binding get() = _binding!!

    private var currentIssueKey: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = QalFragmentSearchJiraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupView()
        setListeners()
    }

    private fun setupView() {
        binding.tilIssueKey.prefixText = "${QaHelper.projectKey}-"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setListeners() {
        binding.layoutSearch.setOnClickListener {
            Utils.hideKeyboard(requireActivity())
            searchJiraTicket()
        }

        binding.layoutAttachFiles.setOnClickListener {
            attachFilesToJira()
        }
    }

    private fun searchJiraTicket() {
        // 티켓 번호 유효성 검사
        val ticketNumber = binding.editIssueKey.text.toString().trim()
        if (ticketNumber.isEmpty()) {
            ShowToast(requireContext(), "티켓 번호를 입력해주세요")
            binding.editIssueKey.requestFocus()
            return
        }

        val issueKey = "${QaHelper.projectKey}-$ticketNumber"

        lifecycleScope.launch {
            try {
                showLoading(true)
                binding.layoutSearch.isEnabled = false

                val response = withContext(Dispatchers.IO) {
                    NetworkMgr.getJira(
                        targetUrl = QaHelper.getUrl,
                        issueKey = issueKey
                    )
                }

                showLoading(false)

                if (response != null) {
                    displaySuccess(response)
                } else {
                    displayError("서버 응답이 없거나 요청이 실패했습니다")
                }

            } catch (e: Exception) {
                showLoading(false)
                displayError("오류 발생: ${e.message}")
                e.printStackTrace()
            } finally {
                binding.layoutSearch.isEnabled = true
            }
        }
    }

    private fun displaySuccess(response: TicketInfoRes) {
        val ticket = response.ticketInfo
        val url = "${QaHelper.jiraBaseUrl}/browse/${ticket.issueKey}"

        currentIssueKey = ticket.issueKey

        val resultText = buildString {
            appendLine("=== Jira 티켓 정보 ===\n")
            appendLine("Key: ${ticket.issueKey}")
            appendLine("제목: ${ticket.title}")
            appendLine("상태: ${ticket.status}")
            ticket.priority?.let { appendLine("우선순위: $it") }
            ticket.assignee?.let { appendLine("담당자: $it") }
            ticket.reporter?.let { appendLine("보고자: $it") }
            ticket.created?.let { appendLine("생성일: ${formatToKoreanTime(it)}") }
            ticket.updated?.let { appendLine("수정일: ${formatToKoreanTime(it)}") }
            appendLine()
            appendLine("URL: $url")

            ticket.attachments?.let { attachments ->
                if (attachments.isNotEmpty()) {
                    appendLine()
                    appendLine("=== 첨부파일 (${attachments.size}개) ===")
                    attachments.forEach { attachment ->
                        appendLine("• ${attachment.name} (${formatFileSize(attachment.size)})")
                        appendLine("  ${attachment.url}")
                    }
                }
            }
        }

        binding.tvResult.text = resultText
        binding.layoutAttachFiles.visibility = View.VISIBLE
        ShowToast(requireContext(), "Jira 티켓 조회 완료")
    }

    private fun displayError(errorMessage: String) {
        binding.tvResult.text = "=== 오류 발생 ===\n\n$errorMessage"
        binding.layoutAttachFiles.visibility = View.GONE
        currentIssueKey = null
        ShowToast(requireContext(), "티켓 조회 실패")
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.tvResult.text = if (show) "Jira 티켓 조회 중..." else binding.tvResult.text
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${size / (1024 * 1024)} MB"
        }
    }

    /**
     * ISO 8601 날짜 문자열을 한국 시간으로 변환
     * @param isoDateString ISO 8601 형식의 날짜 문자열 (예: "2025-12-30T16:37:58.967+0900")
     * @return 한국 시간으로 포맷된 문자열 (예: "2025-12-30 16:37:58")
     */
    private fun formatToKoreanTime(isoDateString: String): String {
        return try {
            // 밀리초 + 타임존 형식 파싱 (예: "2025-12-30T16:37:58.967+0900")
            val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
            val zonedDateTime = ZonedDateTime.parse(isoDateString, inputFormatter)
            val koreanTime = zonedDateTime.withZoneSameInstant(ZoneId.of("Asia/Seoul"))
            val outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            koreanTime.format(outputFormatter)
        } catch (e: Exception) {
            isoDateString // 파싱 실패 시 원본 반환
        }
    }

    private fun attachFilesToJira() {
        val issueKey = currentIssueKey
        if (issueKey == null) {
            ShowToast(requireContext(), "지라 키 정보가 없습니다")
            return
        }

        // 스크린샷 파일 로드
        val screenshotDir = FileMgr.getScreenshotDir(requireContext())
        val files = screenshotDir.listFiles()

        if (files == null || files.isEmpty()) {
            ShowToast(requireContext(), "첨부할 스크린샷이 없습니다")
            return
        }

        val sortedFiles = files.sortedBy { it.lastModified() }

        lifecycleScope.launch {
            try {
                showLoading(true)
                binding.layoutAttachFiles.isEnabled = false

                // 파일 첨부 API 호출
                val response = withContext(Dispatchers.IO) {
                    NetworkMgr.attachFiles(
                        targetUrl = QaHelper.attachUrl,
                        issueKey = issueKey,
                        files = sortedFiles
                    )
                }

                showLoading(false)

                if (response != null) {
                    // 첨부 성공한 파일들 삭제
                    FileMgr.deleteAllScreenshots(requireContext())
                    ShowToast(requireContext(), "파일 첨부 완료")
                    // 다시 조회하여 업데이트된 첨부파일 목록 표시
                    searchJiraTicket()
                } else {
                    ShowToast(requireContext(), "파일 첨부 실패")
                }

            } catch (e: Exception) {
                showLoading(false)
                ShowToast(requireContext(), "오류 발생: ${e.message}")
                e.printStackTrace()
            } finally {
                binding.layoutAttachFiles.isEnabled = true
            }
        }
    }
}
