/*
 * Copyright 2024 Eqraa. All rights reserved.
 * Test Infrastructure - Base ViewModel Test Class
 */
package com.eqraa.reader.base

import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule

/**
 * Base class for ViewModel unit tests.
 * 
 * Provides:
 * - TestDispatcher for coroutine testing
 * - MockK annotations initialization
 * - Automatic mock cleanup after each test
 * 
 * Usage:
 * ```kotlin
 * class MyViewModelTest : BaseViewModelTest() {
 *     @MockK
 *     private lateinit var repository: MyRepository
 *     
 *     private lateinit var viewModel: MyViewModel
 *     
 *     override fun setUp() {
 *         super.setUp()
 *         viewModel = MyViewModel(repository)
 *     }
 *     
 *     @Test
 *     fun `test something`() = runTest {
 *         // Given
 *         every { repository.getData() } returns flowOf(listOf())
 *         
 *         // When
 *         val result = viewModel.data.first()
 *         
 *         // Then
 *         assertThat(result).isEmpty()
 *     }
 * }
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class BaseViewModelTest {

    @get:Rule
    val testDispatcherRule = TestDispatcherRule()

    /**
     * Set up test fixtures before each test.
     * Override this method to initialize your test subjects.
     * Always call super.setUp() first.
     */
    @Before
    open fun setUp() {
        MockKAnnotations.init(this, relaxed = true)
    }

    /**
     * Clean up after each test.
     * Override this method for custom cleanup.
     * Always call super.tearDown() last.
     */
    @After
    open fun tearDown() {
        clearAllMocks()
    }

    /**
     * Convenience extension for running coroutine tests.
     * Alias for kotlinx.coroutines.test.runTest.
     */
    protected fun runViewModelTest(
        testBody: suspend kotlinx.coroutines.test.TestScope.() -> Unit
    ) = runTest(testBody = testBody)
}
