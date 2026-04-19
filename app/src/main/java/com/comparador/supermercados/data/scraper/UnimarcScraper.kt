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
            val apiPath = "/api/catalog_system/pub/products/search?ft=$encoded&_from=0&_to=9&sc=1"
            val products = jumboParser.parseVtex(
                fetchJsonViaWebView("https://www.unimarc.cl/", apiPath),
                Supermarket.UNIMARC
            )
            if (products.isNotEmpty()) return products
        }.onFailure { lastError = it }

        runCatching {
            val apiPath = "/api/io/_v/api/intelligent-search/product_search" +
                "?locale=es-CL&query=$encoded&count=10&page=1&map=ft&hideUnavailableItems=true"
            val products = jumboParser.parseVtexIO(
                fetchJsonViaWebView("https://www.unimarc.cl/", apiPath),
                Supermarket.UNIMARC
            )
            if (products.isNotEmpty()) return products
        }.onFailure { lastError = it }

        lastError?.let { throw Exception(it.message) }
        return emptyList()
    }
}
