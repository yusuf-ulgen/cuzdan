package com.yusufulgen.cuzdan.ui.notifications

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import com.yusufulgen.cuzdan.R
import com.yusufulgen.cuzdan.databinding.BottomSheetAgreementBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AgreementBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAgreementBinding? = null
    private val binding get() = _binding!!

    private var onAccepted: (() -> Unit)? = null
    private var title: String? = null
    private var content: String? = null
    private var isReadOnly: Boolean = false
    /** If true, pressing back without accepting will exit the whole app */
    private var isMandatory: Boolean = false

    companion object {
        fun newInstance(
            title: String,
            content: String,
            isReadOnly: Boolean = false,
            isMandatory: Boolean = false,
            onAccepted: (() -> Unit)? = null
        ): AgreementBottomSheet {
            return AgreementBottomSheet().apply {
                this.title = title
                this.content = content
                this.isReadOnly = isReadOnly
                this.isMandatory = isMandatory
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

        // Make the dialog not cancellable by touching outside when mandatory
        dialog?.setCanceledOnTouchOutside(!isMandatory)
        isCancelable = !isMandatory

        binding.textTitle.text = title
        binding.textContent.text = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            android.text.Html.fromHtml(content ?: "", android.text.Html.FROM_HTML_MODE_COMPACT)
        } else {
            @Suppress("DEPRECATION")
            android.text.Html.fromHtml(content ?: "")
        }

        if (isReadOnly) {
            binding.checkAgreement.visibility = View.GONE
            binding.btnAccept.text = getString(R.string.dialog_confirm)
            binding.btnAccept.isEnabled = true
        } else {
            binding.btnAccept.isEnabled = true
        }

        binding.btnAccept.setOnClickListener {
            if (isReadOnly || binding.checkAgreement.isChecked) {
                onAccepted?.invoke()
                dismiss()
            } else {
                com.google.android.material.snackbar.Snackbar.make(
                    binding.root,
                    getString(R.string.agreement_not_accepted),
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                )
                    .setBackgroundTint(resources.getColor(R.color.purple_500, null))
                    .setTextColor(resources.getColor(R.color.white, null))
                    .show()
            }
        }

        // Handle back press when mandatory
        if (isMandatory) {
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
                showExitWarningDialog()
            }
        }
    }

    private fun showExitWarningDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
            .setTitle(getString(R.string.agreement_exit_confirm))
            .setMessage(getString(R.string.agreement_must_accept_exit_warning))
            .setPositiveButton(getString(R.string.agreement_exit)) { _, _ ->
                requireActivity().finishAffinity()
            }
            .setNegativeButton(getString(R.string.agreement_go_back), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
