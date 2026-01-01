package com.md.qahelper.dto

/**
 * n8n 웹훅 응답 DTO
 *
 * Created on 2025. 12. 22.
 */
data class ServerResp(
    val issueKey: String?,    // 지라 티켓 번호 (예: "QA-3843")
    val totalUploadRequest: Int?,
    val uploadedCount: Int?,
    val uploadStatus: String?    // 응답 메시지
)