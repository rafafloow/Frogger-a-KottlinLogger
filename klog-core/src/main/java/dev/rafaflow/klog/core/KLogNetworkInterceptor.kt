package dev.rafaflow.klog.core

import okhttp3.Interceptor
import okhttp3.HttpUrl
import okhttp3.Response
import java.io.IOException

/**
 * Optional OkHttp interceptor. The library declares OkHttp as compile-only.
 */
public class KLogNetworkInterceptor(
    private val tag: String = "KLogNetwork",
) : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startedAtNanos = System.nanoTime()

        return try {
            chain.proceed(request).also { response ->
                if (response.code in 400..599) {
                    KLog.e(
                        tag = tag,
                        msg = "${request.method} ${request.url.withoutQuery()} returned " +
                            "${response.code} in ${elapsedMillis(startedAtNanos)} ms",
                    )
                }
            }
        } catch (exception: IOException) {
            KLog.e(
                tag = tag,
                msg = "${request.method} ${request.url.withoutQuery()} failed after " +
                    "${elapsedMillis(startedAtNanos)} ms",
                throwable = exception,
            )
            throw exception
        }
    }

    private fun elapsedMillis(startedAtNanos: Long): Long =
        (System.nanoTime() - startedAtNanos) / NANOS_PER_MILLISECOND

    // Avoid leaking query parameters, credentials, or URL fragments into reports.
    private fun HttpUrl.withoutQuery(): String = newBuilder()
        .query(null)
        .fragment(null)
        .username("")
        .password("")
        .build()
        .toString()

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
