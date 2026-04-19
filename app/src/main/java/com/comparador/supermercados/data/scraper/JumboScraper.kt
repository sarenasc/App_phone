package com.comparador.supermercados.data.scraper

import android.content.Context
import com.comparador.supermercados.data.model.Product
import com.comparador.supermercados.data.model.Supermarket
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import java.net.URLEncoder

class JumboScraper(context: Context) : BaseScraper(context) {

    private val gson = Gson()

    override suspend fun search(query: String): List<Product> {
        val encoded = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        var lastError: Throwable? = null

        // Carga jumbo.cl en WebView (resuelve Cloudflare) y llama al API vía fetch()
        runCatching {
            val apiPath = "/api/catalog_system/pub/products/search?ft=$encoded&_from=0&_to=9&sc=1"
            val products = parseVtex(
                fetchJsonViaWebView("https://www.jumbo.cl/", apiPath),
                Supermarket.JUMBO
            )
            if (products.isNotEmpty()) return products
        }.onFailure { lastError = it }

        // Fallback: VTEX IO Intelligent Search
        runCatching {
            val apiPath = "/api/io/_v/api/intelligent-search/product_search" +
                "?locale=es-CL&query=$encoded&count=10&page=1&map=ft&hideUnavailableItems=true"
            val products = parseVtexIO(
                fetchJsonViaWebView("https://www.jumbo.cl/", apiPath),
                Supermarket.JUMBO
            )
            if (products.isNotEmpty()) return products
        }.onFailure { lastError = it }

        lastError?.let { throw Exception(it.message) }
        return emptyList()
    }

    internal fun parseVtexIO(json: String, supermarket: Supermarket): List<Product> {
        val root = runCatching { gson.fromJson(json, JsonObject::class.java) }.getOrNull() ?: return emptyList()
        val productsArray = root.getAsJsonArray("products") ?: return emptyList()
        return parseVtex(productsArray.toString(), supermarket)
    }

    internal fun parseVtex(json: String, supermarket: Supermarket): List<Product> {
        val type = object : TypeToken<List<JsonObject>>() {}.type
        val items: List<JsonObject> = runCatching<List<JsonObject>> { gson.fromJson(json, type) }.getOrNull() ?: return emptyList()

        return items.mapNotNull { item ->
            runCatching {
                val name = item.get("productName")?.asString ?: return@mapNotNull null
                val brand = item.get("brand")?.asString
                val firstItem = item.getAsJsonArray("items")?.firstOrNull()?.asJsonObject ?: return@mapNotNull null
                val offer = firstItem.getAsJsonArray("sellers")
                    ?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("commertialOffer") ?: return@mapNotNull null

                if (offer.get("IsAvailable")?.asBoolean == false) return@mapNotNull null

                val price = offer.get("Price")?.asDouble?.takeIf { it > 0 } ?: return@mapNotNull null
                val listPrice = offer.get("ListPrice")?.asDouble
                val imageUrl = firstItem.getAsJsonArray("images")
                    ?.firstOrNull()?.asJsonObject?.get("imageUrl")?.asString

                Product(
                    name = name,
                    price = price,
                    originalPrice = listPrice?.takeIf { it > price },
                    imageUrl = imageUrl,
                    brand = brand,
                    supermarket = supermarket
                )
            }.getOrNull()
        }
    }
}
