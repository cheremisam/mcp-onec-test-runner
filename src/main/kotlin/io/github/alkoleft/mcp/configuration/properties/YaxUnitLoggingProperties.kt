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

package io.github.alkoleft.mcp.configuration.properties

import java.nio.file.Path

/**
 * Настройки YaXUnit
 */
data class YaxUnitProperties(
    val logging: YaxUnitLoggingProperties = YaxUnitLoggingProperties(),
)

/**
 * Настройки логирования YaXUnit
 */
data class YaxUnitLoggingProperties(
    /**
     * Режим логирования: AUTO (по умолчанию), FILE, CONSOLE, FILE_AND_CONSOLE.
     * AUTO автоматически выбирает режим на основе строки подключения.
     */
    val mode: YaxUnitLoggingMode = YaxUnitLoggingMode.AUTO,

    /**
     * Уровень логирования (info, debug, error).
     */
    val level: String = "info",

    /**
     * Размещать временные файлы (конфиг, отчёт, лог) в app.workPath/yaxunit/
     * вместо системной временной директории (%TEMP%).
     * Упрощает поиск и изоляцию между проектами.
     */
    val useWorkPath: Boolean = true,
)

/**
 * Разрешённая конфигурация логирования после применения эвристик [YaxUnitLoggingMode.AUTO].
 *
 * @param filePath путь к файлу лога (null при CONSOLE)
 * @param console признак вывода в консоль (stdout процесса 1С)
 * @param level уровень логирования
 * @param tempDirectory директория для временных файлов при useWorkPath=true
 */
data class ResolvedYaxUnitLogging(
    val filePath: String?,
    val console: Boolean,
    val level: String,
    val tempDirectory: Path?,
) {
    companion object {
        /**
         * Резолвит режим логирования на основе настроек и строки подключения.
         */
        fun resolve(
            logging: YaxUnitLoggingProperties,
            connectionString: String,
            tempDirectory: Path?,
        ): ResolvedYaxUnitLogging {
            val resolvedMode = resolveMode(logging.mode, connectionString)
            val filePath = if (resolvedMode.isFile()) logPath(tempDirectory) else null
            val console = resolvedMode.isConsole()
            return ResolvedYaxUnitLogging(
                filePath = filePath,
                console = console,
                level = logging.level,
                tempDirectory = tempDirectory,
            )
        }

        private fun resolveMode(
            mode: YaxUnitLoggingMode,
            connectionString: String,
        ): YaxUnitLoggingMode {
            if (mode != YaxUnitLoggingMode.AUTO) return mode
            val upper = connectionString.uppercase()
            return when {
                "SRVR=" in upper -> YaxUnitLoggingMode.CONSOLE
                "FILE=" in upper -> YaxUnitLoggingMode.FILE
                else -> YaxUnitLoggingMode.CONSOLE
            }
        }

        private fun logPath(tempDirectory: Path?): String? {
            if (tempDirectory == null) return null
            val dir = java.nio.file.Files.createDirectories(tempDirectory.resolve("yaxunit"))
            return dir.resolve("yaxunit-log-${java.util.UUID.randomUUID()}.log").toString()
        }

        private fun YaxUnitLoggingMode.isFile() = this == YaxUnitLoggingMode.FILE || this == YaxUnitLoggingMode.FILE_AND_CONSOLE
        private fun YaxUnitLoggingMode.isConsole() = this == YaxUnitLoggingMode.CONSOLE || this == YaxUnitLoggingMode.FILE_AND_CONSOLE
    }
}
