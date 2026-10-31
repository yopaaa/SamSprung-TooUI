/*
 * ====================================================================
 * Copyright (c) 2021-2023 AbandonedCart.  All rights reserved.
 *
 * See https://github.com/SamSprung/.github/blob/main/LICENSE#L5
 * ====================================================================
 *
 * The license and distribution terms for any publicly available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution license
 * [including the GNU Public License.] Content not subject to these terms is
 * subject to to the terms and conditions of the Apache License, Version 2.0.
 */

package com.yopa.samsprung.drawer

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class CoverStateAdapter(fragmentActivity: AppCompatActivity) : FragmentStateAdapter(fragmentActivity) {
    private val mFragments: ArrayList<PanelViewFragment> = arrayListOf()
    private val mFragmentIDs: ArrayList<Long> = arrayListOf()
    private var nextUniqueId: Long = 4L

    fun addFragment() : Int {
        val fragment = PanelViewFragment()
        val newId = nextUniqueId++
        mFragments.add(fragment)
        mFragmentIDs.add(newId)
        val position = mFragmentIDs.size + 3
        notifyItemInserted(position)
        return position
    }

    fun removeFragment(position: Int) {
        val internalIndex = position - 4
        if (internalIndex in 0 until mFragments.size) {
            mFragments.removeAt(internalIndex)
            mFragmentIDs.removeAt(internalIndex)
            mFragments.trimToSize()
            mFragmentIDs.trimToSize()
            notifyItemRemoved(position)
        }
    }

    fun getFragment(position: Int) : PanelViewFragment {
        return mFragments[position - 4]
    }

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> NotificationFragment()
            1 -> ClockHomeFragment()
            2 -> AppDrawerFragment()
            3 -> RecentAppsFragment()
            else -> mFragments[position - 4]
        }
    }

    override fun getItemId(position: Int): Long {
        return if (position < 4) position.toLong() else mFragmentIDs[position - 4]
    }

    override fun getItemCount(): Int {
        return mFragmentIDs.size + 4
    }

    override fun containsItem(itemId: Long): Boolean {
        if (itemId in 0L..3L) return true
        return mFragmentIDs.contains(itemId)
    }
}
