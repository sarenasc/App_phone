package com.comparador.supermercados.data.repository

import com.comparador.supermercados.data.model.Product
import com.comparador.supermercados.data.model.Supermarket
import com.comparador.supermercados.data.scraper.JumboScraper
import com.comparador.supermercados.data.scraper.LiderScraper
import com.comparador.supermercados.data.scraper.UnimarcScraper
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class SupermarketRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val lider = LiderScraper(client)
    private val jumbo = JumboScraper(client)
    private val unimarc = UnimarcScraper(client)

    suspend fun search(query: String): Map<Supermarket, List<Product>> = coroutineScope {
        val liderDeferred = async { runCatching { lider.search(query) }.getOrDefault(emptyList()) }
        val jumboDeferred = async { runCatching { jumbo.search(query) }.getOrDefault(emptyList()) }
        val unimarcDeferred = async { runCatching { unimarc.search(query) }.getOrDefault(emptyList()) }

        mapOf(
            Supermarket.LIDER to liderDeferred.await(),
            Supermarket.JUMBO to jumboDeferred.await(),
            Supermarket.UNIMARC to unimarcDeferred.await()
        )
    }
}
