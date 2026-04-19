import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinAllOpen) apply false
    alias(libs.plugins.kotest) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlinxBenchmark) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.ktlint)
}

val ktlintToolVersion = libs.versions.ktlint.tool.get()

fun Project.configureKtlint() {
    extensions.configure<KtlintExtension> {
        version = ktlintToolVersion

        reporters {
            reporter(ReporterType.HTML)
            reporter(ReporterType.SARIF)
        }

        filter {
            exclude { it.file.toPath().startsWith(layout.buildDirectory.asFile.get().toPath()) }
        }

        additionalEditorconfig.set(
            mapOf(
                "ktlint_code_style" to "intellij_idea",
                "ktlint_standard_comment-spacing" to "disabled",
                "ktlint_standard_discouraged-comment-location" to "disabled",
                "ktlint_standard_property-naming" to "disabled",
                "ktlint_standard_no-wildcard-imports" to "disabled",
                "ktlint_standard_spacing-between-declarations-with-annotations" to "disabled",
                "ktlint_standard_multiline-if-else" to "disabled",
                "ktlint_standard_value-argument-comment" to "disabled",
                "ktlint_standard_value-parameter-comment" to "disabled",
                "ktlint_standard_backing-property-naming" to "disabled",
                "ktlint_standard_function-expression-body" to "disabled",
                "ktlint_standard_class-signature" to "disabled",
                "ktlint_standard_blank-line-between-when-conditions" to "disabled",
            ),
        )
    }

    tasks.configureEach {
        if (name.contains("ktlint", ignoreCase = true)) {
            notCompatibleWithConfigurationCache("org.jlleitschuh.gradle.ktlint tasks are not configuration-cache compatible in this build")
        }
    }
}

configureKtlint()

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configureKtlint()
}

tasks.register("coverageHtml") {
    group = "verification"
    description = "Generates the JVM/common HTML coverage report for the :bignum multiplatform module."
    dependsOn(":bignum:koverHtmlReportJvm")
}

tasks.register("coverageXml") {
    group = "verification"
    description = "Generates the JVM/common XML coverage report for the :bignum multiplatform module."
    dependsOn(":bignum:koverXmlReportJvm")
}

tasks.register("coverage") {
    group = "verification"
    description = "Generates the JVM/common HTML and XML coverage reports for the :bignum multiplatform module."
    dependsOn("coverageHtml", "coverageXml")
}
