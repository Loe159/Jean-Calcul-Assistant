package com.jeancalcul.assistant

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun affiche_l_ecran_principal_au_lancement() {
        composeRule.onNodeWithText("Jean Calcul Assistant").assertIsDisplayed()
        composeRule.onNodeWithText("Configuration Hermes").assertIsDisplayed()
        composeRule.onNodeWithText("Conversation texte").assertIsDisplayed()
    }
}
