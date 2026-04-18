package com.yusufulgen.cuzdan.ui.assets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.yusufulgen.cuzdan.R
import com.yusufulgen.cuzdan.data.local.entity.AssetType
import com.yusufulgen.cuzdan.data.local.entity.PriceAlert
import com.yusufulgen.cuzdan.data.local.entity.PriceAlertCondition
import com.yusufulgen.cuzdan.databinding.BottomSheetPriceAlertBinding
import com.yusufulgen.cuzdan.util.formatCurrency
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.math.BigDecimal

class PriceAlertBottomSheet(
    private val symbol: String,
    private val name: String,
    private val assetType: AssetType,
    private val currentPrice: BigDecimal,
    private val onAlertSet: (PriceAlert) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetPriceAlertBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPriceAlertBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textAssetInfo.text = "$symbol - $name"
        binding.textCurrentPriceValue.text = currentPrice.formatCurrency()

        binding.btnSaveAlert.setOnClickListener {
            val targetStr = binding.editTargetPrice.text.toString()
            if (targetStr.isBlank()) {
                binding.editTargetPrice.error = getString(R.string.alert_error_price)
                return@setOnClickListener
            }

            val targetPrice = targetStr.toBigDecimalOrNull()
            if (targetPrice == null) {
                binding.editTargetPrice.error = getString(R.string.alert_error_invalid)
                return@setOnClickListener
            }

            val condition = when (binding.toggleCondition.checkedButtonId) {
                R.id.btn_above -> PriceAlertCondition.ABOVE
                R.id.btn_equals -> PriceAlertCondition.EQUALS
                else -> PriceAlertCondition.BELOW
            }

            val alert = PriceAlert(
                symbol = symbol,
                name = name,
                assetType = assetType,
                targetPrice = targetPrice,
                condition = condition
            )

            onAlertSet(alert)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "PriceAlertBottomSheet"
    }
}
