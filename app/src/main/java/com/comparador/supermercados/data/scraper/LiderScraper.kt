package com.comparador.supermercados.data.scraper

import com.comparador.supermercados.data.model.Product
import com.comparador.supermercados.data.model.Supermarket
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import java.net.URLEncoder

class LiderScraper(client: OkHttpClient) : BaseScraper(client) {

    private val gson = Gson()
    private val jumboParser = JumboScraper(client)

    override suspend fun search(query: String): List<Product> {
        val encoded = URLEncoder.encode(query, "UTF-8")

        // Intento 1: API VTEX (en caso de que hayan migrado plataforma)
        runCatching {
            val url = "https://www.lider.cl/supermercado/api/catalog_system/pub/products/search?ft=$encoded&_from=0&_to=9"
            val products = jumboParser.parseVtex(fetchJson(url), Supermarket.LIDER)
            if (products.isNotEmpty()) return products
        }

        // Intento 2: Parsear datos embebidos en la página Next.js (__NEXT_DATA__)
        runCatching {
            val html = fetchHtml("https://www.lider.cl/supermercado/search?Ntt=$encoded&currentPage=1")
            val doc = Jsoup.parse(html)
            val nextData = doc.select("script#__NEXT_DATA__").firstOrNull()?.data()
            if (!nextData.isNullOrBlank()) {
                val products = parseNextData(nextData)
                if (products.isNotEmpty()) return products
            }
        }

        return emptyList()
    }

    private fun parseNextData(json: String): List<Product> {
        val root = runCatching { gson.fromJson(json, JsonObject::class.java) }.getOrNull() ?: return emptyList()
        val pageProps = root.getAsJsonObject("props")?.getAsJsonObject("pageProps") ?: return emptyList()
        val productArray = findProductArray(pageProps, depth = 0) ?: return emptyList()

        return productArray.mapNotNull { element ->
            runCatching {
                val obj = element.asJsonObject
                val name = obj.get("displayName")?.asString
                    ?: obj.get("name")?.asString
                    ?: obj.get("productName")?.asString
                    ?: return@mapNotNull null

                val price = obj.get("price")?.asDouble
                    ?: obj.getAsJsonObject("prices")?.get("normalPrice")?.asDouble
                    ?: obj.get("sellingPrice")?.asDouble
                    ?: return@mapNotNull null

                val originalPrice = obj.getAsJsonObject("prices")?.get("originalPrice")?.asDouble
                    ?: obj.get("listPrice")?.asDouble

                val imageUrl = obj.get("image")?.asString
                    ?: obj.getAsJsonObject("images")?.get("defaultImage")?.asString

                Product(
                    name = name,
                    price = price,
                    originalPrice = originalPrice?.takeIf { it > price },
                    imageUrl = imageUrl,
                    brand = obj.get("brand")?.asString,
                    supermarket = Supermarket.LIDER
                )
            }.getOrNull()
        }
    }

    private fun findProductArray(obj: JsonObject, depth: Int): JsonArray? {
        if (depth > 6) return null
        for (key in obj.keySet()) {
            val value = obj.get(key) ?: continue
            when {
                value is JsonArray && value.size() > 0 && looksLikeProductArray(value) -> return value
                value is JsonObject -> findProductArray(value, depth + 1)?.let { return it }
            }
        }
        return null
    }

    private fun looksLikeProductArray(array: JsonArray): Boolean {
        val first = array.firstOrNull() as? JsonObject ?: return false
        return first.has("displayName") || first.has("productName") ||
            (first.has("name") && (first.has("price") || first.has("prices")))
    }
}
