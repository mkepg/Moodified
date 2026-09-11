package com.moodified.app.domain.usecase.privacy

import com.moodified.app.domain.exporter.UserDataExporter
import javax.inject.Inject

class ExportUserDataUseCase
    @Inject
    constructor(
        private val exporter: UserDataExporter,
    ) {
        suspend operator fun invoke(): UserDataExporter.ExportResult = exporter.export()
    }
