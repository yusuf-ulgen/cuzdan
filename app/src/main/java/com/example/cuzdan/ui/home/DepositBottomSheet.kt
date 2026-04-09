package com.example.cuzdan.ui.home

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import com.example.cuzdan.R
import com.example.cuzdan.databinding.BottomSheetDepositBinding
import com.example.cuzdan.util.formatCurrency
import com.example.cuzdan.util.showToast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import java.math.BigDecimal

@AndroidEntryPoint
class DepositBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetDepositBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by activityViewModels()

    private var selectedCurrency = "TL"
    private var isWithdrawMode = false

    private val colorPurple get() = android.content.res.ColorStateList.valueOf(0xFF8B5CF6.toInt())
    private val colorRed    get() = android.content.res.ColorStateList.valueOf(0xFFEF4444.toInt())
    private val colorClear  get() = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), R.style.CustomBottomSheetDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetDepositBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupModeToggle()
        setupCurrencySelector()
        setupListeners()
        updateCurrentDepositedInfo()
    }

    private fun setupModeToggle() {
        binding.btnModeDeposit.setOnClickListener {
            isWithdrawMode = false
            updateModeButtons()
        }
        binding.btnModeWithdraw.setOnClickListener {
            isWithdrawMode = true
            updateModeButtons()
        }
        updateModeButtons()
    }

    private fun updateModeButtons() {
        val textSecondaryColor = run {
            val tv = android.util.TypedValue()
            requireContext().theme.resolveAttribute(com.example.cuzdan.R.attr.textSecondary, tv, true)
            tv.data
        }

        if (!isWithdrawMode) {
            binding.btnModeDeposit.backgroundTintList = colorPurple
            binding.btnModeDeposit.setTextColor(Color.WHITE)
            binding.btnModeWithdraw.backgroundTintList = colorClear
            binding.btnModeWithdraw.setTextColor(textSecondaryColor)
            binding.textTitle.text = getString(R.string.deposit_title)
            binding.btnConfirm.text = getString(R.string.deposit_confirm)
            binding.btnConfirm.backgroundTintList = colorPurple
        } else {
            binding.btnModeWithdraw.backgroundTintList = colorPurple
            binding.btnModeWithdraw.setTextColor(Color.WHITE)
            binding.btnModeDeposit.backgroundTintList = colorClear
            binding.btnModeDeposit.setTextColor(textSecondaryColor)
            binding.textTitle.text = getString(R.string.withdraw_title)
            binding.btnConfirm.text = getString(R.string.withdraw_confirm)
            binding.btnConfirm.backgroundTintList = colorPurple
        }
    }

    private fun setupCurrencySelector() {
        updateCurrencySymbol()
        binding.cardCurrency.setOnClickListener {
            selectedCurrency = when (selectedCurrency) {
                "TL"  -> "USD"
                "USD" -> "EUR"
                else  -> "TL"
            }
            updateCurrencySymbol()
        }
    }

    private fun updateCurrencySymbol() {
        binding.textCurrencySymbol.text = when (selectedCurrency) {
            "USD" -> "$"
            "EUR" -> "€"
            else  -> "₺"
        }
    }

    private fun updateCurrentDepositedInfo() {
        val state = viewModel.uiState.value
        val selectedPortfolio = state.portfolios
            .find { it.portfolio.id == state.selectedPortfolioId }
        val depositedTry = selectedPortfolio?.depositedAmount ?: BigDecimal.ZERO
        binding.textCurrentDeposited.text = depositedTry.formatCurrency("TL")
    }

    private fun setupListeners() {
        binding.btnClose.setOnClickListener { dismiss() }

        binding.btnConfirm.setOnClickListener {
            val amountText = binding.editAmount.text.toString().trim().replace(",", ".")
            if (amountText.isEmpty() || amountText == ".") {
                binding.editAmountLayout.error = getString(R.string.deposit_error_empty)
                return@setOnClickListener
            }
            binding.editAmountLayout.error = null

            val amount = try {
                amountText.toBigDecimal()
            } catch (e: NumberFormatException) {
                binding.editAmountLayout.error = getString(R.string.deposit_error_invalid)
                return@setOnClickListener
            }

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                binding.editAmountLayout.error = getString(R.string.deposit_error_positive)
                return@setOnClickListener
            }

            val portfolioId = viewModel.uiState.value.selectedPortfolioId
            if (portfolioId == -1L) {
                showToast(com.example.cuzdan.R.string.deposit_error_total_mode)
                return@setOnClickListener
            }

            viewModel.depositOrWithdraw(amount, selectedCurrency, isWithdrawMode)
            val msgRes = if (isWithdrawMode) R.string.withdraw_success else R.string.deposit_success
            showToast(msgRes)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
