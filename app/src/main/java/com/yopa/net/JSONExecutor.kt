/*
 * ====================================================================
 * Copyright (c) 2012-2023 AbandonedCart.  All rights reserved.
 *
 * https://github.com/AbandonedCart/AbandonedCart/blob/main/LICENSE#L4
 * ====================================================================
 *
 * The license and distribution terms for any publicly available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution license
 * [including the GNU Public License.] Content not subject to these terms is
 * subject to to the terms and conditions of the Apache License, Version 2.0.
 */

package com.yopa.net

import android.os.Build
import com.yopa.samsprung.BuildConfig
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

class JSONExecutor(private val url: String) {

    private var listener: ResultListener? = null
    private var isStarted = false

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is Exception) {
            listener?.onException(throwable)
        }
    }

    private fun execute() {
        if (isStarted) return
        isStarted = true

        CoroutineScope(Dispatchers.IO + exceptionHandler).launch {
            try {
                val result = fetchUrl(url)
                if (result != null) {
                    listener?.onResults(result)
                } else {
                    listener?.onException(Exception("Empty or failed response from $url"))
                }
            } catch (e: Exception) {
                listener?.onException(e)
            }
        }
    }

    private fun fetchUrl(targetUrl: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val parsedUrl = URL(targetUrl)
            conn = parsedUrl.openConnection() as? HttpURLConnection ?: return null
            conn.apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                useCaches = false
                defaultUseCaches = false
                setRequestProperty(
                    "User-Agent",
                    "SamSprung-TooUI/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; Linux)"
                )
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }

            val statusCode = conn.responseCode
            if (statusCode == HttpURLConnection.HTTP_MOVED_PERM || statusCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                val redirectUrl = conn.getHeaderField("Location")
                conn.disconnect()
                if (!redirectUrl.isNullOrEmpty()) {
                    return fetchUrl(redirectUrl)
                }
                return null
            }

            if (statusCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return null
            }

            conn.inputStream.use { inStream ->
                BufferedReader(InputStreamReader(inStream, StandardCharsets.UTF_8)).use { streamReader ->
                    val responseStrBuilder = StringBuilder()
                    var inputStr: String?
                    while (streamReader.readLine().also { inputStr = it } != null) {
                        responseStrBuilder.append(inputStr)
                    }
                    responseStrBuilder.toString()
                }
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) { }
        }
    }

    interface ResultListener {
        fun onResults(result: String?)
        fun onException(e: Exception)
    }

    fun setResultListener(listener: ResultListener?) {
        this.listener = listener
        execute()
    }
}
