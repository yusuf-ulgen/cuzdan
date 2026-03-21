package com.example.cuzdan.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.cuzdan.data.local.entity.Portfolio
import com.example.cuzdan.data.repository.PortfolioRepository
import com.example.cuzdan.databinding.DialogEditPortfolioBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class EditPortfolioDialogFragment : DialogFragment() {

    @Inject
    lateinit var portfolioRepository: PortfolioRepository

    private var _binding: DialogEditPortfolioBinding? = null
    private val binding get() = _binding!!

    private var portfolioId: Long = -1

    companion object {
        private const val ARG_PORTFOLIO_ID = "portfolio_id"

        fun newInstance(portfolioId: Long): EditPortfolioDialogFragment {
            val fragment = EditPortfolioDialogFragment()
            val args = Bundle()
            args.putLong(ARG_PORTFOLIO_ID, portfolioId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        portfolioId = arguments?.getLong(ARG_PORTFOLIO_ID) ?: -1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditPortfolioBinding.inflate(inflater, container, false)
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
        loadPortfolioData()
        setupListeners()
    }

    private fun loadPortfolioData() {
        if (portfolioId == -1L) {
            dismiss()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            val portfolio = portfolioRepository.getPortfolioById(portfolioId)
            portfolio?.let {
                binding.editPortfolioName.setText(it.name)
                binding.switchIncludeTotal.isChecked = it.isIncludedInTotal
            } ?: dismiss()
        }
    }

    private fun setupListeners() {
        binding.btnSave.setOnClickListener {
            val name = binding.editPortfolioName.text.toString().trim()
            if (name.isEmpty()) {
                binding.editPortfolioName.error = "Lütfen bir isim girin"
            } else {
                val includeTotal = binding.switchIncludeTotal.isChecked
                
                CoroutineScope(Dispatchers.Main).launch {
                    val portfolio = portfolioRepository.getPortfolioById(portfolioId)
                    portfolio?.let {
                        portfolioRepository.updatePortfolio(it.copy(
                            name = name,
                            isIncludedInTotal = includeTotal
                        ))
                        Toast.makeText(context, "Portföy güncellendi", Toast.LENGTH_SHORT).show()
                        dismiss()
                    }
                }
            }
        }

        binding.btnDelete.setOnClickListener {
            showDeleteConfirmation()
        }
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Portföyü Sil")
            .setMessage("Bu portföyü ve içindeki tüm varlıkları silmek istediğinize emin misiniz?")
            .setPositiveButton("SİL") { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    val portfolio = portfolioRepository.getPortfolioById(portfolioId)
                    portfolio?.let {
                        portfolioRepository.deletePortfolio(it)
                        Toast.makeText(context, "Portföy silindi", Toast.LENGTH_SHORT).show()
                        dismiss()
                    }
                }
            }
            .setNegativeButton("İPTAL", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
