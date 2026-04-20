import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinAllOpen)
    alias(libs.plugins.kotlinxBenchmark)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotest)
}

data class BenchmarkSuite(
    val name: String,
    val includePattern: String,
)

val benchmarkSuites = listOf(
    BenchmarkSuite("arithmetic", "io.github.artificialpb.bignum.benchmark.ArithmeticBenchmark.*"),
    BenchmarkSuite("bitwise", "io.github.artificialpb.bignum.benchmark.BitwiseBenchmark.*"),
    BenchmarkSuite("comparison", "io.github.artificialpb.bignum.benchmark.ComparisonBenchmark.*"),
    BenchmarkSuite("construction", "io.github.artificialpb.bignum.benchmark.ConstructionBenchmark.*"),
    BenchmarkSuite("conversion", "io.github.artificialpb.bignum.benchmark.ConversionBenchmark.*"),
    BenchmarkSuite("numberTheory", "io.github.artificialpb.bignum.benchmark.NumberTheoryBenchmark.*"),
    BenchmarkSuite("range", "io.github.artificialpb.bignum.benchmark.RangeBenchmark.*"),
    BenchmarkSuite("bigDecimalArithmetic", "io.github.artificialpb.bignum.benchmark.BigDecimalArithmeticBenchmark.*"),
    BenchmarkSuite("bigDecimalComparison", "io.github.artificialpb.bignum.benchmark.BigDecimalComparisonBenchmark.*"),
    BenchmarkSuite("bigDecimalConstruction", "io.github.artificialpb.bignum.benchmark.BigDecimalConstructionBenchmark.*"),
    BenchmarkSuite("bigDecimalConversion", "io.github.artificialpb.bignum.benchmark.BigDecimalConversionBenchmark.*"),
    BenchmarkSuite("bigDecimalScale", "io.github.artificialpb.bignum.benchmark.BigDecimalScaleBenchmark.*"),
)

val ionspinBenchmarkSuites = listOf(
    BenchmarkSuite("ionspinArithmetic", "io.github.artificialpb.bignum.benchmark.IonspinArithmeticBenchmark.*"),
    BenchmarkSuite("ionspinBitwise", "io.github.artificialpb.bignum.benchmark.IonspinBitwiseBenchmark.*"),
    BenchmarkSuite("ionspinBigDecimalArithmetic", "io.github.artificialpb.bignum.benchmark.IonspinBigDecimalArithmeticBenchmark.*"),
    BenchmarkSuite("ionspinBigDecimalComparison", "io.github.artificialpb.bignum.benchmark.IonspinBigDecimalComparisonBenchmark.*"),
    BenchmarkSuite("ionspinBigDecimalConstruction", "io.github.artificialpb.bignum.benchmark.IonspinBigDecimalConstructionBenchmark.*"),
    BenchmarkSuite("ionspinBigDecimalConversion", "io.github.artificialpb.bignum.benchmark.IonspinBigDecimalConversionBenchmark.*"),
    BenchmarkSuite("ionspinBigDecimalScale", "io.github.artificialpb.bignum.benchmark.IonspinBigDecimalScaleBenchmark.*"),
    BenchmarkSuite("ionspinComparison", "io.github.artificialpb.bignum.benchmark.IonspinComparisonBenchmark.*"),
    BenchmarkSuite("ionspinConstruction", "io.github.artificialpb.bignum.benchmark.IonspinConstructionBenchmark.*"),
    BenchmarkSuite("ionspinConversion", "io.github.artificialpb.bignum.benchmark.IonspinConversionBenchmark.*"),
    BenchmarkSuite("ionspinNumberTheory", "io.github.artificialpb.bignum.benchmark.IonspinNumberTheoryBenchmark.*"),
)

val mainWarmups = 2
val mainIterations = 3
val mainIterationTimeMs = 1000L
val smokeWarmups = 1
val smokeIterations = 1
val smokeIterationTimeMs = 1000L

kotlin {
    applyDefaultHierarchyTemplate()

    jvm()
    macosArm64()
    iosArm64()
    iosX64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(projects.bignum)
            implementation(libs.kotlinx.benchmark.runtime)
            implementation(libs.ionspin.bignum)
        }

        commonTest.dependencies {
            implementation(libs.kotest.framework.engine)
            implementation(libs.kotest.assertions.core)
        }

        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
        }
    }
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

benchmark {
    targets {
        register("jvm")
        register("macosArm64")
    }

    configurations {
        named("main") {
            warmups = mainWarmups
            iterations = mainIterations
            iterationTime = mainIterationTimeMs
            iterationTimeUnit = "ms"
        }
        register("smoke") {
            warmups = smokeWarmups
            iterations = smokeIterations
            iterationTime = smokeIterationTimeMs
            iterationTimeUnit = "ms"
        }
        benchmarkSuites.forEach { suite ->
            register(suite.name) {
                include(suite.includePattern)
                warmups = mainWarmups
                iterations = mainIterations
                iterationTime = mainIterationTimeMs
                iterationTimeUnit = "ms"
            }
            register("${suite.name}Smoke") {
                include(suite.includePattern)
                warmups = smokeWarmups
                iterations = smokeIterations
                iterationTime = smokeIterationTimeMs
                iterationTimeUnit = "ms"
            }
        }
        ionspinBenchmarkSuites.forEach { suite ->
            register(suite.name) {
                include(suite.includePattern)
                warmups = mainWarmups
                iterations = mainIterations
                iterationTime = mainIterationTimeMs
                iterationTimeUnit = "ms"
            }
            register("${suite.name}Smoke") {
                include(suite.includePattern)
                warmups = smokeWarmups
                iterations = smokeIterations
                iterationTime = smokeIterationTimeMs
                iterationTimeUnit = "ms"
            }
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}

tasks.withType<KotlinNativeTest>().configureEach {
    enabled = false
}

tasks.register("compileAllBenchmarks") {
    group = "verification"
    description = "Compiles benchmark sources for every multiplatform benchmark target."
    dependsOn(
        "compileKotlinJvm",
        "compileKotlinMacosArm64",
        "compileKotlinIosArm64",
        "compileKotlinIosX64",
        "compileKotlinIosSimulatorArm64",
    )
}

// ktlint generates tasks for these source sets automatically, even if we exclude their paths via config
tasks.configureEach {
    if (
        name in setOf(
            "runKtlintCheckOverMacosArm64MacosArm64BenchmarkSourceSet",
            "runKtlintFormatOverMacosArm64MacosArm64BenchmarkSourceSet",
            "ktlintMacosArm64MacosArm64BenchmarkSourceSetCheck",
            "ktlintMacosArm64MacosArm64BenchmarkSourceSetFormat",
        )
    ) {
        enabled = false
    }
}
