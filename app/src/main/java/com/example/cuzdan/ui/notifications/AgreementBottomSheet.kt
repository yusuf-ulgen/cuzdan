package com.example.cuzdan.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.cuzdan.R
import com.example.cuzdan.databinding.BottomSheetAgreementBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AgreementBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAgreementBinding? = null
    private val binding get() = _binding!!

    private var onAccepted: (() -> Unit)? = null
    private var title: String? = null
    private var content: String? = null
    private var isReadOnly: Boolean = false

    companion object {
        fun newInstance(title: String, content: String, isReadOnly: Boolean = false, onAccepted: (() -> Unit)? = null): AgreementBottomSheet {
            return AgreementBottomSheet().apply {
                this.title = title
                this.content = content
                this.isReadOnly = isReadOnly
                this.onAccepted = onAccepted
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetAgreementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.textTitle.text = title
        binding.textContent.text = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            android.text.Html.fromHtml(content ?: "", android.text.Html.FROM_HTML_MODE_COMPACT)
        } else {
            android.text.Html.fromHtml(content ?: "")
        }
        
        if (isReadOnly) {
            binding.checkAgreement.visibility = View.GONE
            binding.btnAccept.text = getString(R.string.dialog_confirm)
            binding.btnAccept.isEnabled = true
        } else {
            binding.btnAccept.isEnabled = true
            // Checkbox listener was just for enabling/disabling, can remove or keep for other visual feedback
        }

        binding.btnAccept.setOnClickListener {
            if (isReadOnly || binding.checkAgreement.isChecked) {
                onAccepted?.invoke()
                dismiss()
            } else {
                com.google.android.material.snackbar.Snackbar.make(binding.root, "Lütfen sözleşmeyi kabul edin.", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                    .setBackgroundTint(resources.getColor(R.color.purple_500, null))
                    .setTextColor(resources.getColor(R.color.white, null))
                    .show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
