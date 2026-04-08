package com.example.cuzdan.ui.alerts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cuzdan.R
import com.example.cuzdan.databinding.FragmentAlertsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AlertsFragment : Fragment() {

    private var _binding: FragmentAlertsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AlertsViewModel by viewModels()
    private val activeAdapter = PriceAlertListAdapter()
    private val triggeredAdapter = PriceAlertListAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAlertsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textTitle.text = getString(R.string.title_alerts)

        binding.recyclerAlerts.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerAlerts.adapter = activeAdapter

        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.tab_active_alerts))
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.tab_triggered_alerts))

        binding.tabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                val isActive = tab.position == 0
                binding.recyclerAlerts.adapter = if (isActive) activeAdapter else triggeredAdapter
                renderEmptyState(isActive)
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })

        observe()
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    activeAdapter.submit(state.active)
                    triggeredAdapter.submit(state.triggered)
                    renderEmptyState(binding.tabs.selectedTabPosition == 0, state.active.size, state.triggered.size)
                }
            }
        }
    }

    private fun renderEmptyState(isActiveTab: Boolean, activeCount: Int? = null, triggeredCount: Int? = null) {
        val active = activeCount ?: (activeAdapter.itemCount)
        val triggered = triggeredCount ?: (triggeredAdapter.itemCount)
        val isEmpty = if (isActiveTab) active == 0 else triggered == 0

        binding.textEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerAlerts.visibility = if (isEmpty) View.GONE else View.VISIBLE

        binding.textEmpty.text = if (isActiveTab) {
            getString(R.string.empty_active_alerts)
        } else {
            getString(R.string.empty_triggered_alerts)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

