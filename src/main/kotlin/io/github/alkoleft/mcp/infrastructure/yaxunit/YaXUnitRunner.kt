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

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.alkoleft.mcp.application.actions.test.yaxunit.TestExecutionRequest
import io.github.alkoleft.mcp.application.actions.test.yaxunit.YaXUnitExecutionResult
import io.github.alkoleft.mcp.configuration.properties.ApplicationProperties
import io.github.alkoleft.mcp.configuration.properties.ResolvedYaxUnitLogging
import io.github.alkoleft.mcp.infrastructure.platform.dsl.PlatformDsl
import io.github.alkoleft.mcp.infrastructure.platform.dsl.process.ProcessResult
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.Path

private val logger = KotlinLogging.logger { }

/**
 * Реализация YaXUnitRunner для запуска тестов через 1С:Предприятие
 * Интегрирован со стратегиями построения команд и обработки ошибок
 */
@Component
class YaXUnitRunner(
    private val platformDsl: PlatformDsl,
    private val objectMapper: ObjectMapper,
    private val properties: ApplicationProperties,
) {
    fun executeTests(request: TestExecutionRequest): YaXUnitExecutionResult {
        logger.info { "Запуск выполнения тестов YaXUnit для ${request.javaClass.simpleName}" }

        // Создаем временную конфигурацию
        val (configPath, config, resolvedLogging) = createConfigFile(request)

        // Запускаем тесты через EnterpriseDsl
        logger.debug { "Выполнение тестов" }
        val result = executeTests(configPath = configPath)

        logger.info { "Процесс завершен с кодом выхода: ${result.exitCode}" }

        // Определяем путь к отчету
        val reportPath = Path(config.reportPath)
        if (Files.exists(reportPath)) {
            logger.info { "Отчет о тестах найден по пути: $reportPath" }
        } else {
            logger.warn { "Отчет о тестах не найден по ожидаемому пути ($reportPath)" }
        }

        return YaXUnitExecutionResult(
            commandResult = result,
            configPath = configPath,
            logPath = config.logging.file,
            reportPath = reportPath,
        )
    }

    private fun createConfigFile(request: TestExecutionRequest): Triple<Path, YaXUnitConfig, ResolvedYaxUnitLogging> {
        val tempDir = tempDirectory()
        val resolvedLogging =
            ResolvedYaxUnitLogging.resolve(
                logging = properties.yaxunit.logging,
                connectionString = properties.connection.connectionString,
                tempDirectory = tempDir,
            )
        val reportPath = reportPath(tempDir)

        val config = request.toConfig(reportPath, resolvedLogging)
        config.validate().also {
            if (!it.isValid) {
                logger.warn { "Проверка конфигурации не пройдена: ${it.errors.joinToString(", ")}" }
            }
        }
        val configPath = configPath(tempDir)
        objectMapper.writeValue(configPath.toFile(), config)

        return Triple(configPath, config, resolvedLogging)
    }

    /**
     * Возвращает директорию для временных файлов YaXUnit.
     * Если useWorkPath=true — использует app.workPath/.metr-tests/yaxunit/,
     * иначе — системную временную директорию.
     */
    private fun tempDirectory(): Path? {
        if (!properties.yaxunit.logging.useWorkPath) return null
        val dir = artifactsDirectory(properties.workPath)
        Files.createDirectories(dir)
        return dir
    }

    /**
     * Строит аргументы команды для запуска 1С:Предприятие
     */
    private fun executeTests(configPath: Path): ProcessResult =
        platformDsl
            .enterprise {
                runArguments("RunUnitTests=${configPath.toAbsolutePath()}")
            }.run()

    companion object {
        // On Windows "yaxunit" and the exported extension "YAXUNIT" are the
        // same directory. Keep runtime artifacts outside all source sets.
        internal fun artifactsDirectory(workPath: Path): Path = workPath.resolve(".metr-tests").resolve("yaxunit")

        /**
         * Создаёт путь к файлу конфигурации YaXUnit.
         * Если tempDirectory задан, файл создаётся в нём.
         */
        fun configPath(tempDirectory: Path? = null): Path {
            val dir = tempDirectory ?: Files.createTempDirectory("yaxunit-config-")
            return dir.resolve("yaxunit-config-${UUID.randomUUID()}.json")
        }

        /**
         * Создаёт путь к файлу отчёта jUnit.
         * Если tempDirectory задан, файл создаётся в нём.
         */
        fun reportPath(tempDirectory: Path? = null): String {
            val dir = tempDirectory ?: run {
                val tmp = Files.createTempDirectory("yaxunit-report-")
                tmp
            }
            return dir.resolve("yaxunit-report-${UUID.randomUUID()}.xml").toString()
        }
    }
}
