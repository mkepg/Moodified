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
                name = "Jetpack Compose",
                version = "BOM 2024.x",
                licenseType = LicenseType.APACHE_2_0,
                url = "https://developer.android.com/jetpack/compose",
            ),
            License(
                name = "Material 3 for Compose",
                version = "1.x",
                licenseType = LicenseType.APACHE_2_0,
                url = "https://m3.material.io",
            ),
            License(
                name = "Compose Material Icons Extended",
                version = "1.x",
                licenseType = LicenseType.APACHE_2_0,
                url = "https://developer.android.com/jetpack/compose",
            ),
            License(
                name = "AndroidX Navigation Compose",
                version = "2.x",
                licenseType = LicenseType.APACHE_2_0,
                url = "https://developer.android.com/jetpack/androidx/releases/navigation",
            ),
            License(
                name = "AndroidX Lifecycle",
                version = "2.x",
                licenseType = LicenseType.APACHE_2_0,
                url = "https://developer.android.com/jetpack/androidx/releases/lifecycle",
            ),
            License(
                name = "AndroidX Room",
                version = "2.x",
                licenseType = LicenseType.APACHE_2_0,
                url = "https://developer.android.com/jetpack/androidx/releases/room",
            ),
            License(
                name = "AndroidX Activity Compose",
                version = "1.x",
                licenseType = LicenseType.APACHE_2_0,
                url = "https://developer.android.com/jetpack/androidx/releases/activity",
            ),
            License(
                name = "AndroidX AppCompat",
                version = "1.x",
                licenseType = LicenseType.APACHE_2_0,
                url = "https://developer.android.com/jetpack/androidx/releases/appcompat",
            ),
            License(
                name = "AndroidX Splashscreen",
                version = "1.x",
                licenseType = LicenseType.APACHE_2_0,
                url = "https://developer.android.com/jetpack/androidx/releases/core",
            ),
            License(
                name = "AndroidX WorkManager",
                version = "2.9.0",
                licenseType = LicenseType.APACHE_2_0,
                url = "https://developer.android.com/jetpack/androidx/releases/work",
            ),
            License(
                name = "Hilt",
                version = "2.x",
                licenseType = LicenseType.APACHE_2_0,
                url = "https://dagger.dev/hilt",
            ),
            License(
                name = "Kotlin",
                version = "1.9.x",
                licenseType = LicenseType.APACHE_2_0,
                url = "https://kotlinlang.org",
            ),
            License(
                name = "Kotlinx Coroutines",
                version = "1.x",
                licenseType = LicenseType.APACHE_2_0,
                url = "https://github.com/Kotlin/kotlinx.coroutines",
            ),
            License(
                name = "Lottie for Compose",
                version = "6.x",
                licenseType = LicenseType.APACHE_2_0,
                url = "https://airbnb.io/lottie",
            ),
            License(
                name = "Play Services Location",
                version = "21.x",
                licenseType = LicenseType.APACHE_2_0,
                url = "https://developers.google.com/android/guides/setup",
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
