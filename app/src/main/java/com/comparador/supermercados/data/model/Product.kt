package com.comparador.supermercados.data.model

data class Product(
    val name: String,
    val price: Double,
    val originalPrice: Double? = null,
    val imageUrl: String? = null,
    val brand: String? = null,
    val supermarket: Supermarket
)

enum class Supermarket(val displayName: String) {
    LIDER("Líder"),
    JUMBO("Jumbo"),
    UNIMARC("Unimarc")
}
