package com.comparador.supermercados.data.scraper

import com.comparador.supermercados.data.model.Product
import com.comparador.supermercados.data.model.Supermarket
import okhttp3.OkHttpClient
import java.net.URLEncoder

class UnimarcScraper(client: OkHttpClient) : BaseScraper(client) {

    private val jumboParser = JumboScraper(client)

    override suspend fun search(query: String): List<Product> {
        val encoded = URLEncoder.encode(query, "UTF-8")

        // Intento 1: VTEX IO Intelligent Search (API moderna)
        runCatching {
            val url = "https://www.unimarc.cl/api/io/_v/api/intelligent-search/product_search" +
                "?locale=es-CL&query=$encoded&count=10&page=1&map=ft&hideUnavailableItems=true"
            val products = jumboParser.parseVtexIO(
                fetchJson(url, mapOf("Referer" to "https://www.unimarc.cl/", "Origin" to "https://www.unimarc.cl")),
                Supermarket.UNIMARC
            )
            if (products.isNotEmpty()) return products
        }

        // Intento 2: VTEX catalog search (legacy)
        runCatching {
            val url = "https://www.unimarc.cl/api/catalog_system/pub/products/search?ft=$encoded&_from=0&_to=9"
            val products = jumboParser.parseVtex(
                fetchJson(url, mapOf("Referer" to "https://www.unimarc.cl/")),
                Supermarket.UNIMARC
            )
            if (products.isNotEmpty()) return products
        }

        return emptyList()
    }
}
