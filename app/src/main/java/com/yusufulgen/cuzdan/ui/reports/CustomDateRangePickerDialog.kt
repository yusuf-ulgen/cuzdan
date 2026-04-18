package com.yusufulgen.cuzdan.ui.reports

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import com.yusufulgen.cuzdan.R
import com.yusufulgen.cuzdan.databinding.LayoutCustomDateRangePickerBinding
import com.yusufulgen.cuzdan.util.formatCurrency
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.*

class CustomDateRangePickerDialog(
    private val onRangeSelected: (Long, Long) -> Unit
) : DialogFragment() {

    private var _binding: LayoutCustomDateRangePickerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = LayoutCustomDateRangePickerBinding.inflate(LayoutInflater.from(context))

        setupSpinners()

        return MaterialAlertDialogBuilder(requireContext(), R.style.CustomDialogTheme)
            .setView(binding.root)
            .create()
    }

    private fun setupSpinners() {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)

        val days = (1..31).map { it.toString() }
        val months = (1..12).map { it.toString() }
        val years = (currentYear - 5..currentYear).map { it.toString() }.reversed()

        val dayAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, days)
        val monthAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, months)
        val yearAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, years)

        binding.spinnerStartDay.adapter = dayAdapter
        binding.spinnerStartMonth.adapter = monthAdapter
        binding.spinnerStartYear.adapter = yearAdapter

        binding.spinnerEndDay.adapter = dayAdapter
        binding.spinnerEndMonth.adapter = monthAdapter
        binding.spinnerEndYear.adapter = yearAdapter

        // Set default values (End date = today, Start date = 7 days ago)
        binding.spinnerEndDay.setSelection(currentDay - 1)
        binding.spinnerEndMonth.setSelection(currentMonth)
        binding.spinnerEndYear.setSelection(0) // First year is currentYear

        val startCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
        binding.spinnerStartDay.setSelection(startCal.get(Calendar.DAY_OF_MONTH) - 1)
        binding.spinnerStartMonth.setSelection(startCal.get(Calendar.MONTH))
        binding.spinnerStartYear.setSelection(years.indexOf(startCal.get(Calendar.YEAR).toString()))

        binding.btnSaveRange.setOnClickListener {
            val startDay = binding.spinnerStartDay.selectedItem.toString().toInt()
            val startMonth = binding.spinnerStartMonth.selectedItem.toString().toInt() - 1
            val startYear = binding.spinnerStartYear.selectedItem.toString().toInt()

            val endDay = binding.spinnerEndDay.selectedItem.toString().toInt()
            val endMonth = binding.spinnerEndMonth.selectedItem.toString().toInt() - 1
            val endYear = binding.spinnerEndYear.selectedItem.toString().toInt()

            val selectedStart = Calendar.getInstance().apply {
                set(startYear, startMonth, startDay, 0, 0, 0)
            }.timeInMillis

            val selectedEnd = Calendar.getInstance().apply {
                set(endYear, endMonth, endDay, 23, 59, 59)
            }.timeInMillis

            if (selectedStart > selectedEnd) {
                // Handle error: Start date cannot be after end date
                return@setOnClickListener
            }

            onRangeSelected(selectedStart, selectedEnd)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CustomDateRangePicker"
    }
}
