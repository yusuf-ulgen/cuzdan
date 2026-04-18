package com.yusufulgen.cuzdan.ui.assets

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import com.yusufulgen.cuzdan.R
import com.yusufulgen.cuzdan.databinding.DialogTermDepositCalculatorBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class TermDepositCalculatorDialogFragment : DialogFragment() {

    private var _binding: DialogTermDepositCalculatorBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogTermDepositCalculatorBinding.inflate(LayoutInflater.from(requireContext()))

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.CustomDialogTheme)
            .setTitle(getString(R.string.cash_tool_term_deposit))
            .setView(binding.root)
            .setPositiveButton(R.string.dialog_confirm, null)
            .setNegativeButton(R.string.dialog_cancel) { _, _ -> dismiss() }
            .create()

        dialog.setOnShowListener {
            val btn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            btn.setOnClickListener {
                val principal = binding.editPrincipal.text?.toString()?.trim().orEmpty().toBigDecimalOrNull()
                val annualGrossRate = binding.editAnnualRate.text?.toString()?.trim().orEmpty().toBigDecimalOrNull()
                val stopajRate = binding.editStopajRate.text?.toString()?.trim().orEmpty().toBigDecimalOrNull()
                val startDateStr = binding.editStartDate.text?.toString()?.trim().orEmpty()

                if (principal == null || principal <= BigDecimal.ZERO) {
                    binding.editPrincipal.error = getString(R.string.error_invalid_amount)
                    return@setOnClickListener
                }
                if (annualGrossRate == null || annualGrossRate < BigDecimal.ZERO) {
                    binding.editAnnualRate.error = getString(R.string.error_invalid_rate)
                    return@setOnClickListener
                }
                if (stopajRate == null || stopajRate < BigDecimal.ZERO) {
                    binding.editStopajRate.error = getString(R.string.error_invalid_rate)
                    return@setOnClickListener
                }

                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                val startDate = try { LocalDate.parse(startDateStr, formatter) } catch (_: Exception) { null }
                if (startDate == null) {
                    binding.editStartDate.error = getString(R.string.error_invalid_date)
                    return@setOnClickListener
                }

                val today = LocalDate.now()
                val days = ChronoUnit.DAYS.between(startDate, today).coerceAtLeast(0)

                // Simple interest approximation:
                // grossInterest = P * (r/100) * (days/365)
                // netInterest = grossInterest * (1 - stopaj/100)
                val grossInterest = principal
                    .multiply(annualGrossRate)
                    .divide(BigDecimal("100"), 12, RoundingMode.HALF_UP)
                    .multiply(BigDecimal(days))
                    .divide(BigDecimal("365"), 12, RoundingMode.HALF_UP)

                val netInterest = grossInterest
                    .multiply(BigDecimal.ONE.subtract(stopajRate.divide(BigDecimal("100"), 12, RoundingMode.HALF_UP)))

                val total = principal.add(netInterest).setScale(2, RoundingMode.HALF_UP)
                binding.textResult.text = getString(
                    R.string.cash_term_deposit_result_template,
                    days.toString(),
                    netInterest.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    total.toPlainString()
                )
            }
        }

        // Clear inline errors as user types
        binding.editPrincipal.addTextChangedListener { binding.editPrincipal.error = null }
        binding.editAnnualRate.addTextChangedListener { binding.editAnnualRate.error = null }
        binding.editStopajRate.addTextChangedListener { binding.editStopajRate.error = null }
        binding.editStartDate.addTextChangedListener { binding.editStartDate.error = null }

        // Prefill start date as today in yyyy-MM-dd
        val todayMillis = System.currentTimeMillis()
        val today = java.time.Instant.ofEpochMilli(todayMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        binding.editStartDate.setText(today.toString())

        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

