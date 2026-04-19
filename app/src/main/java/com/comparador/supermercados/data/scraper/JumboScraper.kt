package com.comparador.supermercados.data.scraper

import com.comparador.supermercados.data.model.Product
import com.comparador.supermercados.data.model.Supermarket
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import java.net.URLEncoder

class JumboScraper(client: OkHttpClient) : BaseScraper(client) {

    private val gson = Gson()

    override suspend fun search(query: String): List<Product> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.jumbo.cl/api/catalog_system/pub/products/search?ft=$encoded&_from=0&_to=9"
        val json = fetchJson(url, mapOf("Referer" to "https://www.jumbo.cl/"))
        return parseVtex(json, Supermarket.JUMBO)
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

                val price = offer.get("Price")?.asDouble ?: return@mapNotNull null
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
