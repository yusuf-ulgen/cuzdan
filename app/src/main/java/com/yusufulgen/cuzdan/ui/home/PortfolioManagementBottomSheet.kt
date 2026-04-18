package com.yusufulgen.cuzdan.ui.home

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
import com.yusufulgen.cuzdan.R
import com.yusufulgen.cuzdan.data.local.entity.Portfolio
import com.yusufulgen.cuzdan.databinding.BottomSheetPortfolioManagementBinding
import com.yusufulgen.cuzdan.databinding.ItemPortfolioManageBinding
import com.yusufulgen.cuzdan.util.formatCurrency
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.math.BigDecimal
import com.yusufulgen.cuzdan.util.PreferenceManager

import javax.inject.Inject

@AndroidEntryPoint
class PortfolioManagementBottomSheet : BottomSheetDialogFragment() {

    @Inject
    lateinit var prefManager: PreferenceManager

    private var _binding: BottomSheetPortfolioManagementBinding? = null

    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by activityViewModels()
    private lateinit var portfolioAdapter: PortfolioManageAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPortfolioManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        observeState()
    }

    private fun setupRecyclerView() {
        portfolioAdapter = PortfolioManageAdapter(
            onSelected = { portfolioId ->
                viewModel.selectPortfolio(portfolioId)
                dismiss()
            },
            onEdit = { portfolioId ->
                EditPortfolioDialogFragment.newInstance(portfolioId).show(parentFragmentManager, "EditPortfolio")
                dismiss()
            }
        )
        binding.recyclerPortfolios.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerPortfolios.adapter = portfolioAdapter
    }

    private fun setupListeners() {
        binding.btnClose.setOnClickListener { dismiss() }
    }


    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val list = mutableListOf<PortfolioItem>()
                    // Portföyler Toplamı (Maliyet hesaplaması burada yapılmalı ya da viewmodel'den gelmeli)
                    // Önceki implementasyonda totalBalance ve dailyChange viewmodel'den geliyordu.
                    // Toplam maliyeti de ekliyorum.
                    val totalCost = state.portfolios.sumOf { it.totalCost }
                    
                    list.add(PortfolioItem(-1, requireContext().getString(R.string.label_total_portfolios_sub), state.totalBalance, state.dailyChangeAbs, state.dailyChangePerc, totalCost))
                    
                    state.portfolios.forEach { p ->
                        list.add(PortfolioItem(p.portfolio.id, p.portfolio.name, p.balance, p.dailyChangeAbs, p.dailyChangePerc, p.totalCost))
                    }
                    
                    portfolioAdapter.submitList(list, state.selectedPortfolioId, state.currency)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    data class PortfolioItem(
        val id: Long, 
        val name: String, 
        val balance: BigDecimal,
        val changeAbs: BigDecimal,
        val changePerc: BigDecimal,
        val totalCost: BigDecimal
    )

    inner class PortfolioManageAdapter(
        private val onSelected: (Long) -> Unit,
        private val onEdit: (Long) -> Unit
    ) : RecyclerView.Adapter<PortfolioManageAdapter.ViewHolder>() {

        private var items = emptyList<PortfolioItem>()
        private var selectedId: Long = -1
        private var currency: String = "TL"

        fun submitList(newList: List<PortfolioItem>, currentSelected: Long, currentCurrency: String) {
            items = newList
            selectedId = currentSelected
            currency = currentCurrency
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemPortfolioManageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.apply {
                textPortfolioName.text = item.name
                radioSelected.isChecked = item.id == selectedId
                
                textPortfolioTotal.text = item.balance.formatCurrency(currency)
                textPortfolioTotal.setTextColor(root.context.getColor(if (prefManager.getThemeMode() == "light") R.color.black else R.color.white))
                
                textPortfolioChange.text = String.format("%s (%%%+.1f)", 
                    item.changeAbs.formatCurrency(currency), 
                    item.changePerc
                )


                val isNeutral = item.changeAbs.abs() < BigDecimal("0.01") && item.changePerc.abs() < BigDecimal("0.01")
                val changeColor = when {
                    isNeutral -> com.yusufulgen.cuzdan.R.color.text_label
                    item.changeAbs >= BigDecimal.ZERO -> com.yusufulgen.cuzdan.R.color.accent_green
                    else -> com.yusufulgen.cuzdan.R.color.accent_red
                }
                textPortfolioChange.setTextColor(root.context.getColor(changeColor))

                if (item.id == -1L) {
                    btnEditPortfolio.visibility = View.GONE
                } else {
                    btnEditPortfolio.visibility = View.VISIBLE
                }
                
                root.setOnClickListener { onSelected(item.id) }
                btnEditPortfolio.setOnClickListener { onEdit(item.id) }
            }
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(val binding: ItemPortfolioManageBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
