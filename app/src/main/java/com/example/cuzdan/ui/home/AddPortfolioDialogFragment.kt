package com.example.cuzdan.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.cuzdan.data.local.entity.Portfolio
import com.example.cuzdan.data.repository.PortfolioRepository
import com.example.cuzdan.databinding.DialogAddPortfolioBinding
import com.example.cuzdan.util.showToast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AddPortfolioDialogFragment : DialogFragment() {

    @Inject
    lateinit var portfolioRepository: PortfolioRepository

    private var _binding: DialogAddPortfolioBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddPortfolioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
    }

    private fun setupListeners() {
        binding.btnSave.setOnClickListener {
            val name = binding.editPortfolioName.text.toString().trim()
            if (name.isEmpty()) {
                binding.editPortfolioName.error = getString(com.example.cuzdan.R.string.portfolio_name_hint)
            } else {
                val includeTotal = binding.switchIncludeTotal.isChecked
                
                CoroutineScope(Dispatchers.Main).launch {
                    portfolioRepository.insertPortfolio(
                        Portfolio(name = name, isIncludedInTotal = includeTotal)
                    )
                    showToast(com.example.cuzdan.R.string.toast_portfolio_added)
                    dismiss()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
