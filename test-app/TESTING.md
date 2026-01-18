# Eqraa Testing Guide

This document provides comprehensive guidance for running, writing, and maintaining tests for the Eqraa EPUB reader application.

## Quick Start

```bash
# Run all unit tests
./gradlew :test-app:testDebugUnitTest

# Run specific test class
./gradlew :test-app:testDebugUnitTest --tests "com.eqraa.reader.viewmodel.BookshelfViewModelTest"

# Run instrumentation tests (requires emulator/device)
./gradlew :test-app:connectedDebugAndroidTest
```

## Test Structure

```
test-app/src/
├── test/java/com/eqraa/reader/        # Unit tests (JVM)
│   ├── base/                          # Base classes & utilities
│   │   ├── BaseViewModelTest.kt       # ViewModel test base class
│   │   └── TestDispatcherRule.kt      # Coroutine test dispatcher
│   ├── viewmodel/                     # ViewModel tests
│   │   ├── BookshelfViewModelTest.kt
│   │   └── SyncStatusViewModelTest.kt
│   ├── repository/                    # Repository tests
│   │   ├── BookRepositoryTest.kt
│   │   └── ReadingProgressRepositoryTest.kt
│   └── utils/                         # Test utilities
│       ├── TestDataBuilders.kt        # Test data factories
│       └── FakeSupabaseService.kt     # Mock Supabase
│
└── androidTest/java/com/eqraa/reader/ # Instrumentation tests
    └── database/
        └── BooksDaoTest.kt            # Room database tests
```

## Writing Tests

### ViewModel Tests

```kotlin
class MyViewModelTest : BaseViewModelTest() {

    @MockK
    private lateinit var repository: MyRepository
    
    private lateinit var viewModel: MyViewModel

    override fun setUp() {
        super.setUp()
        viewModel = MyViewModel(repository)
    }

    @Test
    fun `given initial state when loading then emits loading state`() = runTest {
        // Given
        every { repository.getData() } returns flowOf(emptyList())

        // When
        viewModel.loadData()
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            assertThat(awaitItem().isLoading).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

### Flow Testing with Turbine

```kotlin
@Test
fun `books flow emits updates`() = runTest {
    viewModel.books.test {
        assertThat(awaitItem()).isEmpty()
        
        // Trigger update
        repository.addBook(testBook)
        
        assertThat(awaitItem()).hasSize(1)
        cancelAndIgnoreRemainingEvents()
    }
}
```

### Test Data Builders

```kotlin
// Create test fixtures with defaults
val book = TestDataBuilders.createTestBook()
val bookmark = TestDataBuilders.createTestBookmark(bookId = 1L)
val highlight = TestDataBuilders.createTestHighlight()

// Override specific fields
val customBook = TestDataBuilders.createTestBook(
    title = "Custom Title",
    author = "Custom Author"
)
```

## Test Dependencies

| Dependency | Purpose |
|------------|---------|
| JUnit 4 | Test framework |
| MockK | Kotlin mocking |
| Turbine | Flow testing |
| Robolectric | Android unit tests |
| AssertJ | Fluent assertions |
| Coroutines Test | Coroutine testing |

## CI/CD

Tests run automatically on:
- Push to `main` or `develop`
- Pull requests to `main` or `develop`

### GitHub Actions Workflow

- **Unit Tests**: Always run
- **Instrumentation Tests**: Run on `main` or with `run-instrumentation` label
- **Coverage Reports**: Generated after unit tests

## Coverage

Generate coverage report:

```bash
./gradlew :test-app:jacocoTestReport
```

Report location: `test-app/build/reports/jacoco/html/index.html`

## Best Practices

1. **Use Given-When-Then structure** for clarity
2. **Test one behavior per test** method
3. **Use descriptive test names** with backticks
4. **Mock external dependencies** (Supabase, network, etc.)
5. **Test edge cases** and error scenarios
6. **Avoid test interdependencies**
7. **Keep tests fast** (< 1 second each)
