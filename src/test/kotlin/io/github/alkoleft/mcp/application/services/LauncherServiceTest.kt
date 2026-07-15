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

package io.github.alkoleft.mcp.application.services

import io.github.alkoleft.mcp.application.actions.change.ChangesSet
import io.github.alkoleft.mcp.application.actions.change.SourceSetChanges
import io.github.alkoleft.mcp.application.actions.common.ActionStepResult
import io.github.alkoleft.mcp.application.actions.common.BuildAction
import io.github.alkoleft.mcp.application.actions.common.BuildMode
import io.github.alkoleft.mcp.application.actions.common.BuildResult
import io.github.alkoleft.mcp.application.actions.common.ChangeAnalysisAction
import io.github.alkoleft.mcp.application.actions.common.ChangeAnalysisResult
import io.github.alkoleft.mcp.application.actions.test.yaxunit.ChangeType
import io.github.alkoleft.mcp.configuration.properties.ApplicationProperties
import io.github.alkoleft.mcp.configuration.properties.ProjectFormat
import io.github.alkoleft.mcp.configuration.properties.SourceSet
import io.github.alkoleft.mcp.configuration.properties.SourceSetItem
import io.github.alkoleft.mcp.configuration.properties.SourceSetPurpose
import io.github.alkoleft.mcp.configuration.properties.SourceSetType
import io.github.alkoleft.mcp.infrastructure.platform.dsl.PlatformDsl
import io.github.alkoleft.mcp.infrastructure.storage.HashStorage
import io.github.alkoleft.mcp.infrastructure.storage.SourceSetContext
import io.github.alkoleft.mcp.infrastructure.yaxunit.ReportParser
import io.github.alkoleft.mcp.infrastructure.yaxunit.YaXUnitRunner
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration

class LauncherServiceTest {
    private lateinit var buildAction: BuildAction
    private lateinit var changeAnalysisAction: ChangeAnalysisAction
    private lateinit var platformDsl: PlatformDsl
    private lateinit var reportParser: ReportParser
    private lateinit var yaxUnitRunner: YaXUnitRunner
    private lateinit var properties: ApplicationProperties
    private lateinit var sourceSetsService: SourceSetsService
    private lateinit var launcherService: LauncherService
    private lateinit var hashStorage: HashStorage

    private val configurationItem =
        SourceSetItem(
            path = "cf",
            name = "configuration",
            type = SourceSetType.CONFIGURATION,
            purpose = setOf(SourceSetPurpose.MAIN),
        )
    private val extensionItem =
        SourceSetItem(
            path = "ext/my-ext",
            name = "my-ext",
            type = SourceSetType.EXTENSION,
            purpose = setOf(SourceSetPurpose.MAIN),
        )
    private val testExtensionItem =
        SourceSetItem(
            path = "tests/my-tests",
            name = "my-tests",
            type = SourceSetType.EXTENSION,
            purpose = setOf(SourceSetPurpose.TESTS),
        )

    private val allItems = listOf(configurationItem, extensionItem, testExtensionItem)
    private val designerSourceSet = SourceSet(Path("/base"), allItems)

    private val changeAnalysisResult: ChangeAnalysisResult
        get() {
            val extFile = Path("/base/ext/my-ext/file1.bsl")
            val testFile = Path("/base/tests/my-tests/file2.bsl")
            val extChanges: ChangesSet = mapOf(extFile to Pair(ChangeType.MODIFIED, ""))
            val testChanges: ChangesSet = mapOf(testFile to Pair(ChangeType.NEW, ""))
            val allChanges = extChanges + testChanges
            return ChangeAnalysisResult(
                hasChanges = false,
                changedFiles = allChanges.keys,
                changeTypes = allChanges,
                sourceSetChanges = mapOf(
                    "my-ext" to SourceSetChanges(
                        sourceSetName = "my-ext",
                        sourceSetPath = "ext/my-ext",
                        changedFiles = extChanges.keys,
                        changeTypes = extChanges,
                    ),
                    "my-tests" to SourceSetChanges(
                        sourceSetName = "my-tests",
                        sourceSetPath = "tests/my-tests",
                        changedFiles = testChanges.keys,
                        changeTypes = testChanges,
                    ),
                ),
                steps = listOf(ActionStepResult("Анализ", success = true, duration = Duration.ZERO)),
                timestamp = 1000L,
            )
        }

    @BeforeEach
    fun setUp() {
        buildAction = mockk()
        changeAnalysisAction = mockk()
        platformDsl = mockk()
        reportParser = mockk()
        yaxUnitRunner = mockk()
        properties = mockk(relaxed = true)
        sourceSetsService = mockk()
        hashStorage = mockk(relaxed = true)

        launcherService =
            LauncherService(
                buildAction = buildAction,
                changeAnalysisAction = changeAnalysisAction,
                launchAction = mockk(relaxed = true),
                platformDsl = platformDsl,
                reportParser = reportParser,
                yaxUnitRunner = yaxUnitRunner,
                properties = properties,
                sourceSetsService = sourceSetsService,
            )

        // Common mocks: ensure build pipeline completes after change analysis
        every { properties.format } returns ProjectFormat.DESIGNER
        every { changeAnalysisAction.saveSourceSetState(any(), any(), any(), any()) } returns true
        every { buildAction.runPartial(any(), any(), any(), any()) } returns
            BuildResult(
                message = "OK",
                success = true,
                sourceSet = mapOf("my-ext" to mockk(), "my-tests" to mockk()),
            )
    }

    @Test
    fun `build with FULL mode should scan all source set items`() {
        every { sourceSetsService.getDesignerSourceSet() } returns
            SourceSetContext(
                sourceSet = designerSourceSet,
                hashStorage = hashStorage,
                format = ProjectFormat.DESIGNER,
                isDesignerSource = true,
            )

        val contextSlot = slot<SourceSetContext>()
        every { changeAnalysisAction.run(capture(contextSlot)) } returns changeAnalysisResult

        launcherService.build(mode = BuildMode.FULL)

        val captured = contextSlot.captured
        assertEquals(3, captured.sourceSet.size, "FULL mode должен сканировать все 3 элемента")
        assertEquals(
            setOf(SourceSetType.CONFIGURATION, SourceSetType.EXTENSION),
            captured.sourceSet.map { it.type }.toSet(),
            "FULL mode должен включать CONFIGURATION и EXTENSION",
        )
    }

    @Test
    fun `build with SKIP_MAIN_CONFIGURATION should scan only extensions`() {
        every { sourceSetsService.getDesignerSourceSet() } returns
            SourceSetContext(
                sourceSet = designerSourceSet,
                hashStorage = hashStorage,
                format = ProjectFormat.DESIGNER,
                isDesignerSource = true,
            )

        val contextSlot = slot<SourceSetContext>()
        every { changeAnalysisAction.run(capture(contextSlot)) } returns changeAnalysisResult

        launcherService.build(mode = BuildMode.SKIP_MAIN_CONFIGURATION)

        val captured = contextSlot.captured
        assertEquals(2, captured.sourceSet.size, "SKIP_MAIN_CONFIGURATION должен сканировать только 2 расширения")
        assertTrue(
            captured.sourceSet.none { it.type == SourceSetType.CONFIGURATION },
            "SKIP_MAIN_CONFIGURATION не должен включать основную конфигурацию",
        )
        assertTrue(
            captured.sourceSet.all { it.type == SourceSetType.EXTENSION },
            "SKIP_MAIN_CONFIGURATION должен включать только EXTENSION",
        )
    }
}
