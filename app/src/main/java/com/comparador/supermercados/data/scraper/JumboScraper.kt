package com.comparador.supermercados.data.scraper

import android.content.Context
import com.comparador.supermercados.data.model.Product
import com.comparador.supermercados.data.model.Supermarket
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import java.net.URLEncoder

class JumboScraper(context: Context) : BaseScraper(context) {

    private val gson = Gson()

    override suspend fun search(query: String): List<Product> {
        val encoded = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        var lastError: Throwable? = null

        // Carga la página de búsqueda y extrae datos del estado interno de VTEX
        runCatching {
            val json = fetchPageStateViaWebView(
                "https://www.jumbo.cl/busqueda?q=$encoded",
                encoded,
                "jumbo.cl"
            )
            val products = parseStoreState(json, Supermarket.JUMBO)
            if (products.isNotEmpty()) return products
        }.onFailure { lastError = it }

        // Fallback: catalog API vía WebView fetch con cookies
        runCatching {
            val apiPath = "/api/catalog_system/pub/products/search?ft=$encoded&_from=0&_to=9"
            val products = parseVtex(
                fetchJsonViaWebView("https://www.jumbo.cl/", apiPath),
                Supermarket.JUMBO
            )
            if (products.isNotEmpty()) return products
        }.onFailure { lastError = it }

        lastError?.let { throw Exception(it.message) }
        return emptyList()
    }

    internal fun parseStoreState(json: String, supermarket: Supermarket): List<Product> {
        val root = runCatching { gson.fromJson(json, JsonObject::class.java) }.getOrNull() ?: return emptyList()

        // Buscar productos en múltiples ubicaciones del estado de VTEX IO
        val candidates = listOf(
            root.getAsJsonObject("search")?.getAsJsonArray("products"),
            root.getAsJsonObject("productList")?.getAsJsonArray("products"),
            root.getAsJsonArray("products"),
            root.getAsJsonObject("data")?.getAsJsonObject("productSearch")?.getAsJsonArray("products"),
        )
        val arr = candidates.firstNotNullOfOrNull { it?.takeIf { a -> a.size() > 0 } }
            ?: findProductArray(root, 0)
            ?: return emptyList()

        return arr.mapNotNull { el ->
            runCatching {
                val obj = el.asJsonObject
                val name = obj.get("productName")?.asString
                    ?: obj.get("name")?.asString
                    ?: return@mapNotNull null
                val brand = obj.get("brand")?.asString
                val firstItem = obj.getAsJsonArray("items")?.firstOrNull()?.asJsonObject
                val offer = firstItem?.getAsJsonArray("sellers")
                    ?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("commertialOffer")
                val price = offer?.get("Price")?.asDouble?.takeIf { it > 0 }
                    ?: obj.get("price")?.asDouble?.takeIf { it > 0 }
                    ?: obj.getAsJsonObject("priceRange")?.getAsJsonObject("sellingPrice")?.get("lowPrice")?.asDouble?.takeIf { it > 0 }
                    ?: return@mapNotNull null
                val listPrice = offer?.get("ListPrice")?.asDouble
                    ?: obj.getAsJsonObject("priceRange")?.getAsJsonObject("listPrice")?.get("lowPrice")?.asDouble
                val imageUrl = firstItem?.getAsJsonArray("images")
                    ?.firstOrNull()?.asJsonObject?.get("imageUrl")?.asString
                    ?: obj.getAsJsonArray("images")?.firstOrNull()?.asJsonObject?.get("imageUrl")?.asString
                Product(name, price, listPrice?.takeIf { it > price }, imageUrl, brand, supermarket)
            }.getOrNull()
        }
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
                Product(name, price, listPrice?.takeIf { it > price }, imageUrl, brand, supermarket)
            }.getOrNull()
        }
    }

    private fun findProductArray(obj: JsonObject, depth: Int): JsonArray? {
        if (depth > 6) return null
        for (key in obj.keySet()) {
            val value = obj.get(key) ?: continue
            when {
                value is JsonArray && value.size() > 0 -> {
                    val first = value.firstOrNull() as? JsonObject ?: continue
                    if (first.has("productName") || first.has("productId")) return value
                }
                value is JsonObject -> findProductArray(value, depth + 1)?.let { return it }
            }
        }
        return null
    }
}
