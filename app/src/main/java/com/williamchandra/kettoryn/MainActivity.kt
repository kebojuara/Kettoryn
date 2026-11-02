package com.williamchandra.kettoryn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.williamchandra.kettoryn.ui.KettorynViewModel
import com.williamchandra.kettoryn.ui.navigation.KettorynAppShell
import com.williamchandra.kettoryn.ui.theme.KettorynTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as KettorynApplication).container

        setContent {
            val viewModel: KettorynViewModel = viewModel(
                factory = KettorynViewModel.provideFactory(
                    workoutRepository = appContainer.workoutRepository,
                    preferencesRepository = appContainer.preferencesRepository
                )
            )
            val preferences by viewModel.preferencesState.collectAsStateWithLifecycle()

            KettorynTheme(themeMode = preferences.themeMode) {
                KettorynAppShell(viewModel = viewModel)
            }
        }
    }
}
