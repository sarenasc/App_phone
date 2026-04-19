package com.comparador.supermercados.data.repository

import android.content.Context
import android.util.Log
import com.comparador.supermercados.data.model.SearchState
import com.comparador.supermercados.data.model.Supermarket
import com.comparador.supermercados.data.scraper.JumboScraper
import com.comparador.supermercados.data.scraper.LiderScraper
import com.comparador.supermercados.data.scraper.UnimarcScraper
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class SupermarketRepository(context: Context) {

    private val lider = LiderScraper(context)
    private val jumbo = JumboScraper(context)
    private val unimarc = UnimarcScraper(context)

    suspend fun search(query: String): SearchState.Success = coroutineScope {
        val liderDeferred = async { runCatching { lider.search(query) } }
        val jumboDeferred = async { runCatching { jumbo.search(query) } }
        val unimarcDeferred = async { runCatching { unimarc.search(query) } }

        val liderResult = liderDeferred.await()
        val jumboResult = jumboDeferred.await()
        val unimarcResult = unimarcDeferred.await()

        liderResult.exceptionOrNull()?.let { Log.e(TAG, "Líder: ${it.message}") }
        jumboResult.exceptionOrNull()?.let { Log.e(TAG, "Jumbo: ${it.message}") }
        unimarcResult.exceptionOrNull()?.let { Log.e(TAG, "Unimarc: ${it.message}") }

        Log.d(TAG, "Líder: ${liderResult.getOrDefault(emptyList()).size} productos")
        Log.d(TAG, "Jumbo: ${jumboResult.getOrDefault(emptyList()).size} productos")
        Log.d(TAG, "Unimarc: ${unimarcResult.getOrDefault(emptyList()).size} productos")

        val results = mapOf(
            Supermarket.LIDER to liderResult.getOrDefault(emptyList()),
            Supermarket.JUMBO to jumboResult.getOrDefault(emptyList()),
            Supermarket.UNIMARC to unimarcResult.getOrDefault(emptyList())
        )

        val errors = buildMap<Supermarket, String> {
            liderResult.exceptionOrNull()?.let { put(Supermarket.LIDER, it.message ?: "Error") }
            jumboResult.exceptionOrNull()?.let { put(Supermarket.JUMBO, it.message ?: "Error") }
            unimarcResult.exceptionOrNull()?.let { put(Supermarket.UNIMARC, it.message ?: "Error") }
        }

        SearchState.Success(results, errors)
    }

    companion object {
        private const val TAG = "SupermarketRepo"
    }
}
