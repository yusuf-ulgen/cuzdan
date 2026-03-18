package com.example.cuzdan.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cuzdan.data.local.entity.Portfolio
import com.example.cuzdan.databinding.BottomSheetPortfolioSettingsListBinding
import com.example.cuzdan.databinding.ItemPortfolioSettingsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PortfolioSettingsListBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetPortfolioSettingsListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var portfolioAdapter: PortfolioSettingsAdapter

    override fun getTheme(): Int = com.example.cuzdan.R.style.CustomBottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPortfolioSettingsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        observeState()
    }

    private fun setupRecyclerView() {
        portfolioAdapter = PortfolioSettingsAdapter { portfolio ->
            showEditDialog(portfolio.id)
        }
        binding.recyclerPortfoliosSettings.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerPortfoliosSettings.adapter = portfolioAdapter
    }

    private fun setupListeners() {
        binding.btnClose.setOnClickListener { dismiss() }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Sadece gerçek portföyleri listele (Toplam hariç)
                    val realPortfolios = state.portfolios.map { it.portfolio }
                    portfolioAdapter.submitList(realPortfolios)
                }
            }
        }
    }

    private fun showEditDialog(portfolioId: Long) {
        PortfolioEditBottomSheet.newInstance(portfolioId).show(parentFragmentManager, "PortfolioEdit")
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class PortfolioSettingsAdapter(
        private val onEdit: (Portfolio) -> Unit
    ) : RecyclerView.Adapter<PortfolioSettingsAdapter.ViewHolder>() {

        private var items = emptyList<Portfolio>()

        fun submitList(newList: List<Portfolio>) {
            items = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemPortfolioSettingsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.textPortfolioName.text = item.name
            holder.itemView.setOnClickListener { onEdit(item) }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(val binding: ItemPortfolioSettingsBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
