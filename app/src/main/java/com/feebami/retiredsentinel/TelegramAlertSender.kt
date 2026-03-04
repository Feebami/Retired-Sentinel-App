package com.feebami.retiredsentinel

import android.content.Context
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException

class TelegramAlertSender {

    private val client = OkHttpClient()

    fun sendVideoAlert(context: Context, videoFile: File) {
        if (!AppSettings.telegramEnabled ||
            AppSettings.getTelegramToken(context).isBlank() ||
            AppSettings.getTelegramChatId(context).isBlank()) {
            Log.i("TelegramSender", "Telegram alerts disabled or missing credentials.")
            return
        }

        val url = "https://api.telegram.org/bot${AppSettings.getTelegramToken(context)}/sendVideo"

        // Build the multipart/form-data request body
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", AppSettings.getTelegramChatId(context))
            .addFormDataPart("caption", "\uD83D\uDEA8 INTRUDER ALERT! \uD83D\uDEA8\nUnrecognized person detected.")
            .addFormDataPart(
                "video",
                videoFile.name,
                videoFile.asRequestBody("video/mp4".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        Log.i("TelegramSender", "Uploading alert video to Telegram...")

        // Execute asynchronously so we don't block the camera or video encoder
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TelegramSender", "Failed to send video to Telegram", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.i("TelegramSender", "Successfully sent video to Telegram!")
                } else {
                    Log.e("TelegramSender", "Telegram API Error: ${response.code} - ${response.body.string()}")
                }
                response.close()
            }
        })
    }
}
