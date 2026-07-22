package com.example.test.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.test.data.BirdSpecies

sealed class Screen {
    object Detector : Screen()
    object History : Screen()
    object Settings : Screen()
    data class SpeciesInfo(val species: BirdSpecies) : Screen()
}

class AppNavController(initialScreen: Screen = Screen.Detector) {
    var currentScreen by mutableStateOf(initialScreen)
        private set
        
    private val backStack = mutableStateListOf<Screen>(initialScreen)

    fun navigateTo(screen: Screen) {
        // If navigating to a main screen, clear the backstack to prevent deep stack build-up
        if (screen is Screen.Detector || screen is Screen.History || screen is Screen.Settings) {
            backStack.clear()
        }
        backStack.add(screen)
        currentScreen = screen
    }

    fun navigateBack(): Boolean {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
            currentScreen = backStack.last()
            return true
        }
        return false
    }
    
    fun canNavigateBack(): Boolean {
        return backStack.size > 1
    }
}
