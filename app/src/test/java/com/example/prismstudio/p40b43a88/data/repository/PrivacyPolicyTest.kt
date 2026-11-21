package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.domain.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyPolicyTest {
    @Test
    fun everySelectableLanguageHasItsOwnReadableOfflinePolicy() {
        val policies = AppLanguage.selectable.map { PrivacyPolicy.forLanguage(it) }
        assertEquals(policies.size, policies.map { it.title }.distinct().size)
        assertEquals("Privacy policy", PrivacyPolicy.forLanguage(AppLanguage.EN).title)
        for (policy in policies) {
            assertTrue(policy.body.contains("The Reality Team"))
            assertTrue(policy.body.contains("therealityteam2026@gmail.com"))
            assertFalse(policy.body.contains("mailto:"))
            assertFalse(policy.body.contains("##"))
        }
    }
}
