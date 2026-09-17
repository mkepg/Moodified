package com.moodified.app.presentation.support.data

enum class LicenseType {
    APACHE_2_0,
    MIT,
    BSD_3_CLAUSE,
}

data class License(
    val name: String,
    val version: String,
    val licenseType: LicenseType,
    val url: String,
)

object Licenses {
    val all: List<License> =
        listOf(
            License(
                "Jetpack Compose",
                "BOM 2024.x",
                LicenseType.APACHE_2_0,
                "https://developer.android.com/jetpack/compose",
            ),
            License(
                "Material 3 for Compose",
                "1.x",
                LicenseType.APACHE_2_0,
                "https://m3.material.io",
            ),
            License(
                "Compose Material Icons Extended",
                "1.x",
                LicenseType.APACHE_2_0,
                "https://developer.android.com/jetpack/compose",
            ),
            License(
                "AndroidX Navigation Compose",
                "2.x",
                LicenseType.APACHE_2_0,
                "https://developer.android.com/jetpack/androidx/releases/navigation",
            ),
            License(
                "AndroidX Lifecycle",
                "2.x",
                LicenseType.APACHE_2_0,
                "https://developer.android.com/jetpack/androidx/releases/lifecycle",
            ),
            License(
                "AndroidX Room",
                "2.x",
                LicenseType.APACHE_2_0,
                "https://developer.android.com/jetpack/androidx/releases/room",
            ),
            License(
                "AndroidX Activity Compose",
                "1.x",
                LicenseType.APACHE_2_0,
                "https://developer.android.com/jetpack/androidx/releases/activity",
            ),
            License(
                "AndroidX AppCompat",
                "1.x",
                LicenseType.APACHE_2_0,
                "https://developer.android.com/jetpack/androidx/releases/appcompat",
            ),
            License(
                "AndroidX Splashscreen",
                "1.x",
                LicenseType.APACHE_2_0,
                "https://developer.android.com/jetpack/androidx/releases/core",
            ),
            License(
                "AndroidX WorkManager",
                "2.9.0",
                LicenseType.APACHE_2_0,
                "https://developer.android.com/jetpack/androidx/releases/work",
            ),
            License(
                "Hilt",
                "2.x",
                LicenseType.APACHE_2_0,
                "https://dagger.dev/hilt",
            ),
            License(
                "Kotlin",
                "1.9.x",
                LicenseType.APACHE_2_0,
                "https://kotlinlang.org",
            ),
            License(
                "Kotlinx Coroutines",
                "1.x",
                LicenseType.APACHE_2_0,
                "https://github.com/Kotlin/kotlinx.coroutines",
            ),
            License(
                "Lottie for Compose",
                "6.x",
                LicenseType.APACHE_2_0,
                "https://airbnb.io/lottie",
            ),
            License(
                "Play Services Location",
                "21.x",
                LicenseType.APACHE_2_0,
                "https://developers.google.com/android/guides/setup",
            ),
        )

    fun textFor(type: LicenseType): String =
        when (type) {
            LicenseType.APACHE_2_0 -> APACHE_2_0_TEXT
            LicenseType.MIT -> MIT_TEXT
            LicenseType.BSD_3_CLAUSE -> BSD_3_CLAUSE_TEXT
        }

    private const val APACHE_2_0_TEXT =
        "Licensed under the Apache License, Version 2.0 (the \"License\"); you may not use this " +
            "file except in compliance with the License. You may obtain a copy of the License at " +
            "http://www.apache.org/licenses/LICENSE-2.0. Unless required by applicable law or agreed to " +
            "in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, " +
            "WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License " +
            "for the specific language governing permissions and limitations under the License."

    private const val MIT_TEXT =
        "Permission is hereby granted, free of charge, to any person obtaining a copy of this software " +
            "and associated documentation files (the \"Software\"), to deal in the Software without " +
            "restriction, including without limitation the rights to use, copy, modify, merge, publish, " +
            "distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom " +
            "the Software is furnished to do so, subject to the following conditions: The above " +
            "copyright notice and this permission notice shall be included in all copies or substantial " +
            "portions of the Software. THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND."

    private const val BSD_3_CLAUSE_TEXT =
        "Redistribution and use in source and binary forms, with or without modification, are permitted " +
            "provided that the conditions of the BSD 3-Clause License are met. THIS SOFTWARE IS PROVIDED " +
            "BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS \"AS IS\" AND ANY EXPRESS OR IMPLIED WARRANTIES " +
            "ARE DISCLAIMED. See https://opensource.org/licenses/BSD-3-Clause for the full text."
}
