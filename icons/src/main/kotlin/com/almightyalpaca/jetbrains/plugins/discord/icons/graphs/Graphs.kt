/*
 * Copyright 2017-2019 Aljoscha Grebe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.almightyalpaca.jetbrains.plugins.discord.icons.graphs

import com.almightyalpaca.jetbrains.plugins.discord.icons.utils.getLocalIcons
import com.almightyalpaca.jetbrains.plugins.discord.shared.source.local.LocalSource
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Paths

@Suppress("BlockingMethodInNonBlockingContext")
fun main() = runBlocking {
    val source = LocalSource(Paths.get("../"), retry = false)

    val languages = source.getLanguages()
    val themes = source.getThemes()

    val graphs = Paths.get("build/graphs/")
    Files.createDirectories(graphs)

    for (theme in themes.keys) {
        val icons = getLocalIcons(theme)

        val exporter = DotGraphExporter(languages, icons)

        val path = graphs.resolve("$theme.dot")

        Files.newBufferedWriter(path).use { writer ->
            exporter.writeTo(writer)
        }
    }
}
