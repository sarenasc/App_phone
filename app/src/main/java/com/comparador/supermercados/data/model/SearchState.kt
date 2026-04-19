package com.comparador.supermercados.data.model

sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState()
    data class Success(
        val results: Map<Supermarket, List<Product>>,
        val errors: Map<Supermarket, String> = emptyMap()
    ) : SearchState()
    data class Error(val message: String) : SearchState()
}
