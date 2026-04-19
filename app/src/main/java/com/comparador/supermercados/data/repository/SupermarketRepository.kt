package com.comparador.supermercados.data.repository

import android.util.Log
import com.comparador.supermercados.data.model.Product
import com.comparador.supermercados.data.model.SearchState
import com.comparador.supermercados.data.model.Supermarket
import com.comparador.supermercados.data.scraper.JumboScraper
import com.comparador.supermercados.data.scraper.LiderScraper
import com.comparador.supermercados.data.scraper.UnimarcScraper
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class SupermarketRepository {

    private val cookieJar = object : CookieJar {
        private val store = mutableMapOf<String, List<Cookie>>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { store[url.host] = cookies }
        override fun loadForRequest(url: HttpUrl): List<Cookie> = store[url.host] ?: emptyList()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .cookieJar(cookieJar)
        .build()

    private val lider = LiderScraper(client)
    private val jumbo = JumboScraper(client)
    private val unimarc = UnimarcScraper(client)

    suspend fun search(query: String): SearchState.Success = coroutineScope {
        val liderDeferred = async { runCatching { lider.search(query) } }
        val jumboDeferred = async { runCatching { jumbo.search(query) } }
        val unimarcDeferred = async { runCatching { unimarc.search(query) } }

        val liderResult = liderDeferred.await()
        val jumboResult = jumboDeferred.await()
        val unimarcResult = unimarcDeferred.await()

        liderResult.exceptionOrNull()?.let { Log.e(TAG, "Líder error: ${it.message}", it) }
        jumboResult.exceptionOrNull()?.let { Log.e(TAG, "Jumbo error: ${it.message}", it) }
        unimarcResult.exceptionOrNull()?.let { Log.e(TAG, "Unimarc error: ${it.message}", it) }

        Log.d(TAG, "Líder: ${liderResult.getOrDefault(emptyList()).size} productos")
        Log.d(TAG, "Jumbo: ${jumboResult.getOrDefault(emptyList()).size} productos")
        Log.d(TAG, "Unimarc: ${unimarcResult.getOrDefault(emptyList()).size} productos")

        val results = mapOf(
            Supermarket.LIDER to liderResult.getOrDefault(emptyList()),
            Supermarket.JUMBO to jumboResult.getOrDefault(emptyList()),
            Supermarket.UNIMARC to unimarcResult.getOrDefault(emptyList())
        )

        val errors = buildMap<Supermarket, String> {
            liderResult.exceptionOrNull()?.let { put(Supermarket.LIDER, it.message ?: "Error desconocido") }
            jumboResult.exceptionOrNull()?.let { put(Supermarket.JUMBO, it.message ?: "Error desconocido") }
            unimarcResult.exceptionOrNull()?.let { put(Supermarket.UNIMARC, it.message ?: "Error desconocido") }
        }

        SearchState.Success(results, errors)
    }

    companion object {
        private const val TAG = "SupermarketRepo"
    }
}
