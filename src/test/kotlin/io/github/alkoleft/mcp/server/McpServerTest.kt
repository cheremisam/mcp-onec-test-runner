/*
 * This file is part of METR.
 *
 * Copyright (C) 2025 Aleksey Koryakin <alkoleft@gmail.com> and contributors.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * METR is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * METR is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with METR.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.alkoleft.mcp.server

import io.github.alkoleft.mcp.application.actions.common.BuildMode
import io.github.alkoleft.mcp.application.actions.common.BuildResult
import io.github.alkoleft.mcp.application.actions.common.RunTestResult
import io.github.alkoleft.mcp.application.actions.test.yaxunit.RunAllTestsRequest
import io.github.alkoleft.mcp.application.actions.test.yaxunit.RunModuleTestsRequest
import io.github.alkoleft.mcp.application.actions.test.yaxunit.TestExecutionRequest
import io.github.alkoleft.mcp.application.services.DumpService
import io.github.alkoleft.mcp.application.services.LauncherService
import io.github.alkoleft.mcp.application.services.SyntaxCheckService
import io.github.alkoleft.mcp.configuration.properties.ApplicationProperties
import io.github.alkoleft.mcp.configuration.properties.BuildProperties
import io.github.alkoleft.mcp.infrastructure.log.LogManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.time.Duration

class McpServerTest {
    private lateinit var launcherService: LauncherService
    private lateinit var syntaxCheckService: SyntaxCheckService
    private lateinit var dumpService: DumpService
    private lateinit var properties: ApplicationProperties
    private lateinit var logManager: LogManager
    private lateinit var mcpServer: McpServer

    @BeforeEach
    fun setUp() {
        launcherService = mockk()
        syntaxCheckService = mockk()
        dumpService = mockk()
        properties = mockk(relaxed = true)
        logManager = mockk(relaxed = true)
        mcpServer = McpServer(launcherService, syntaxCheckService, dumpService, properties, logManager)
    }

    private val runTestResult =
        RunTestResult(
            success = true,
            duration = Duration.ZERO,
            message = "OK",
            errors = emptyList(),
            report = null,
            reportPath = null,
            enterpriseLogPath = null,
            logPath = null,
        )

    private val buildResult = BuildResult(message = "OK", success = true)

    // ────────────────────────────────────────────
    // runAllTests — выбор BuildMode
    // ────────────────────────────────────────────

    @Test
    fun `runAllTests with skipMainConfigurationUpdate=true should use SKIP_MAIN_CONFIGURATION`() {
        every { properties.build } returns BuildProperties()
        val requestSlot = slot<TestExecutionRequest>()
        every { launcherService.runTests(capture(requestSlot)) } returns runTestResult

        mcpServer.runAllTests(skipMainConfigurationUpdate = true)

        val captured = requestSlot.captured as RunAllTestsRequest
        assertEquals(BuildMode.SKIP_MAIN_CONFIGURATION, captured.buildMode)
    }

    @Test
    fun `runAllTests with config skipMainConfiguration=true and param null should use SKIP_MAIN_CONFIGURATION`() {
        every { properties.build } returns BuildProperties(skipMainConfiguration = true)
        val requestSlot = slot<TestExecutionRequest>()
        every { launcherService.runTests(capture(requestSlot)) } returns runTestResult

        mcpServer.runAllTests()

        val captured = requestSlot.captured as RunAllTestsRequest
        assertEquals(BuildMode.SKIP_MAIN_CONFIGURATION, captured.buildMode)
    }

    @Test
    fun `runAllTests with config skipMainConfiguration=false and param null should use FULL`() {
        every { properties.build } returns BuildProperties(skipMainConfiguration = false)
        val requestSlot = slot<TestExecutionRequest>()
        every { launcherService.runTests(capture(requestSlot)) } returns runTestResult

        mcpServer.runAllTests()

        val captured = requestSlot.captured as RunAllTestsRequest
        assertEquals(BuildMode.FULL, captured.buildMode)
    }

    @Test
    fun `runAllTests with skipMainConfigurationUpdate=false should use FULL even if config is true`() {
        every { properties.build } returns BuildProperties(skipMainConfiguration = true)
        val requestSlot = slot<TestExecutionRequest>()
        every { launcherService.runTests(capture(requestSlot)) } returns runTestResult

        mcpServer.runAllTests(skipMainConfigurationUpdate = false)

        val captured = requestSlot.captured as RunAllTestsRequest
        assertEquals(BuildMode.FULL, captured.buildMode)
    }

    // ────────────────────────────────────────────
    // runModuleTests — выбор BuildMode
    // ────────────────────────────────────────────

    @Test
    fun `runModuleTests with skipMainConfigurationUpdate=true should use SKIP_MAIN_CONFIGURATION`() {
        every { properties.build } returns BuildProperties()
        val requestSlot = slot<TestExecutionRequest>()
        every { launcherService.runTests(capture(requestSlot)) } returns runTestResult

        mcpServer.runModuleTests(moduleName = "TestModule", skipMainConfigurationUpdate = true)

        val captured = requestSlot.captured as RunModuleTestsRequest
        assertEquals(BuildMode.SKIP_MAIN_CONFIGURATION, captured.buildMode)
    }

    @Test
    fun `runModuleTests with config skipMainConfiguration=true and param null should use SKIP_MAIN_CONFIGURATION`() {
        every { properties.build } returns BuildProperties(skipMainConfiguration = true)
        val requestSlot = slot<TestExecutionRequest>()
        every { launcherService.runTests(capture(requestSlot)) } returns runTestResult

        mcpServer.runModuleTests(moduleName = "TestModule")

        val captured = requestSlot.captured as RunModuleTestsRequest
        assertEquals(BuildMode.SKIP_MAIN_CONFIGURATION, captured.buildMode)
    }

    @Test
    fun `runModuleTests with config skipMainConfiguration=false and param null should use FULL`() {
        every { properties.build } returns BuildProperties(skipMainConfiguration = false)
        val requestSlot = slot<TestExecutionRequest>()
        every { launcherService.runTests(capture(requestSlot)) } returns runTestResult

        mcpServer.runModuleTests(moduleName = "TestModule")

        val captured = requestSlot.captured as RunModuleTestsRequest
        assertEquals(BuildMode.FULL, captured.buildMode)
    }

    @Test
    fun `runModuleTests with skipMainConfigurationUpdate=false should use FULL even if config is true`() {
        every { properties.build } returns BuildProperties(skipMainConfiguration = true)
        val requestSlot = slot<TestExecutionRequest>()
        every { launcherService.runTests(capture(requestSlot)) } returns runTestResult

        mcpServer.runModuleTests(moduleName = "TestModule", skipMainConfigurationUpdate = false)

        val captured = requestSlot.captured as RunModuleTestsRequest
        assertEquals(BuildMode.FULL, captured.buildMode)
    }

    // ────────────────────────────────────────────
    // buildProject — выбор BuildMode
    // ────────────────────────────────────────────

    @Test
    fun `buildProject with skipMainConfigurationUpdate=true should use SKIP_MAIN_CONFIGURATION`() {
        every { properties.build } returns BuildProperties()
        val modeSlot = slot<BuildMode>()
        every { launcherService.build(fullRebuild = any(), mode = capture(modeSlot)) } returns buildResult

        mcpServer.buildProject(skipMainConfigurationUpdate = true)

        assertEquals(BuildMode.SKIP_MAIN_CONFIGURATION, modeSlot.captured)
    }

    @Test
    fun `buildProject with config skipMainConfiguration=true and param null should use SKIP_MAIN_CONFIGURATION`() {
        every { properties.build } returns BuildProperties(skipMainConfiguration = true)
        val modeSlot = slot<BuildMode>()
        every { launcherService.build(fullRebuild = any(), mode = capture(modeSlot)) } returns buildResult

        mcpServer.buildProject()

        assertEquals(BuildMode.SKIP_MAIN_CONFIGURATION, modeSlot.captured)
    }

    @Test
    fun `buildProject with config skipMainConfiguration=false and param null should use FULL`() {
        every { properties.build } returns BuildProperties(skipMainConfiguration = false)
        val modeSlot = slot<BuildMode>()
        every { launcherService.build(fullRebuild = any(), mode = capture(modeSlot)) } returns buildResult

        mcpServer.buildProject()

        assertEquals(BuildMode.FULL, modeSlot.captured)
    }

    @Test
    fun `buildProject with skipMainConfigurationUpdate=false should use FULL even if config is true`() {
        every { properties.build } returns BuildProperties(skipMainConfiguration = true)
        val modeSlot = slot<BuildMode>()
        every { launcherService.build(fullRebuild = any(), mode = capture(modeSlot)) } returns buildResult

        mcpServer.buildProject(skipMainConfigurationUpdate = false)

        assertEquals(BuildMode.FULL, modeSlot.captured)
    }

    // ────────────────────────────────────────────
    // runAllTests — сквозная проверка с реальным ответом
    // ────────────────────────────────────────────

    @Test
    fun `runAllTests with skipMainConfigurationUpdate=true should return success response`() {
        every { properties.build } returns BuildProperties()
        every { launcherService.runTests(any()) } returns runTestResult

        val response = mcpServer.runAllTests(skipMainConfigurationUpdate = true)

        assertEquals(true, response.success)
    }
}
