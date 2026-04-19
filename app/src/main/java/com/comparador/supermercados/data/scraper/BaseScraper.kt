package com.comparador.supermercados.data.scraper

import com.comparador.supermercados.data.model.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

abstract class BaseScraper(protected val client: OkHttpClient) {

    protected suspend fun fetchHtml(url: String, extraHeaders: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(url)
                .addHeader("User-Agent", CHROME_UA)
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .addHeader("Accept-Language", "es-CL,es;q=0.9,en;q=0.8")
                .addHeader("Accept-Encoding", "gzip, deflate, br")
                .addHeader("sec-ch-ua", SEC_CH_UA)
                .addHeader("sec-ch-ua-mobile", "?1")
                .addHeader("sec-ch-ua-platform", "\"Android\"")
                .addHeader("sec-fetch-dest", "document")
                .addHeader("sec-fetch-mode", "navigate")
                .addHeader("sec-fetch-site", "none")
                .addHeader("sec-fetch-user", "?1")
                .addHeader("upgrade-insecure-requests", "1")
            extraHeaders.forEach { (k, v) -> builder.addHeader(k, v) }
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) throw Exception("HTTP ${response.code} en $url")
                response.body?.string() ?: ""
            }
        }

    protected suspend fun fetchJson(url: String, extraHeaders: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(url)
                .addHeader("User-Agent", CHROME_UA)
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("Accept-Language", "es-CL,es;q=0.9,en;q=0.8")
                .addHeader("Accept-Encoding", "gzip, deflate, br")
                .addHeader("sec-ch-ua", SEC_CH_UA)
                .addHeader("sec-ch-ua-mobile", "?1")
                .addHeader("sec-ch-ua-platform", "\"Android\"")
                .addHeader("sec-fetch-dest", "empty")
                .addHeader("sec-fetch-mode", "cors")
                .addHeader("sec-fetch-site", "same-origin")
            extraHeaders.forEach { (k, v) -> builder.addHeader(k, v) }
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) throw Exception("HTTP ${response.code} en $url")
                val body = response.body?.string() ?: ""
                val trimmed = body.trimStart()
                if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                    throw Exception("Respuesta bloqueada (${trimmed.take(60)})")
                }
                body
            }
        }

    abstract suspend fun search(query: String): List<Product>

    companion object {
        private const val CHROME_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36"
        private const val SEC_CH_UA =
            "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\""
    }
}
