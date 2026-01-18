/*
 * Copyright 2024 Eqraa. All rights reserved.
 * Unit Tests for UserPreferencesViewModel
 */
package com.eqraa.reader.reader.preferences

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import app.cash.turbine.test
import com.eqraa.reader.base.BaseViewModelTest
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Configurable
import org.readium.r2.navigator.preferences.PreferencesEditor
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferencesViewModelTest : BaseViewModelTest() {

    private lateinit var mockPreferencesManager: PreferencesManager<EpubPreferences>
    private lateinit var mockPreferencesEditor: PreferencesEditor<EpubPreferences>
    private lateinit var preferencesFlow: MutableStateFlow<EpubPreferences>
    private val bookId = 1L

    override fun setUp() {
        super.setUp()
        preferencesFlow = MutableStateFlow(EpubPreferences())
        mockPreferencesManager = mockk(relaxed = true)
        mockPreferencesEditor = mockk(relaxed = true)
        
        every { mockPreferencesManager.preferences } returns preferencesFlow
        every { mockPreferencesEditor.preferences } returns EpubPreferences()
    }

    @Test
    fun `editor flow maps preferences to editor`() = runTest {
        // Given
        val viewModel = UserPreferencesViewModel<org.readium.r2.navigator.epub.EpubSettings, EpubPreferences>(
            viewModelScope = backgroundScope,
            bookId = bookId,
            preferencesManager = mockPreferencesManager,
            createPreferencesEditor = { mockPreferencesEditor }
        )

        // When
        viewModel.editor.test {
            val editor = awaitItem()
            
            // Then
            assertThat(editor).isEqualTo(mockPreferencesEditor)
        }
    }

    @Test
    fun `commit saves preferences from editor`() = runTest {
        // Given
        val job = kotlinx.coroutines.Job()
        val viewModelScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler) + job)
        val viewModel = UserPreferencesViewModel<org.readium.r2.navigator.epub.EpubSettings, EpubPreferences>(
            viewModelScope = viewModelScope,
            bookId = bookId,
            preferencesManager = mockPreferencesManager,
            createPreferencesEditor = { mockPreferencesEditor }
        )
        val newPreferences = EpubPreferences(fontSize = 1.5)
        every { mockPreferencesEditor.preferences } returns newPreferences

        // When
        viewModel.commit()
        
        // Then
        coVerify { mockPreferencesManager.setPreferences(newPreferences) }
        
        // Cleanup
        job.cancel()
    }

    @Test
    fun `bind submits initial preferences`() = runTest {
        // Given
        val controller = Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).create().start()
        val activity = controller.get()
        
        val viewModel = UserPreferencesViewModel<org.readium.r2.navigator.epub.EpubSettings, EpubPreferences>(
            viewModelScope = backgroundScope,
            bookId = bookId,
            preferencesManager = mockPreferencesManager,
            createPreferencesEditor = { mockPreferencesEditor }
        )
        val mockConfigurable = mockk<Configurable<org.readium.r2.navigator.epub.EpubSettings, EpubPreferences>>(relaxed = true)
        val initialPreferences = EpubPreferences(fontSize = 1.2)
        preferencesFlow.value = initialPreferences

        // When
        viewModel.bind(mockConfigurable, activity)
        
        advanceUntilIdle()

        // Then
        verify { mockConfigurable.submitPreferences(initialPreferences) }
        
        // Cleanup
        controller.destroy()
    }
}
