package com.md.qahelper.mgr

import com.google.gson.Gson
import com.md.qahelper.dto.ServerResp
import com.md.qahelper.dto.TicketInfoRes
import com.md.qahelper.util.MyLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 *
 * Created on 2025. 12. 29..
 */
object NetworkMgr {

    private const val LINE_FEED = "\r\n"
    private const val CHARSET = "UTF-8"
    private const val CONNECT_TIMEOUT = 12_000
    private const val READ_TIMEOUT = 60_000



    private fun addTextField(writer: DataOutputStream, boundary: String, name: String, value: String) {
        writer.writeBytes("--$boundary$LINE_FEED")
        writer.writeBytes("Content-Disposition: form-data; name=\"$name\"$LINE_FEED")
        writer.writeBytes(LINE_FEED)
        writer.write(value.toByteArray(Charsets.UTF_8))
        writer.writeBytes(LINE_FEED)
    }

    private fun addFilePart(writer: DataOutputStream, boundary: String, paramName: String, file: File) {
        if (!file.exists()) {
            MyLogger.loge("MyLogger File does not exist, skipping: ${file.absolutePath}")
            return
        }

        MyLogger.log("MyLogger Writing file part for: ${file.name}")
        writer.writeBytes("--$boundary$LINE_FEED")
        writer.writeBytes("Content-Disposition: form-data; name=\"$paramName\"; filename=\"${file.name}\"$LINE_FEED")
        writer.writeBytes("Content-Type: application/octet-stream$LINE_FEED")
        writer.writeBytes(LINE_FEED)
        FileInputStream(file).use { inputStream ->
            val buffer = ByteArray(4096)
            var bytesRead: Int
            var totalBytesWritten = 0L
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                writer.write(buffer, 0, bytesRead)
                totalBytesWritten += bytesRead
            }
            MyLogger.log("MyLogger File ${file.name} written: $totalBytesWritten bytes")
        }
        writer.writeBytes(LINE_FEED)
    }


    suspend fun postUpload(
        targetUrl: String,
        projectKey: String,
        title: String,
        desc: String,
        files: List<File>
    ): ServerResp? = withContext(Dispatchers.IO) {

        MyLogger.log("MyLogger started - URL: $targetUrl")
        MyLogger.log("MyLogger Parameters - projectKey: $projectKey")
        MyLogger.log("MyLogger Parameters - title: $title")
        MyLogger.log("MyLogger Parameters - desc: $desc")
        MyLogger.log("MyLogger Files count: ${files.size}")

        if (targetUrl.isBlank()) {
            MyLogger.loge("MyLogger Target URL is empty")
            return@withContext null
        }

        val boundary = "Boundary-${UUID.randomUUID()}"
        val url = URL(targetUrl)

        MyLogger.log("MyLogger Creating HTTP connection with boundary: $boundary")

        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            useCaches = false
            doOutput = true
            doInput = true
            requestMethod = "POST"
            setRequestProperty("Connection", "Keep-Alive")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setChunkedStreamingMode(0)
        }

        try {
            DataOutputStream(connection.outputStream).use { outputStream ->
                addTextField(outputStream, boundary, "projectKey", projectKey)
                addTextField(outputStream, boundary, "title", title)
                addTextField(outputStream, boundary, "desc", desc)

                if (files.isEmpty()) {
                    MyLogger.log("MyLogger No files to attach (files are optional)")
                } else {
                    MyLogger.log("MyLogger Adding ${files.size} file(s) to request")
                    for ((index, file) in files.withIndex()) {
                        MyLogger.log("MyLogger Adding file ${index + 1}/${files.size}: ${file.name} (${file.length()} bytes)")
                        addFilePart(outputStream, boundary, "files", file)
                    }
                }

                outputStream.writeBytes("--$boundary--$LINE_FEED")
                outputStream.flush()
                MyLogger.log("MyLogger Request data written successfully")
            }

            MyLogger.log("MyLogger Waiting for server response...")
            val responseCode = connection.responseCode
            MyLogger.log("MyLogger Received response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == 201) {
                MyLogger.log("MyLogger Response successful, reading response body...")
                val responseString = InputStreamReader(connection.inputStream, CHARSET).use { it.readText() }
                MyLogger.log("MyLogger Response body: $responseString")

                val serverResp = Gson().fromJson(responseString, ServerResp::class.java)
                return@withContext serverResp

            } else {
                MyLogger.loge("MyLogger Server returned error response code: $responseCode")
                val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() }
                MyLogger.loge("MyLogger Error response body: $errorMsg")
                return@withContext null
            }

        } catch (e: Exception) {
            MyLogger.loge("MyLogger Exception occurred during post: ${e.message}")
            e.printStackTrace()
            return@withContext null
        } finally {
            MyLogger.log("MyLogger Disconnecting connection")
            connection.disconnect()
        }
    }

    suspend fun getJira(
        targetUrl: String,
        issueKey: String
    ): TicketInfoRes? = withContext(Dispatchers.IO) {

        MyLogger.log("MyLogger getJira started - URL: $targetUrl, issueKey: $issueKey")

        if (targetUrl.isBlank()) {
            MyLogger.loge("MyLogger Target URL is empty")
            return@withContext null
        }

        if (issueKey.isBlank()) {
            MyLogger.loge("MyLogger issueKey is empty")
            return@withContext null
        }

        val url = URL(targetUrl)

        MyLogger.log("MyLogger Creating HTTP POST connection to: $targetUrl")

        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            useCaches = false
            doOutput = true
            doInput = true
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        try {
            // JSON body 작성
            val jsonBody = """{"issueKey":"$issueKey"}"""
            MyLogger.log("MyLogger Request body: $jsonBody")

            // Request body 전송
            connection.outputStream.use { outputStream ->
                outputStream.write(jsonBody.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            }

            MyLogger.log("MyLogger Waiting for server response...")
            val responseCode = connection.responseCode
            MyLogger.log("MyLogger Received response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                MyLogger.log("MyLogger Response successful, reading response body...")
                val responseString = InputStreamReader(connection.inputStream, CHARSET).use { it.readText() }
                MyLogger.log("MyLogger Response body: $responseString")

                val ticketInfoRes = Gson().fromJson(responseString, TicketInfoRes::class.java)
                return@withContext ticketInfoRes

            } else {
                MyLogger.loge("MyLogger Server returned error response code: $responseCode")
                val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() }
                MyLogger.loge("MyLogger Error response body: $errorMsg")
                return@withContext null
            }

        } catch (e: Exception) {
            MyLogger.loge("MyLogger Exception occurred during getJira: ${e.message}")
            e.printStackTrace()
            return@withContext null
        } finally {
            MyLogger.log("MyLogger Disconnecting connection")
            connection.disconnect()
        }
    }

    suspend fun attachFiles(
        targetUrl: String,
        issueKey: String,
        files: List<File>
    ): ServerResp? = withContext(Dispatchers.IO) {

        MyLogger.log("MyLogger attachFiles started - URL: $targetUrl, issueKey: $issueKey")
        MyLogger.log("MyLogger Files count: ${files.size}")

        if (targetUrl.isBlank()) {
            MyLogger.loge("MyLogger Target URL is empty")
            return@withContext null
        }

        if (issueKey.isBlank()) {
            MyLogger.loge("MyLogger issueKey is empty")
            return@withContext null
        }

        if (files.isEmpty()) {
            MyLogger.loge("MyLogger No files to attach")
            return@withContext null
        }

        val boundary = "Boundary-${UUID.randomUUID()}"
        val url = URL(targetUrl)

        MyLogger.log("MyLogger Creating HTTP connection with boundary: $boundary")

        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            useCaches = false
            doOutput = true
            doInput = true
            requestMethod = "POST"
            setRequestProperty("Connection", "Keep-Alive")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setChunkedStreamingMode(0)
        }

        try {
            DataOutputStream(connection.outputStream).use { outputStream ->
                // issueKey 필드 추가
                addTextField(outputStream, boundary, "issueKey", issueKey)

                MyLogger.log("MyLogger Adding ${files.size} file(s) to request")
                for ((index, file) in files.withIndex()) {
                    MyLogger.log("MyLogger Adding file ${index + 1}/${files.size}: ${file.name} (${file.length()} bytes)")
                    addFilePart(outputStream, boundary, "files", file)
                }

                outputStream.writeBytes("--$boundary--$LINE_FEED")
                outputStream.flush()
                MyLogger.log("MyLogger Request data written successfully")
            }

            MyLogger.log("MyLogger Waiting for server response...")
            val responseCode = connection.responseCode
            MyLogger.log("MyLogger Received response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == 201) {
                MyLogger.log("MyLogger Response successful, reading response body...")
                val responseString = InputStreamReader(connection.inputStream, CHARSET).use { it.readText() }
                MyLogger.log("MyLogger Response body: $responseString")

                val serverResp = Gson().fromJson(responseString, ServerResp::class.java)
                return@withContext serverResp

            } else {
                MyLogger.loge("MyLogger Server returned error response code: $responseCode")
                val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() }
                MyLogger.loge("MyLogger Error response body: $errorMsg")
                return@withContext null
            }

        } catch (e: Exception) {
            MyLogger.loge("MyLogger Exception occurred during attachFiles: ${e.message}")
            e.printStackTrace()
            return@withContext null
        } finally {
            MyLogger.log("MyLogger Disconnecting connection")
            connection.disconnect()
        }
    }
}