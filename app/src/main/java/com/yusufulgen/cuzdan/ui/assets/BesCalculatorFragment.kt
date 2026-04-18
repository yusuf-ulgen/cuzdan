package com.yusufulgen.cuzdan.ui.assets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.yusufulgen.cuzdan.R
import com.yusufulgen.cuzdan.databinding.FragmentCashCalculatorBaseBinding
import com.yusufulgen.cuzdan.databinding.IncludeCashBesFormBinding
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow

class BesCalculatorFragment : Fragment() {

    private var _base: FragmentCashCalculatorBaseBinding? = null
    private val base get() = _base!!

    private var _form: IncludeCashBesFormBinding? = null
    private val form get() = _form!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _base = FragmentCashCalculatorBaseBinding.inflate(inflater, container, false)
        _form = IncludeCashBesFormBinding.inflate(inflater, base.contentContainer, true)
        return base.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        base.textTitle.text = getString(R.string.cash_tool_bes_short)
        base.btnBack.setOnClickListener { findNavController().navigateUp() }
        base.btnCalculate.setOnClickListener { calculate() }

        form.editYears.setText("10")
        form.editAnnualReturn.setText("30")
        form.editStateRate.setText("30")
    }

    private fun calculate() {
        val principal = form.editPrincipal.text?.toString()?.trim().orEmpty().toBigDecimalOrNull() ?: return
        if (principal <= BigDecimal.ZERO) return

        val years = form.editYears.text?.toString()?.trim().orEmpty().toIntOrNull() ?: return
        if (years <= 0) return

        val annualReturn = form.editAnnualReturn.text?.toString()?.trim().orEmpty().toBigDecimalOrNull() ?: return
        val stateRate = form.editStateRate.text?.toString()?.trim().orEmpty().toBigDecimalOrNull() ?: return
        if (annualReturn < BigDecimal.ZERO || stateRate < BigDecimal.ZERO) return

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

        form.textResult.text = getString(
            R.string.cash_bes_result_template_v2,
            years.toString(),
            stateContribution.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            futureValue.toPlainString()
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _form = null
        _base = null
    }
}

