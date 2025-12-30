package com.md.qahelper.mgr

import com.md.qahelper.dto.ServerResponse
import com.md.qahelper.util.MyLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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

    suspend fun postUpload(
        targetUrl: String,
        title: String,
        desc: String,
        files: List<File>
    ): ServerResponse? = withContext(Dispatchers.IO) {

        MyLogger.log("MyLogger started - URL: $targetUrl")
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

                val jsonObject = JSONObject(responseString)
                val jiraKey = if (jsonObject.isNull("jiraKey")) null else jsonObject.optString("jiraKey")
                val totalUploadRequest = if (jsonObject.isNull("totalUploadRequest")) null else jsonObject.optInt("totalUploadRequest")
                val uploadedCount = if (jsonObject.isNull("uploadedCount")) null else jsonObject.optInt("uploadedCount")
                val uploadStatus = if (jsonObject.isNull("uploadStatus")) null else jsonObject.optString("uploadStatus")

                return@withContext ServerResponse(jiraKey, totalUploadRequest, uploadedCount, uploadStatus)

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
}