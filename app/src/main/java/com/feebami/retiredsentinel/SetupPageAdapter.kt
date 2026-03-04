package com.feebami.retiredsentinel

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class SetupPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount() = 2
    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> SettingsFragment()
        1 -> UploadImagesFragment()
        else -> throw IllegalArgumentException("Invalid tab position: $position")
    }
}
