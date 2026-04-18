package com.comparador.supermercados.data.scraper

import com.comparador.supermercados.data.model.Product
import com.comparador.supermercados.data.model.Supermarket
import okhttp3.OkHttpClient
import java.net.URLEncoder

class UnimarcScraper(client: OkHttpClient) : BaseScraper(client) {

    private val jumboParser = JumboScraper(client)

    override suspend fun search(query: String): List<Product> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.unimarc.cl/api/catalog_system/pub/products/search?ft=$encoded&_from=0&_to=9"
        val json = fetchJson(url, mapOf("Referer" to "https://www.unimarc.cl/"))
        return jumboParser.parseVtex(json, Supermarket.UNIMARC)
    }
}
