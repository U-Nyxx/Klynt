package com.unyxx.act.manager.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import com.unyxx.act.manager.viewmodel.AppsViewModel
import com.unyxx.act.ui.theme.KlyntTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppsViewModel by lazy {
        @Suppress("UNCHECKED_CAST")
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppsViewModel(applicationContext) as T
            }
        })[AppsViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KlyntTheme {
                val navController = rememberNavController()
                AppNavHost(navController, viewModel)
            }
        }
    }
}
