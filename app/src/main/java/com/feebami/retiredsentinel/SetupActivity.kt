package com.feebami.retiredsentinel

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.feebami.retiredsentinel.databinding.ActivitySetupBinding
import com.google.android.material.tabs.TabLayoutMediator

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    lateinit var identityDetector: IdentityDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.load(this)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        identityDetector = IdentityDetector(this)

        binding.viewPager.adapter = SetupPagerAdapter(this)

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Settings"
                1 -> "Upload Images"
                else -> ""
            }
        }.attach()

        binding.btnStart.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        identityDetector.close()
    }
}
