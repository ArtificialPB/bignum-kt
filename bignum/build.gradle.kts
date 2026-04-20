import org.gradle.api.Action
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotest)
    alias(libs.plugins.androidLibrary)
    `maven-publish`
}

val libtommathDir = projectDir.resolve("src/nativeInterop/libtommath")
val differentialFixtureDir = projectDir.resolve("src/commonTest/resources/differential")
val githubRepoUrl = "https://github.com/ArtificialPB/bignum-kt"
val tommathPinnedDefines = listOf(
    "MP_NO_FILE",
    "MP_USE_ENUMS",
    "MP_64BIT",
)
val tommathPinnedCompilerOpts = tommathPinnedDefines.map { "-D$it" }

data class TommathAppleTarget(val sdk: String, val clangTarget: String)

val pinnedTommathTargets = mapOf(
    "macos_arm64" to TommathAppleTarget("macosx", "arm64-apple-macos"),
    "ios_arm64" to TommathAppleTarget("iphoneos", "arm64-apple-ios"),
    "ios_x64" to TommathAppleTarget("iphonesimulator", "x86_64-apple-ios-simulator"),
    "ios_simulator_arm64" to TommathAppleTarget("iphonesimulator", "arm64-apple-ios-simulator"),
)

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    applyDefaultHierarchyTemplate()

    jvm()
    androidTarget {
        publishLibraryVariants("release")
    }

    macosArm64()
    iosArm64()
    iosX64()
    iosSimulatorArm64()

    targets.withType<KotlinNativeTarget>().configureEach {
        check(konanTarget.name in pinnedTommathTargets) {
            "Native target '$name' (${konanTarget.name}) is not pinned for LibTomMath. " +
                "Add an explicit entry to pinnedTommathTargets before enabling it."
        }
        compilations.getByName("main") {
            cinterops {
                val tommath by creating {
                    defFile(project.file("src/nativeMain/cinterop/tommath.def"))
                    includeDirs.allHeaders(
                        projectDir.resolve("src/nativeMain/cinterop"),
                        libtommathDir,
                    )
                    compilerOpts(*tommathPinnedCompilerOpts.toTypedArray())
                    extraOpts("-libraryPath", layout.buildDirectory.get().dir("tmp/libs/${konanTarget.name}"))
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotest.framework.engine)
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotest.property)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        val jvmAndroidMain by creating {
            dependsOn(commonMain)
        }

        val jvmMain by getting {
            dependsOn(jvmAndroidMain)
        }

        val androidMain by getting {
            dependsOn(jvmAndroidMain)
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.kotest.runner.junit5)
            }
        }

        val nativeMain by getting {
            dependencies {
            }
        }
    }
}

val configurePublishingRepositories: Action<RepositoryHandler> = Action {
    maven {
        name = "localStaging"
        url = uri(rootProject.layout.buildDirectory.dir("staging-deploy"))
    }
    mavenLocal()
}

val configurePom = Action<MavenPom> {
    name.set("bignum-kt")
    description.set("High-performance Kotlin Multiplatform BigInteger library with JVM semantics across JVM, Android, and Apple native.")
    url.set(githubRepoUrl)

    licenses {
        license {
            name.set("Apache-2.0 License")
            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            distribution.set("repo")
        }
    }

    developers {
        developer {
            id.set("artificialpb")
            name.set("ArtificialPB")
            organization.set("ArtificialPB")
            organizationUrl.set("https://github.com/ArtificialPB")
        }
    }

    scm {
        url.set(githubRepoUrl)
        connection.set("scm:git:git://github.com/ArtificialPB/bignum-kt.git")
        developerConnection.set("scm:git:ssh://git@github.com/ArtificialPB/bignum-kt.git")
    }
}

val dokkaGeneratePublicationHtml by tasks.getting

val javadocJar by tasks.registering(Jar::class) {
    dependsOn(dokkaGeneratePublicationHtml)
    archiveClassifier.set("javadoc")
    from(layout.buildDirectory.dir("dokka/html"))
}

extensions.configure<PublishingExtension> {
    repositories(configurePublishingRepositories)

    publications.withType(MavenPublication::class.java).configureEach {
        artifact(javadocJar)
        pom(configurePom)
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("bignum.differential.fixtureDir", differentialFixtureDir.absolutePath)
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}

tasks.withType<KotlinNativeTest>().configureEach {
    environment("BIGNUM_DIFFERENTIAL_FIXTURE_DIR", differentialFixtureDir.absolutePath)
}

tasks.register<JavaExec>("generateDifferentialFixtures") {
    group = "verification"
    description = "Generates the checked-in JVM differential fuzz corpus."
    dependsOn("jvmTestClasses")
    classpath(
        configurations.getByName("jvmTestRuntimeClasspath"),
        files(
            layout.buildDirectory.dir("classes/kotlin/jvm/test"),
            layout.buildDirectory.dir("processedResources/jvm/test"),
            layout.buildDirectory.dir("resources/test"),
        ),
    )
    mainClass.set("io.github.artificialpb.bignum.DifferentialFixtureGeneratorMainKt")
    workingDir = projectDir
}

android {
    namespace = "io.github.artificialpb.bignum"
    compileSdk = 35
    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Build LibTomMath from source for each native target

abstract class BuildTommathTask : DefaultTask() {
    @get:Input abstract val sdk: Property<String>
    @get:Input abstract val clangTarget: Property<String>
    @get:Input abstract val compilerOpts: ListProperty<String>
    @get:InputDirectory abstract val sourceDir: DirectoryProperty
    @get:OutputFile abstract val outputLib: RegularFileProperty
    @get:Internal abstract val buildDir: DirectoryProperty

    @get:Inject abstract val execOps: ExecOperations
    @get:Inject abstract val providerFactory: ProviderFactory

    @TaskAction
    fun build() {
        val sysrootResult = providerFactory.exec {
            commandLine("xcrun", "--sdk", sdk.get(), "--show-sdk-path")
        }
        val sysroot = sysrootResult.standardOutput.asText.get().trim()

        // Copy sources to isolated build dir to avoid parallel make races
        val workDir = buildDir.get().asFile
        workDir.deleteRecursively()
        sourceDir.get().asFile.copyRecursively(workDir) { _, _ -> OnErrorAction.SKIP }
        // Remove any stale build artifacts from the copy
        workDir.walk().filter { it.extension in listOf("o", "a") }.forEach { it.delete() }

        execOps.exec {
            executable = "make"
            workingDir = workDir
            args("libtommath.a")
            environment["ARFLAGS"] = "rcs"
            environment["CFLAGS"] = buildList {
                add("-O2")
                addAll(compilerOpts.get())
                add("--target=${clangTarget.get()}")
                add("-isysroot")
                add(sysroot)
                add("-Wno-unused-but-set-variable")
            }.joinToString(" ")
            environment["CC"] = "clang"
        }

        val dest = outputLib.get().asFile
        dest.parentFile.mkdirs()
        workDir.resolve("libtommath.a").copyTo(dest, overwrite = true)
    }
}

pinnedTommathTargets.forEach { (konanName, appleTarget) ->
    val capitalized = konanName.split("_").joinToString("") { it.replaceFirstChar(Char::uppercase) }

    val buildTask = tasks.register<BuildTommathTask>("buildTommath$capitalized") {
        sdk.set(appleTarget.sdk)
        clangTarget.set(appleTarget.clangTarget)
        compilerOpts.set(tommathPinnedCompilerOpts)
        sourceDir.set(libtommathDir)
        buildDir.set(layout.buildDirectory.dir("tmp/tommath-build/$konanName"))
        outputLib.set(layout.buildDirectory.file("tmp/libs/$konanName/libtommath.a"))
    }

    tasks.named<CInteropProcess>("cinteropTommath$capitalized") {
        dependsOn(buildTask)
    }
}
