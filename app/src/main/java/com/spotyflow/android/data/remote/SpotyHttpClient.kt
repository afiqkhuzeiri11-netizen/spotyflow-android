package com.spotyflow.android.data.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object SpotyHttpClient {
    private val warmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    fun warmUpAsync() {
        warmScope.launch {
            try {
                val req = Request.Builder()
                    .url("https://music.youtube.com/generate_204")
                    .head()
                    .build()
                client.newCall(req).execute().close()
            } catch (_: Throwable) {
                // Non-fatal connection warm-up
            }
        }
    }
}
