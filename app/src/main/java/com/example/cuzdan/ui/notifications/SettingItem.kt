package com.example.cuzdan.ui.notifications

data class SettingItem(
    val id: Int,
    val title: String,
    val value: String? = null,
    val hasSwitch: Boolean = false,
    val isSwitchChecked: Boolean = false
)
