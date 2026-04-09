package com.example.cuzdan.ui.assets

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import com.example.cuzdan.R
import com.example.cuzdan.databinding.DialogBesCalculatorBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow

class BesCalculatorDialogFragment : DialogFragment() {

    private var _binding: DialogBesCalculatorBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogBesCalculatorBinding.inflate(LayoutInflater.from(requireContext()))

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.CustomDialogTheme)
            .setTitle(getString(R.string.cash_tool_bes))
            .setView(binding.root)
            .setPositiveButton(R.string.dialog_confirm, null)
            .setNegativeButton(R.string.dialog_cancel) { _, _ -> dismiss() }
            .create()

        dialog.setOnShowListener {
            val btn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            btn.setOnClickListener {
                val principal = binding.editPrincipal.text?.toString()?.trim().orEmpty().toBigDecimalOrNull()
                if (principal == null || principal <= BigDecimal.ZERO) {
                    binding.editPrincipal.error = getString(R.string.error_invalid_amount)
                    return@setOnClickListener
                }

                val years = binding.editYears.text?.toString()?.trim().orEmpty().toIntOrNull()
                val annualReturn = binding.editAnnualReturn.text?.toString()?.trim().orEmpty().toBigDecimalOrNull()
                val stateRate = binding.editStateRate.text?.toString()?.trim().orEmpty().toBigDecimalOrNull()

                if (years == null || years <= 0) {
                    binding.editYears.error = getString(R.string.error_invalid_years)
                    return@setOnClickListener
                }
                if (annualReturn == null || annualReturn < BigDecimal.ZERO) {
                    binding.editAnnualReturn.error = getString(R.string.error_invalid_rate)
                    return@setOnClickListener
                }
                if (stateRate == null || stateRate < BigDecimal.ZERO) {
                    binding.editStateRate.error = getString(R.string.error_invalid_rate)
                    return@setOnClickListener
                }

                // Basit BES modeli:
                // - Kullanıcı toplam katkı: principal
                // - Devlet katkısı: principal * (stateRate/100)
                // - Getiri: (principal + devlet katkısı) yıllık bileşik (annualReturn)
                val stateContribution = principal
                    .multiply(stateRate)
                    .divide(BigDecimal("100"), 12, RoundingMode.HALF_UP)

                val totalContribution = principal.add(stateContribution)
                val growth = (BigDecimal.ONE.add(annualReturn.divide(BigDecimal("100"), 12, RoundingMode.HALF_UP)))
                    .toDouble()
                    .pow(years)
                    .toBigDecimal()

                val futureValue = totalContribution
                    .multiply(growth)
                    .setScale(2, RoundingMode.HALF_UP)

                binding.textResult.text = getString(
                    R.string.cash_bes_result_template_v2,
                    years.toString(),
                    stateContribution.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    futureValue.toPlainString()
                )
            }
        }

        binding.editPrincipal.addTextChangedListener { binding.editPrincipal.error = null }
        binding.editYears.addTextChangedListener { binding.editYears.error = null }
        binding.editAnnualReturn.addTextChangedListener { binding.editAnnualReturn.error = null }
        binding.editStateRate.addTextChangedListener { binding.editStateRate.error = null }

        // Sensible defaults
        binding.editYears.setText("10")
        binding.editAnnualReturn.setText("30")
        binding.editStateRate.setText("30")

        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

