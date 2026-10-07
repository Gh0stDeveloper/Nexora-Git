package com.nexora.git.baselineprofile

import androidx.benchmark.macro.BaselineProfileRule
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkRule
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateStartupProfile() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
    }

    companion object {
        private const val PACKAGE_NAME = "com.nexora.git"
    }
}

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(
            StartupTimingMetric(),
            FrameTimingMetric(),
        ),
        compilationMode = CompilationMode.Partial(
            baselineProfileMode = CompilationMode.Partial.BaselineProfileMode.Require,
        ),
        iterations = 5,
        startupMode = StartupMode.COLD,
        setupBlock = {
            pressHome()
        },
        measureBlock = {
            startActivityAndWait()
        },
    )

    companion object {
        private const val PACKAGE_NAME = "com.nexora.git"
    }
}
