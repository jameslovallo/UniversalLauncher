package com.example.data.service

import okhttp3.OkHttpClient

/**
 * Process-wide [OkHttpClient] shared by every network service.
 *
 * Each service used to build its own client, which meant up to six connection pools,
 * dispatchers and background thread pools per process. Idle connections are pooled for
 * five minutes, so a cold start that touched weather, the catalogue and Drive spun up far
 * more sockets and threads than the launcher needs on a TV box.
 *
 * [OkHttpClient.newBuilder] copies the pool and dispatcher by reference, so per-service
 * clients derived from [shared] still share them while keeping their own timeout policy.
 * Callers that are happy with the default timeouts can use [shared] directly.
 */
object HttpClients {
    val shared: OkHttpClient = OkHttpClient.Builder().build()
}
