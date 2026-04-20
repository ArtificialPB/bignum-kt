import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
import org.jreleaser.gradle.plugin.dsl.deploy.maven.MavenDeployer
import org.jreleaser.model.Active

plugins {
    base
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinAllOpen) apply false
    alias(libs.plugins.kotest) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlinxBenchmark) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.jreleaser)
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

allprojects {
    group = rootProject.group
    version = rootProject.version
}

val ktlintToolVersion = libs.versions.ktlint.tool.get()
val mavenGroupId = group.toString()
val pomDescription = "High-performance Kotlin Multiplatform BigInteger library with JVM semantics across JVM, Android, and Apple native."
val githubRepoUrl = "https://github.com/ArtificialPB/bignum-kt"

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

jreleaser {
    signing {
        active.set(Active.ALWAYS)
        armored.set(true)
        verify.set(true)
    }

    release {
        github {
            enabled.set(true)
            skipRelease.set(true)
            skipTag.set(true)
            token.set("dummy")
        }
    }

    project {
        description.set(pomDescription)
        links {
            homepage.set(githubRepoUrl)
        }
        license.set("Apache-2.0")
        inceptionYear.set("2026")
        authors.set(listOf("ArtificialPB"))
    }

    val stagingDir = layout.buildDirectory.dir("staging-deploy")

    fun MavenDeployer.configureBignumOverrides() {
        listOf(
            "bignum-android",
            "bignum-iosarm64",
            "bignum-iossimulatorarm64",
            "bignum-iosx64",
            "bignum-macosarm64",
        ).forEach { id ->
            artifactOverride {
                groupId = mavenGroupId
                artifactId = id
                jar.set(false)
                sourceJar.set(false)
                javadocJar.set(false)
                verifyPom.set(false)
            }
        }
    }

    deploy {
        maven {
            mavenCentral {
                create("release-deploy") {
                    active.set(Active.RELEASE)
                    url.set("https://central.sonatype.com/api/v1/publisher")

                    applyMavenCentralRules.set(true)
                    stagingRepository(stagingDir.get().asFile.absolutePath)

                    username.set(System.getenv("MAVEN_CENTRAL_USERNAME"))
                    password.set(System.getenv("MAVEN_CENTRAL_PASSWORD"))

                    configureBignumOverrides()
                }
            }

            nexus2 {
                create("snapshot-deploy") {
                    active.set(Active.SNAPSHOT)

                    url.set("https://central.sonatype.com/repository/maven-snapshots/")
                    snapshotUrl.set("https://central.sonatype.com/repository/maven-snapshots/")

                    applyMavenCentralRules.set(true)
                    snapshotSupported.set(true)
                    closeRepository.set(true)
                    releaseRepository.set(true)
                    stagingRepository(stagingDir.get().asFile.absolutePath)

                    username.set(System.getenv("MAVEN_CENTRAL_USERNAME"))
                    password.set(System.getenv("MAVEN_CENTRAL_PASSWORD"))

                    configureBignumOverrides()
                }
            }
        }
    }
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
