package com.md.qahelper.dto

/**
 * JIRA 티켓 정보 응답 DTO
 *
 * Created on 2026. 01. 01.
 */
data class TicketInfoRes(
    val ticketInfo: TicketInfo
)

data class TicketInfo(
    val issueKey: String,          //
    val title: String,             // 티켓 제목
    val status: String,            // 상태 (예: "Done")
    val priority: String?,         // 우선순위 (예: "Low")
    val assignee: String?,         // 담당자
    val reporter: String?,         // 보고자
    val created: String?,          // 생성일시 (ISO 8601 format)
    val updated: String?,          // 수정일시 (ISO 8601 format)
    val attachments: List<Attachment>?  // 첨부파일 목록
)

data class Attachment(
    val name: String,              // 파일명
    val url: String,               // 다운로드 URL
    val size: Long,                // 파일 크기 (bytes)
    val mimeType: String?          // MIME 타입
)