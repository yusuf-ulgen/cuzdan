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
import com.example.cuzdan.R
import com.example.cuzdan.data.local.entity.Portfolio
import com.example.cuzdan.databinding.BottomSheetPortfolioManagementBinding
import com.example.cuzdan.databinding.ItemPortfolioManageBinding
import com.example.cuzdan.util.formatCurrency
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.math.BigDecimal

@AndroidEntryPoint
class PortfolioManagementBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetPortfolioManagementBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels({ requireParentFragment() })
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
        portfolioAdapter = PortfolioManageAdapter { portfolioId ->
            viewModel.selectPortfolio(portfolioId)
            dismiss()
        }
        binding.recyclerPortfolios.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerPortfolios.adapter = portfolioAdapter
    }

    private fun setupListeners() {
        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnAddPortfolio.setOnClickListener {
            AddPortfolioDialogFragment().show(parentFragmentManager, "AddPortfolio")
            dismiss()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val list = mutableListOf<PortfolioItem>()
                    // Portföyler Toplamı (Sanal portföy)
                    list.add(PortfolioItem(-1, "Portföyler Toplamı", state.totalBalance)) // Basitlik için sadece bakiye
                    
                    state.portfolios.forEach { p ->
                        list.add(PortfolioItem(p.id, p.name, BigDecimal.ZERO)) // Gerçek bakiyeler için ek repo çağrısı gerekebilir ama şimdilik isim yeterli
                    }
                    
                    portfolioAdapter.submitList(list, state.selectedPortfolioId)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    data class PortfolioItem(val id: Long, val name: String, val balance: BigDecimal)

    inner class PortfolioManageAdapter(private val onSelected: (Long) -> Unit) :
        RecyclerView.Adapter<PortfolioManageAdapter.ViewHolder>() {

        private var items = emptyList<PortfolioItem>()
        private var selectedId: Long = -1

        fun submitList(newList: List<PortfolioItem>, currentSelected: Long) {
            items = newList
            selectedId = currentSelected
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
                
                // Bakiyeyi sadece toplam için gösteriyoruz şimdilik, diğerleri için repo yüklemesi lazım
                if (item.id == -1L) {
                    textPortfolioTotal.text = item.balance.formatCurrency()
                } else {
                    textPortfolioTotal.text = ""
                }
                textPortfolioChange.visibility = View.GONE
                
                root.setOnClickListener { onSelected(item.id) }
            }
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(val binding: ItemPortfolioManageBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
