package com.th.thtestes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.th.thtestes.ui.screens.ClonerHomeScreen
import com.th.thtestes.ui.theme.ClonaTelaTheme
import com.th.thtestes.ui.viewmodel.ClonerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ClonerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClonaTelaTheme {
                ClonerHomeScreen(viewModel = viewModel)
            }
        }
    }
}
