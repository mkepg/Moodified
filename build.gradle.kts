// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.0.1")
        android.set(true)
        ignoreFailures.set(false)
        // Rule overrides live here (not in .editorconfig) because ktlint Gradle plugin 12.1.1
        // runs ktlint in a worker process that does not reliably pick up the project-root
        // .editorconfig at worker startup time. additionalEditorconfig is the only stable path.
        additionalEditorconfig.set(
            mapOf(
                // @Composable functions use PascalCase by convention
                "ktlint_standard_function-naming" to "disabled",
                // Wildcard imports are common in Compose; disable until expanded
                "ktlint_standard_no-wildcard-imports" to "disabled",
                // Line length enforced by detekt; avoid ktlint duplicate
                "ktlint_standard_max-line-length" to "disabled",
                // Backing property names (e.g. _MutableStateFlow) use existing convention
                "ktlint_standard_property-naming" to "disabled",
                // Multi-class files exist; filename rule disabled
                "ktlint_standard_filename" to "disabled",
            ),
        )
    }

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        baseline = rootProject.file("config/detekt/detekt-baseline.xml")
        buildUponDefaultConfig = true
    }
}
