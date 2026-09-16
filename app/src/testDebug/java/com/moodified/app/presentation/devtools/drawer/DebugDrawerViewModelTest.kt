package com.moodified.app.presentation.devtools.drawer

import com.moodified.app.core.devtools.DiagnosticsRepository
import com.moodified.app.core.devtools.DiagnosticsSnapshot
import com.moodified.app.core.devtools.PermissionStatus
import com.moodified.app.core.devtools.WorkerStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebugDrawerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeDiagnosticsRepository

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeDiagnosticsRepository()
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is loading with null snapshot`() =
        runTest(dispatcher) {
            val vm = DebugDrawerViewModel(repo)
            val initial = vm.uiState.value
            assertTrue("Expected initial loading=true", initial.isLoading)
            assertNull(initial.snapshot)
        }

    @Test
    fun `emits snapshot when repository publishes`() =
        runTest(dispatcher) {
            val vm = DebugDrawerViewModel(repo)
            val values = mutableListOf<DebugDrawerUiState>()
            val job =
                launch {
                    vm.uiState.collect { values.add(it) }
                }
            dispatcher.scheduler.advanceUntilIdle()
            repo.emit(SAMPLE_SNAPSHOT)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(SAMPLE_SNAPSHOT, values.last().snapshot)
            assertEquals(false, values.last().isLoading)
            job.cancel()
        }

    private companion object {
        val SAMPLE_SNAPSHOT =
            DiagnosticsSnapshot(
                dbRowCounts = mapOf("mood_entries" to 12L, "activity_signals" to 340L),
                workerStatuses = listOf(WorkerStatus("PurgeWorker", "ENQUEUED", 0L)),
                permissionGrants =
                    listOf(PermissionStatus("android.permission.ACTIVITY_RECOGNITION", true)),
                trackerStates = mapOf("activity" to true, "sleep" to false),
            )
    }
}

private class FakeDiagnosticsRepository : DiagnosticsRepository {
    private val flow = MutableStateFlow<DiagnosticsSnapshot?>(null)

    override fun snapshot(): kotlinx.coroutines.flow.Flow<DiagnosticsSnapshot> =
        kotlinx.coroutines.flow.flow {
            flow.collect { snap ->
                if (snap != null) emit(snap)
            }
        }

    suspend fun emit(snap: DiagnosticsSnapshot) {
        flow.value = snap
    }
}
