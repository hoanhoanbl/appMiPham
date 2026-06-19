package com.example.appbanmypham.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

object CloudinaryHelper {

    private const val CLOUD_NAME = "dfugeojgj"
    private const val API_KEY    = "345928911447233"
    private const val API_SECRET = "QQmUWvm3UtgLYhV3hn7imecwD1M"

    private val UPLOAD_URL = "https://api.cloudinary.com/v1_1/$CLOUD_NAME/image/upload"

    suspend fun uploadImage(context: Context, imageUri: Uri): String {
        return withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: throw Exception("Không thể đọc ảnh")
            val imageBytes = inputStream.readBytes()
            inputStream.close()

            val timestamp = (System.currentTimeMillis() / 1000).toString()
            val folder = "products"
            val stringToSign = "folder=$folder&timestamp=$timestamp$API_SECRET"
            val signature = sha1Hex(stringToSign)

            val boundary = "----Boundary${System.currentTimeMillis()}"
            val CRLF = "\r\n"

            val url = URL(UPLOAD_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                connectTimeout = 30_000
                readTimeout    = 30_000
            }

            val output = DataOutputStream(connection.outputStream)

            fun addField(name: String, value: String) {
                output.writeBytes("--$boundary$CRLF")
                output.writeBytes("Content-Disposition: form-data; name=\"$name\"$CRLF")
                output.writeBytes(CRLF)
                output.writeBytes(value)
                output.writeBytes(CRLF)
            }

            addField("api_key",   API_KEY)
            addField("timestamp", timestamp)
            addField("signature", signature)
            addField("folder",    folder)

            // File ảnh
            output.writeBytes("--$boundary$CRLF")
            output.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"image.jpg\"$CRLF")
            output.writeBytes("Content-Type: image/jpeg$CRLF")
            output.writeBytes(CRLF)
            output.write(imageBytes)
            output.writeBytes(CRLF)
            output.writeBytes("--$boundary--$CRLF")
            output.flush()
            output.close()

            val code = connection.responseCode
            val stream = if (code == 200) connection.inputStream else connection.errorStream
            val response = stream.bufferedReader().readText()
            stream.close()
            connection.disconnect()

            if (code != 200) throw Exception("Upload lỗi ($code): $response")

            JSONObject(response).getString("secure_url")
        }
    }

    suspend fun deleteImage(publicId: String) {
        withContext(Dispatchers.IO) {
            val timestamp = (System.currentTimeMillis() / 1000).toString()
            val stringToSign = "public_id=$publicId&timestamp=$timestamp$API_SECRET"
            val signature = sha1Hex(stringToSign)

            val url = URL("https://api.cloudinary.com/v1_1/$CLOUD_NAME/image/destroy")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }

            val body = "public_id=${Uri.encode(publicId)}&timestamp=$timestamp&api_key=$API_KEY&signature=$signature"
            connection.outputStream.write(body.toByteArray())
            connection.outputStream.close()
            connection.inputStream.close()
            connection.disconnect()
        }
    }

    fun getPublicIdFromUrl(url: String): String? {
        return try {
            val afterUpload = url.split("/upload/").getOrNull(1) ?: return null
            val withoutVersion = afterUpload.substringAfter("/")
            withoutVersion.substringBeforeLast(".")
        } catch (e: Exception) {
            null
        }
    }

    private fun sha1Hex(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}