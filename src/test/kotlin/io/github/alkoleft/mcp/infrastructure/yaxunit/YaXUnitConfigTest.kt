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

package io.github.alkoleft.mcp.infrastructure.yaxunit

import io.github.alkoleft.mcp.application.actions.test.yaxunit.RunAllTestsRequest
import io.github.alkoleft.mcp.application.actions.test.yaxunit.RunModuleTestsRequest
import io.github.alkoleft.mcp.configuration.properties.ResolvedYaxUnitLogging
import io.github.alkoleft.mcp.configuration.properties.YaxUnitLoggingMode
import io.github.alkoleft.mcp.configuration.properties.YaxUnitLoggingProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YaXUnitConfigTest {
    @Test
    fun `runtime artifacts do not pollute YAXUNIT export on case insensitive filesystems`(
        @TempDir workPath: Path,
    ) {
        val exportedExtension = Files.createDirectories(workPath.resolve("YAXUNIT"))
        Files.writeString(exportedExtension.resolve("Configuration.xml"), "metadata")
        val artifacts = Files.createDirectories(YaXUnitRunner.artifactsDirectory(workPath))
        Files.writeString(YaXUnitRunner.configPath(artifacts), "{}")
        Files.writeString(Path.of(YaXUnitRunner.reportPath(artifacts)), "<testsuites/>")

        Files.list(exportedExtension).use { files ->
            assertEquals(listOf("Configuration.xml"), files.map { it.fileName.toString() }.toList())
        }
        assertTrue(
            !artifacts.toString().lowercase().startsWith(exportedExtension.toString().lowercase()),
            "Reports and launch settings must not become Designer metadata changes",
        )
    }

    private val serverConnectionString = "Srvr='rrpk-334';Ref='DemoAI';"
    private val fileConnectionString = "File='C:\\test\\ib';"
    private val unknownConnectionString = "some=unknown"

    // ────────────────────────────────────────────
    // ResolvedYaxUnitLogging.resolve — AUTO mode
    // ────────────────────────────────────────────

    @Test
    fun `AUTO with server connection should resolve to CONSOLE and have no file`() {
        val logging =
            YaxUnitLoggingProperties(mode = YaxUnitLoggingMode.AUTO)
        val resolved = ResolvedYaxUnitLogging.resolve(logging, serverConnectionString, null)

        assertNull(resolved.filePath, "Для Srvr= не должно быть файла лога")
        assertEquals(true, resolved.console)
    }

    @Test
    fun `AUTO with file connection should resolve to FILE and have log file`(
        @TempDir tempDir: Path,
    ) {
        val logging = YaxUnitLoggingProperties(mode = YaxUnitLoggingMode.AUTO)
        val resolved = ResolvedYaxUnitLogging.resolve(logging, fileConnectionString, tempDir)

        assertNotNull(resolved.filePath, "Для File= должен быть файл лога")
        assertTrue(resolved.filePath!!.endsWith(".log"))
        assertEquals(false, resolved.console)
    }

    @Test
    fun `AUTO with unknown connection should resolve to CONSOLE`() {
        val logging =
            YaxUnitLoggingProperties(mode = YaxUnitLoggingMode.AUTO)
        val resolved = ResolvedYaxUnitLogging.resolve(logging, unknownConnectionString, null)

        assertNull(resolved.filePath)
        assertEquals(true, resolved.console)
    }

    @Test
    fun `AUTO Srvr= is case insensitive`() {
        val logging =
            YaxUnitLoggingProperties(mode = YaxUnitLoggingMode.AUTO)
        val resolved = ResolvedYaxUnitLogging.resolve(logging, "srvr='host';ref='test';", null)

        assertNull(resolved.filePath)
        assertEquals(true, resolved.console)
    }

    // ────────────────────────────────────────────
    // ResolvedYaxUnitLogging.resolve — explicit modes
    // ────────────────────────────────────────────

    @Test
    fun `FILE mode always creates log file regardless of connection`(
        @TempDir tempDir: Path,
    ) {
        val logging =
            YaxUnitLoggingProperties(mode = YaxUnitLoggingMode.FILE)
        val resolved = ResolvedYaxUnitLogging.resolve(logging, serverConnectionString, tempDir)

        assertNotNull(resolved.filePath)
        assertEquals(false, resolved.console)
    }

    @Test
    fun `CONSOLE mode never creates log file`() {
        val logging =
            YaxUnitLoggingProperties(mode = YaxUnitLoggingMode.CONSOLE)
        val resolved = ResolvedYaxUnitLogging.resolve(logging, fileConnectionString, null)

        assertNull(resolved.filePath)
        assertEquals(true, resolved.console)
    }

    @Test
    fun `FILE_AND_CONSOLE mode creates log file and enables console`(
        @TempDir tempDir: Path,
    ) {
        val logging =
            YaxUnitLoggingProperties(mode = YaxUnitLoggingMode.FILE_AND_CONSOLE)
        val resolved = ResolvedYaxUnitLogging.resolve(logging, serverConnectionString, tempDir)

        assertNotNull(resolved.filePath)
        assertEquals(true, resolved.console)
    }

    // ────────────────────────────────────────────
    // toConfig — logging configuration propagation
    // ────────────────────────────────────────────

    @Test
    fun `toConfig should set console true and file null for CONSOLE mode`(
        @TempDir tempDir: Path,
    ) {
        val logging =
            YaxUnitLoggingProperties(mode = YaxUnitLoggingMode.CONSOLE)
        val resolved = ResolvedYaxUnitLogging.resolve(logging, "", null)
        val reportPath = YaXUnitRunner.reportPath(tempDir)

        val config = RunAllTestsRequest().toConfig(reportPath, resolved)

        assertNull(config.logging.file)
        assertEquals(true, config.logging.console)
    }

    @Test
    fun `toConfig should set file and console false for FILE mode`(
        @TempDir tempDir: Path,
    ) {
        val logging =
            YaxUnitLoggingProperties(mode = YaxUnitLoggingMode.FILE)
        val resolved = ResolvedYaxUnitLogging.resolve(logging, "", tempDir)
        val reportPath = YaXUnitRunner.reportPath(tempDir)

        val config = RunAllTestsRequest().toConfig(reportPath, resolved)

        assertNotNull(config.logging.file)
        assertTrue(config.logging.file!!.endsWith(".log"))
        assertEquals(false, config.logging.console)
    }

    @Test
    fun `toConfig should propagate level from resolved logging`() {
        val logging =
            YaxUnitLoggingProperties(mode = YaxUnitLoggingMode.CONSOLE, level = "debug")
        val resolved = ResolvedYaxUnitLogging.resolve(logging, "", null)

        val config = RunAllTestsRequest().toConfig("report.xml", resolved)

        assertEquals("debug", config.logging.level)
    }

    // ────────────────────────────────────────────
    // validate — logging warnings
    // ────────────────────────────────────────────

    @Test
    fun `validate should warn when logging is disabled`() {
        val config =
            YaXUnitConfig(
                filter = null,
                reportFormat = "jUnit",
                reportPath = "report.xml",
                closeAfterTests = true,
                showReport = false,
                logging = LoggingConfig(file = null, console = false, level = "info"),
            )
        val result = config.validate()
        assertTrue(result.isValid)
        assertTrue(result.warnings.any { it.contains("Не указан вывод логирования") })
    }

    @Test
    fun `validate should not warn when console logging is enabled`() {
        val config =
            YaXUnitConfig(
                filter = null,
                reportFormat = "jUnit",
                reportPath = "report.xml",
                closeAfterTests = true,
                showReport = false,
                logging = LoggingConfig(file = null, console = true, level = "info"),
            )
        val result = config.validate()
        assertTrue(result.isValid)
        assertTrue(result.warnings.none { it.contains("Не указан вывод логирования") })
    }

    // ────────────────────────────────────────────
    // YaXUnitRunner — workPath creates directory
    // ────────────────────────────────────────────

    @Test
    fun `YaXUnitRunner configPath should create json file`(
        @TempDir tempDir: Path,
    ) {
        val path = YaXUnitRunner.configPath(tempDir)
        Files.createFile(path)
        assertTrue(Files.exists(path))
        assertTrue(path.toString().endsWith(".json"))
    }

    @Test
    fun `YaXUnitRunner reportPath should create xml path`(
        @TempDir tempDir: Path,
    ) {
        val path = YaXUnitRunner.reportPath(tempDir)
        assertTrue(path.endsWith(".xml"))
    }
}
