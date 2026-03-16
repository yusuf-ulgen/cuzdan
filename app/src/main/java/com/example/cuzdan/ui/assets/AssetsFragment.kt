package com.example.cuzdan.ui.assets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cuzdan.R
import com.example.cuzdan.databinding.FragmentAssetsBinding
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController

class AssetsFragment : Fragment() {

    private var _binding: FragmentAssetsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAssetsBinding.inflate(inflater, container, false)
        
        setupRecyclerView()
        
        return binding.root
    }

    private fun setupRecyclerView() {
        val assetTypes = listOf(
            AssetType(1, "TL", "NAKIT", R.drawable.ic_tl),
            AssetType(2, "BIST", "BIST", R.drawable.ic_bist),
            AssetType(3, "Emtia", "ALTIN", R.drawable.ic_reports),
            AssetType(4, "Döviz", "DOVIZ", R.drawable.ic_currency),
            AssetType(5, "Fon", "FON", R.drawable.ic_funds),
            AssetType(6, "Kripto", "KRIPTO", R.drawable.ic_crypto)
        )

        binding.recyclerAssetTypes.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = AssetTypeAdapter(assetTypes) { selected ->
                val bundle = bundleOf("assetType" to selected.assetType)
                findNavController().navigate(R.id.action_navigation_assets_to_navigation_symbol_search, bundle)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
