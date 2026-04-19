package com.comparador.supermercados.data.scraper

import android.content.Context
import com.comparador.supermercados.data.model.Product
import com.comparador.supermercados.data.model.Supermarket
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.net.URLEncoder

class LiderScraper(context: Context) : BaseScraper(context) {

    private val gson = Gson()
    private val jumboParser = JumboScraper(context)

    override suspend fun search(query: String): List<Product> {
        val encoded = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        var lastError: Throwable? = null

        runCatching {
            val nextData = fetchNextDataViaWebView(
                "https://www.lider.cl/supermercado/search?Ntt=$encoded"
            )
            if (nextData.isNotBlank()) {
                val products = parseNextData(nextData)
                if (products.isNotEmpty()) return products
            }
        }.onFailure { lastError = it }

        lastError?.let { throw Exception(it.message) }
        return emptyList()
    }

    private fun parseNextData(json: String): List<Product> {
        val root = runCatching { gson.fromJson(json, JsonObject::class.java) }.getOrNull() ?: return emptyList()
        val pageProps = root.getAsJsonObject("props")?.getAsJsonObject("pageProps") ?: return emptyList()

        val knownProductArray = tryKnownPaths(pageProps)
        if (knownProductArray != null) {
            val products = parseProductArray(knownProductArray)
            if (products.isNotEmpty()) return products
        }

        val productArray = findProductArray(pageProps, depth = 0) ?: return emptyList()
        return parseProductArray(productArray)
    }

    private fun tryKnownPaths(pageProps: JsonObject): JsonArray? {
        val paths: List<() -> JsonArray?> = listOf(
            { pageProps.getAsJsonObject("searchResult")?.getAsJsonArray("products") },
            { pageProps.getAsJsonObject("initialData")?.getAsJsonObject("data")?.getAsJsonObject("search")?.getAsJsonArray("products") },
            { pageProps.getAsJsonObject("data")?.getAsJsonObject("search")?.getAsJsonArray("products") },
            { pageProps.getAsJsonArray("products") },
            {
                pageProps.getAsJsonObject("dehydratedState")
                    ?.getAsJsonArray("queries")
                    ?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("state")
                    ?.getAsJsonObject("data")
                    ?.getAsJsonObject("search")
                    ?.getAsJsonArray("products")
            }
        )
        return paths.firstNotNullOfOrNull { runCatching { it() }.getOrNull() }
    }

    private fun parseProductArray(array: JsonArray): List<Product> {
        return array.mapNotNull { element ->
            runCatching {
                val obj = element.asJsonObject
                val name = obj.get("displayName")?.asString
                    ?: obj.get("name")?.asString
                    ?: obj.get("productName")?.asString
                    ?: obj.get("title")?.asString
                    ?: return@mapNotNull null

                val price = obj.get("price")?.asDouble
                    ?: obj.getAsJsonObject("prices")?.get("normalPrice")?.asDouble
                    ?: obj.getAsJsonObject("prices")?.get("price")?.asDouble
                    ?: obj.get("sellingPrice")?.asDouble
                    ?: obj.get("offerPrice")?.asDouble
                    ?: return@mapNotNull null

                if (price <= 0) return@mapNotNull null

                val originalPrice = obj.getAsJsonObject("prices")?.get("originalPrice")?.asDouble
                    ?: obj.getAsJsonObject("prices")?.get("listPrice")?.asDouble
                    ?: obj.get("listPrice")?.asDouble

                val imageUrl = obj.get("image")?.asString
                    ?: obj.get("imageUrl")?.asString
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
        if (depth > 8) return null
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
        return first.has("displayName") || first.has("productName") || first.has("title") ||
            (first.has("name") && (first.has("price") || first.has("prices") || first.has("offerPrice")))
    }
}
