package com.comparador.supermercados.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.comparador.supermercados.R
import com.comparador.supermercados.data.model.Product
import com.comparador.supermercados.data.model.SearchState
import com.comparador.supermercados.data.model.Supermarket
import com.comparador.supermercados.databinding.ActivityMainBinding
import com.comparador.supermercados.ui.adapter.ProductAdapter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: SearchViewModel by viewModels()

    private val liderAdapter = ProductAdapter()
    private val jumboAdapter = ProductAdapter()
    private val unimarcAdapter = ProductAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupRecyclerViews()
        setupListeners()
        observeState()
    }

    private fun setupRecyclerViews() {
        fun setup(rv: androidx.recyclerview.widget.RecyclerView, adapter: ProductAdapter) {
            rv.adapter = adapter
            rv.layoutManager = LinearLayoutManager(this)
            rv.isNestedScrollingEnabled = false
            rv.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        }
        setup(binding.rvLider, liderAdapter)
        setup(binding.rvJumbo, jumboAdapter)
        setup(binding.rvUnimarc, unimarcAdapter)
    }

    private fun setupListeners() {
        binding.btnSearch.setOnClickListener { performSearch() }
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { performSearch(); true } else false
        }
    }

    private fun performSearch() {
        val query = binding.etSearch.text?.toString()?.trim() ?: return
        if (query.isBlank()) {
            binding.tilSearch.error = "Ingresa un producto para buscar"
            return
        }
        binding.tilSearch.error = null
        hideKeyboard()
        viewModel.search(query)
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        is SearchState.Idle -> showIdle()
                        is SearchState.Loading -> showLoading()
                        is SearchState.Success -> showResults(state.results)
                        is SearchState.Error -> showError(state.message)
                    }
                }
            }
        }
    }

    private fun showIdle() {
        binding.loadingContainer.visibility = View.GONE
        binding.tvError.visibility = View.GONE
        binding.cardLider.visibility = View.GONE
        binding.cardJumbo.visibility = View.GONE
        binding.cardUnimarc.visibility = View.GONE
    }

    private fun showLoading() {
        binding.loadingContainer.visibility = View.VISIBLE
        binding.tvError.visibility = View.GONE
        binding.cardLider.visibility = View.GONE
        binding.cardJumbo.visibility = View.GONE
        binding.cardUnimarc.visibility = View.GONE
    }

    private fun showResults(results: Map<Supermarket, List<Product>>) {
        binding.loadingContainer.visibility = View.GONE

        val lider = results[Supermarket.LIDER] ?: emptyList()
        val jumbo = results[Supermarket.JUMBO] ?: emptyList()
        val unimarc = results[Supermarket.UNIMARC] ?: emptyList()

        updateSection(binding.cardLider, binding.tvLiderCount, liderAdapter, lider)
        updateSection(binding.cardJumbo, binding.tvJumboCount, jumboAdapter, jumbo)
        updateSection(binding.cardUnimarc, binding.tvUnimarcCount, unimarcAdapter, unimarc)

        if (lider.isEmpty() && jumbo.isEmpty() && unimarc.isEmpty()) {
            binding.tvError.text = getString(R.string.no_results)
            binding.tvError.visibility = View.VISIBLE
        } else {
            binding.tvError.visibility = View.GONE
        }
    }

    private fun updateSection(
        card: com.google.android.material.card.MaterialCardView,
        countView: android.widget.TextView,
        adapter: ProductAdapter,
        products: List<Product>
    ) {
        if (products.isEmpty()) {
            card.visibility = View.GONE
        } else {
            adapter.submitList(products)
            countView.text = getString(R.string.results_count, products.size)
            card.visibility = View.VISIBLE
        }
    }

    private fun showError(message: String) {
        binding.loadingContainer.visibility = View.GONE
        binding.tvError.text = "Error: $message"
        binding.tvError.visibility = View.VISIBLE
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }
}
