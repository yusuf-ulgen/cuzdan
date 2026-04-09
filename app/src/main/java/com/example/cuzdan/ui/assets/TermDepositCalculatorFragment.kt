package com.example.cuzdan.ui.assets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.cuzdan.R
import com.example.cuzdan.databinding.FragmentCashCalculatorBaseBinding
import com.example.cuzdan.databinding.IncludeCashTermDepositFormBinding
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class TermDepositCalculatorFragment : Fragment() {

    private var _base: FragmentCashCalculatorBaseBinding? = null
    private val base get() = _base!!

    private var _form: IncludeCashTermDepositFormBinding? = null
    private val form get() = _form!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _base = FragmentCashCalculatorBaseBinding.inflate(inflater, container, false)
        _form = IncludeCashTermDepositFormBinding.inflate(inflater, base.contentContainer, true)
        return base.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        base.textTitle.text = getString(R.string.cash_tool_term_deposit_short)
        base.btnBack.setOnClickListener { findNavController().navigateUp() }
        base.btnCalculate.setOnClickListener { calculate() }

        val today = java.time.Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.systemDefault()).toLocalDate()
        form.editStartDate.setText(today.toString())
    }

    private fun calculate() {
        val principal = form.editPrincipal.text?.toString()?.trim().orEmpty().toBigDecimalOrNull()
        val annualGrossRate = form.editAnnualRate.text?.toString()?.trim().orEmpty().toBigDecimalOrNull()
        val stopajRate = form.editStopajRate.text?.toString()?.trim().orEmpty().toBigDecimalOrNull()
        val startDateStr = form.editStartDate.text?.toString()?.trim().orEmpty()

        if (principal == null || principal <= BigDecimal.ZERO) return
        if (annualGrossRate == null || annualGrossRate < BigDecimal.ZERO) return
        if (stopajRate == null || stopajRate < BigDecimal.ZERO) return

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val startDate = try { LocalDate.parse(startDateStr, formatter) } catch (_: Exception) { null } ?: return

        val days = ChronoUnit.DAYS.between(startDate, LocalDate.now()).coerceAtLeast(0)

        val grossInterest = principal
            .multiply(annualGrossRate)
            .divide(BigDecimal("100"), 12, RoundingMode.HALF_UP)
            .multiply(BigDecimal(days))
            .divide(BigDecimal("365"), 12, RoundingMode.HALF_UP)

        val netInterest = grossInterest
            .multiply(BigDecimal.ONE.subtract(stopajRate.divide(BigDecimal("100"), 12, RoundingMode.HALF_UP)))

        val total = principal.add(netInterest).setScale(2, RoundingMode.HALF_UP)

        form.textResult.text = getString(
            R.string.cash_term_deposit_result_template,
            days.toString(),
            netInterest.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            total.toPlainString()
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _form = null
        _base = null
    }
}

