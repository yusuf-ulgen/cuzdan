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
        binding.textContent.text = content
        
        if (isReadOnly) {
            binding.checkAgreement.visibility = View.GONE
            binding.btnAccept.text = getString(R.string.dialog_confirm)
            binding.btnAccept.isEnabled = true
        } else {
            binding.checkAgreement.setOnCheckedChangeListener { _, isChecked ->
                binding.btnAccept.isEnabled = isChecked
            }
        }

        binding.btnAccept.setOnClickListener {
            onAccepted?.invoke()
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
