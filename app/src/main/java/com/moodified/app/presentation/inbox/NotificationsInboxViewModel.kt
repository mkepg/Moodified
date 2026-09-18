package com.moodified.app.presentation.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodified.app.domain.model.notification.NotificationRecord
import com.moodified.app.domain.repository.NotificationHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class NotificationsInboxUiState(
    val groups: List<DayGroup> = emptyList(),
    val isEmpty: Boolean = true,
) {
    data class DayGroup(
        val date: LocalDate,
        val records: List<NotificationRecord>,
    )
}

@HiltViewModel
class NotificationsInboxViewModel
    @Inject
    constructor(
        private val repository: NotificationHistoryRepository,
    ) : ViewModel() {
        val uiState: StateFlow<NotificationsInboxUiState> =
            repository.observeAll()
                .map { records -> records.toUiState() }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = NotificationsInboxUiState(),
                )

        fun markRead(id: Long) {
            viewModelScope.launch { repository.markRead(id) }
        }

        fun markAllRead() {
            viewModelScope.launch { repository.markAllRead() }
        }

        fun dismiss(id: Long) {
            viewModelScope.launch { repository.dismiss(id) }
        }

        fun dismissAll() {
            viewModelScope.launch { repository.dismissAll() }
        }
    }

private fun List<NotificationRecord>.toUiState(): NotificationsInboxUiState {
    if (isEmpty()) return NotificationsInboxUiState(isEmpty = true)
    val zone = ZoneId.systemDefault()
    val grouped =
        groupBy { record ->
            Instant.ofEpochMilli(record.deliveredAt).atZone(zone).toLocalDate()
        }
            .toSortedMap(compareByDescending { it })
            .map { (date, records) -> NotificationsInboxUiState.DayGroup(date, records) }
    return NotificationsInboxUiState(groups = grouped, isEmpty = false)
}
