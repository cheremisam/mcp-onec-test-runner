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

/**
 * Режим логирования YaXUnit.
 *
 * Определяет, куда и как YaXUnit пишет диагностические сообщения.
 */
enum class YaxUnitLoggingMode {
    /**
     * Автоматический выбор на основе строки подключения:
     * - Srvr= → CONSOLE (серверная ИБ — rphost и клиент не делят файл)
     * - File= → FILE (файловая ИБ — один процесс)
     * - иначе → CONSOLE (безопасный fallback)
     */
    AUTO,

    /**
     * Только файл. Лог пишется во временный файл yaxunit-log-*.log.
     * Подходит для файловых ИБ, CI с разбором лога.
     */
    FILE,

    /**
     * Только консоль. Лог пишется в stdout процесса 1С (попадает в enterprise /Out).
     * Серверная ИБ: rphost буферизует, клиент сливает — без блокировки файла.
     */
    CONSOLE,

    /**
     * Одновременно файл + консоль.
     * Полезно при отладке, чтобы видеть лог и в реальном времени,
     * и в файле для последующего анализа.
     */
    FILE_AND_CONSOLE,
}
