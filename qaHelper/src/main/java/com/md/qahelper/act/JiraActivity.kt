package com.md.qahelper.act

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.md.qahelper.databinding.QalActivityJiraBinding
import com.md.qahelper.fragment.CreateJiraFragment
import com.md.qahelper.fragment.SearchJiraFragment
import com.md.qahelper.util.Utils

/**
 * Jira 관련 작업을 위한 Activity
 * - TabLayout + ViewPager2 구조
 * - 첫 번째 탭: 지라 생성
 * - 두 번째 탭: 지라 조회
 *
 * Created on 2025. 12. 23.
 */
class JiraActivity : FragmentActivity() {

    private val binding by lazy { QalActivityJiraBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        Utils.applyWindowInsetsPadding(binding.root)
        Utils.setSystemBarsBlack(this)

        setupViewPager()
        setListeners()
    }

    private fun setupViewPager() {
        val adapter = JiraPagerAdapter(this)
        binding.viewPager.adapter = adapter

        // TabLayout과 ViewPager2 연결
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "이슈 등록"
                1 -> "조회 / 파일첨부"
                else -> ""
            }
        }.attach()
    }

    private fun setListeners() {
        binding.ivClose.setOnClickListener {
            finish()
        }
    }

    /**
     * JiraActivity의 ViewPager2 Adapter
     */
    private inner class JiraPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> CreateJiraFragment()
                1 -> SearchJiraFragment()
                else -> throw IllegalArgumentException("Invalid position: $position")
            }
        }
    }
}
