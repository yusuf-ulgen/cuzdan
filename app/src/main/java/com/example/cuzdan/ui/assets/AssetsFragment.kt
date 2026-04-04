package com.example.cuzdan.ui.assets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cuzdan.R
import com.example.cuzdan.databinding.FragmentAssetsBinding
import com.example.cuzdan.util.PreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AssetsFragment : Fragment() {

    @Inject lateinit var prefManager: PreferenceManager

    private var _binding: FragmentAssetsBinding? = null
    private val binding
        get() = _binding!!

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAssetsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (prefManager.getSelectedPortfolioId() == -1L) {
            showPortfolioWarning()
        }

        setupRecyclerView()
    }

    private fun showPortfolioWarning() {
        AlertDialog.Builder(requireContext())
                .setTitle(R.string.total_portfolios_warning_title)
                .setMessage(R.string.total_portfolios_warning_message)
                .setPositiveButton(R.string.dialog_confirm) { _, _ -> findNavController().popBackStack() }
                .setCancelable(false)
                .show()
    }

    private fun setupRecyclerView() {
        val assetTypes =
                listOf(
                        AssetType(1, getString(R.string.asset_type_cash), "NAKIT", R.drawable.nakit),
                        AssetType(2, getString(R.string.asset_type_stocks), "BIST", R.drawable.borsa),
                        AssetType(3, getString(R.string.asset_type_commodity), "EMTIA", R.drawable.emtia),
                        AssetType(4, getString(R.string.asset_type_currency), "DOVIZ", R.drawable.doviz),
                        AssetType(5, getString(R.string.asset_type_fund), "FON", R.drawable.fon),
                        AssetType(6, getString(R.string.asset_type_crypto), "KRIPTO", R.drawable.kripto)
                )

        binding.recyclerAssetTypes.apply {
            layoutManager = LinearLayoutManager(context)
            adapter =
                    AssetTypeAdapter(assetTypes) { selected ->
                        val bundle = bundleOf("assetType" to selected.assetType)
                        findNavController()
                                .navigate(
                                        R.id.action_navigation_assets_to_navigation_symbol_search,
                                        bundle
                                )
                    }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
