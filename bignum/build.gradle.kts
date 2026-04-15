import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotest)
    alias(libs.plugins.androidLibrary)
}

val libtommathDir = projectDir.resolve("src/nativeInterop/libtommath")
val differentialFixtureDir = projectDir.resolve("src/commonTest/resources/differential")

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    applyDefaultHierarchyTemplate()

    jvm()
    androidTarget()

    macosArm64()
    iosArm64()
    iosX64()
    iosSimulatorArm64()

    targets.withType<KotlinNativeTarget>().configureEach {
        compilations.getByName("main") {
            cinterops {
                val tommath by creating {
                    defFile(project.file("src/nativeMain/cinterop/tommath.def"))
                    includeDirs.allHeaders(libtommathDir)
                    extraOpts("-libraryPath", layout.buildDirectory.get().dir("tmp/libs/${konanTarget.name}"))
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
        }

        commonTest.dependencies {
            implementation(libs.kotest.framework.engine)
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotest.property)
            implementation(libs.kotlinx.serialization.json)
        }

        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
        }

        androidMain {
            kotlin.srcDir("src/jvmMain/kotlin")
        }

        nativeMain.dependencies {
        }
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
            environment["CFLAGS"] = "-O2 -DMP_NO_FILE -DMP_USE_ENUMS" +
                " --target=${clangTarget.get()} -isysroot $sysroot -Wno-unused-but-set-variable"
            environment["CC"] = "clang"
        }

        val dest = outputLib.get().asFile
        dest.parentFile.mkdirs()
        workDir.resolve("libtommath.a").copyTo(dest, overwrite = true)
    }
}

data class AppleTarget(val sdk: String, val clangTarget: String)

val appleTargets = mapOf(
    "macos_arm64" to AppleTarget("macosx", "arm64-apple-macos"),
    "ios_arm64" to AppleTarget("iphoneos", "arm64-apple-ios"),
    "ios_x64" to AppleTarget("iphonesimulator", "x86_64-apple-ios-simulator"),
    "ios_simulator_arm64" to AppleTarget("iphonesimulator", "arm64-apple-ios-simulator"),
)

appleTargets.forEach { (konanName, appleTarget) ->
    val capitalized = konanName.split("_").joinToString("") { it.replaceFirstChar(Char::uppercase) }

    val buildTask = tasks.register<BuildTommathTask>("buildTommath$capitalized") {
        sdk.set(appleTarget.sdk)
        clangTarget.set(appleTarget.clangTarget)
        sourceDir.set(libtommathDir)
        buildDir.set(layout.buildDirectory.dir("tmp/tommath-build/$konanName"))
        outputLib.set(layout.buildDirectory.file("tmp/libs/$konanName/libtommath.a"))
    }

    tasks.named<CInteropProcess>("cinteropTommath$capitalized") {
        dependsOn(buildTask)
    }
}
