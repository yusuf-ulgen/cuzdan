package com.example.cuzdan.ui.assets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cuzdan.R
import com.example.cuzdan.databinding.FragmentAssetsBinding

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
            AssetType(1, "TL", R.drawable.ic_tl),
            AssetType(2, "BIST", R.drawable.ic_bist),
            AssetType(3, "Emtia", R.drawable.ic_reports), // Placeholder icon for Emtia
            AssetType(4, "Döviz", R.drawable.ic_currency),
            AssetType(5, "Fon", R.drawable.ic_funds),
            AssetType(6, "Kripto", R.drawable.ic_crypto)
        )

        binding.recyclerAssetTypes.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = AssetTypeAdapter(assetTypes) { selected ->
                // Handle navigation or dialog here
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
