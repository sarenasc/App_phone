package com.comparador.supermercados.data.scraper

import android.content.Context
import com.comparador.supermercados.data.model.Product
import com.comparador.supermercados.data.model.Supermarket
import java.net.URLEncoder

class UnimarcScraper(context: Context) : BaseScraper(context) {

    private val jumboParser = JumboScraper(context)

    override suspend fun search(query: String): List<Product> {
        val encoded = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        var lastError: Throwable? = null

        runCatching {
            val json = fetchPageStateViaWebView(
                "https://www.unimarc.cl/busqueda?q=$encoded",
                encoded,
                "unimarc.cl"
            )
            val products = jumboParser.parseStoreState(json, Supermarket.UNIMARC)
            if (products.isNotEmpty()) return products
        }.onFailure { lastError = it }

        runCatching {
            val apiPath = "/api/catalog_system/pub/products/search?ft=$encoded&_from=0&_to=9"
            val products = jumboParser.parseVtex(
                fetchJsonViaWebView("https://www.unimarc.cl/", apiPath),
                Supermarket.UNIMARC
            )
            if (products.isNotEmpty()) return products
        }.onFailure { lastError = it }

        lastError?.let { throw Exception(it.message) }
        return emptyList()
    }
}
