package com.nishany.android

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

/** Deterministic UI only; this never authenticates against a real account. */
class SignInUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun loginNeedsBothFieldsAndClearsPassword() {
        var submitted: Pair<String, String>? = null
        compose.setContent { NishanyTheme { SignInForm(false, { email, password -> submitted = email to password }, {}, {}) } }
        compose.onNodeWithText("Sign in", useUnmergedTree = true).assertIsNotEnabled()
        compose.onNodeWithText("Email").performTextInput("student@example.invalid")
        compose.onNodeWithText("Password").performTextInput("fixture-only")
        compose.onNodeWithText("Sign in", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals("student@example.invalid" to "fixture-only", submitted) }
        compose.onNodeWithText("Sign in", useUnmergedTree = true).assertIsNotEnabled()
    }
    @Test fun arabicLabelsAndBusyState() {
        compose.setContent {
            CompositionLocalProvider(LocalArabic provides true, LocalLayoutDirection provides LayoutDirection.Rtl) {
                NishanyTheme { SignInForm(true, { _, _ -> }, {}, {}) }
            }
        }
        compose.onNodeWithText("البريد الإلكتروني").assertExists()
        compose.onNodeWithText("دخول", useUnmergedTree = true).assertIsNotEnabled()
    }
}
