package com.moodified.app.core.coordination

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DateSelectionCoordinator
    @Inject
    constructor() {
        private val _selectedDate = MutableStateFlow(LocalDate.now())
        val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

        fun selectDate(date: LocalDate) {
            _selectedDate.value = date
        }
    }
