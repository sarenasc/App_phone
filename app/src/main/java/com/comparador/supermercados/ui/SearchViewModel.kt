package com.comparador.supermercados.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.comparador.supermercados.data.model.SearchState
import com.comparador.supermercados.data.repository.SupermarketRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SupermarketRepository(app)

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state.asStateFlow()

    fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _state.value = SearchState.Loading
            runCatching { repository.search(query) }
                .onSuccess { _state.value = it }
                .onFailure { _state.value = SearchState.Error(it.message ?: "Error desconocido") }
        }
    }
}
