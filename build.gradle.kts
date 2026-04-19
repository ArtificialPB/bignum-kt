plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinAllOpen) apply false
    alias(libs.plugins.kotest) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlinxBenchmark) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kover) apply false
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
