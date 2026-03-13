package com.example.cuzdan.ui.reports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.navigation.fragment.findNavController
import com.example.cuzdan.R
import com.example.cuzdan.databinding.FragmentReportsBinding

class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!

    private var isHidden = false
    private lateinit var adapter: ReportCategoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        
        setupUI()
        setupRecyclerView()
        
        return binding.root
    }

    private fun setupUI() {
        binding.imageHideShow.setOnClickListener {
            isHidden = !isHidden
            updateHideShowUI()
        }

        binding.layoutDailyChange.setOnClickListener {
            findNavController().navigate(R.id.navigation_profit_loss_chart)
        }

        // Mock switching logic
        binding.btnPrevPortfolio.setOnClickListener { /* Switch to prev portfolio */ }
        binding.btnNextPortfolio.setOnClickListener { /* Switch to next portfolio */ }
        
        // Initial setup for amounts
        updateHideShowUI()
    }

    private fun updateHideShowUI() {
        if (isHidden) {
            binding.imageHideShow.setImageResource(R.drawable.ic_eye_off)
            binding.textTotalAmount.text = "***** TL"
            binding.textDailyChangeAbs.text = "*****"
            binding.textDailyChangePerc.text = "*****"
        } else {
            binding.imageHideShow.setImageResource(R.drawable.ic_eye_on)
            binding.textTotalAmount.text = "-60 TL"
            binding.textDailyChangeAbs.text = "- 1.643"
            binding.textDailyChangePerc.text = "%103,8"
        }
        
        if (::adapter.isInitialized) {
            adapter.setHidden(isHidden)
        }
    }

    private fun setupRecyclerView() {
        val categories = listOf(
            ReportCategory("BIST", "125.000 TL", "%13,9", "15.000 TL", false),
            ReportCategory("Emtia", "10.220 TL", "%32,1", "- 77 TL", true)
        )

        adapter = ReportCategoryAdapter(categories, isHidden)
        binding.recyclerReportCategories.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@ReportsFragment.adapter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
