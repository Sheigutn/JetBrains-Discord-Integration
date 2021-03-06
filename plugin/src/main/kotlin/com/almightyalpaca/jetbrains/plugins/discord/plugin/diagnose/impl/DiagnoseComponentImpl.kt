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

package com.almightyalpaca.jetbrains.plugins.discord.plugin.diagnose.impl

import com.almightyalpaca.jetbrains.plugins.discord.plugin.diagnose.DiagnoseComponent
import com.almightyalpaca.jetbrains.plugins.discord.plugin.utils.tryCatch
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.extensions.PluginId
import kotlinx.coroutines.*
import org.apache.commons.lang3.SystemUtils
import java.nio.charset.StandardCharsets
import kotlin.coroutines.CoroutineContext

class DiagnoseComponentImpl : DiagnoseComponent, CoroutineScope {
    private val parentJob: Job = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + parentJob

    override val discord = async(start = CoroutineStart.DEFAULT) { tryCatch(DiagnoseComponent.Discord.OTHER) { readDiscord() } }
    override val plugins = async(start = CoroutineStart.DEFAULT) { tryCatch(DiagnoseComponent.Plugins.NONE) { readPlugins() } }
    override val ide = async(start = CoroutineStart.DEFAULT) { tryCatch(DiagnoseComponent.IDE.OTHER) { readIDE() } }

    private fun readDiscord(): DiagnoseComponent.Discord = when {
        SystemUtils.IS_OS_WINDOWS -> readDiscordWindows()
        SystemUtils.IS_OS_LINUX -> readDiscordLinux()
        SystemUtils.IS_OS_MAC -> readDiscordMac()
        else -> DiagnoseComponent.Discord.OTHER
    }

    private fun readDiscordMac(): DiagnoseComponent.Discord {
        val clients = arrayOf(
            "Discord.app",
            "Discord PTB.app",
            "Discord Canary.app",
            "Discord Development.app"
        )

        val process = Runtime.getRuntime().exec("ps -A")
        process.waitFor()

        val clientNotRunning = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
            reader.lineSequence()
                .none { line -> clients.any { client -> line.contains(client) } }
        }

        if (clientNotRunning) {
            return DiagnoseComponent.Discord.CLOSED
        }

        // TODO: Mac Discord browser detection
        return DiagnoseComponent.Discord.OTHER
    }

    private fun readDiscordLinux(): DiagnoseComponent.Discord {
        val process = Runtime.getRuntime().exec("ps ax")
        process.waitFor()
        val lines = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
            reader.lineSequence()
                .filter { line -> line.contains("discord", true) }
                .joinToString("\n")
        }

        if (lines.isBlank()) {
            return DiagnoseComponent.Discord.CLOSED
        } else if (lines.contains("/snap/discord/", true)) {
            return DiagnoseComponent.Discord.SNAP
        }

        // TODO: Linux Discord browser detection
        return DiagnoseComponent.Discord.OTHER
    }

    private fun readDiscordWindows(): DiagnoseComponent.Discord {
        val browsers = arrayOf(
            "chrome.exe",
            "firefox.exe",
            "ApplicationFrameHost.exe", // Microsoft Edge
            "opera.exe",
            "iexplore.exe"
        )

        val discord = arrayOf(
            "Discord.exe",
            "DiscordPTB.exe",
            "DiscordCanary.exe",
            "DiscordDevelopment.exe"
        )

        val process = Runtime.getRuntime().exec("""tasklist /V /fi "SESSIONNAME eq Console"""")
        val lines = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
            reader.lineSequence()
                .filter { line -> line.contains("Discord", true) }
                .toList()
        }

        if (lines.isEmpty()) {
            return DiagnoseComponent.Discord.CLOSED
        } else {
            val discordClientNotRunning = lines.none { line -> discord.any { exe -> line.startsWith(exe, true) } }
            if (discordClientNotRunning) {
                val discordBrowser = lines.any { line -> line.contains("discord", true) && browsers.any { browser -> line.startsWith(browser, true) } }
                if (discordBrowser) {
                    return DiagnoseComponent.Discord.BROWSER
                }
            }
        }

        return DiagnoseComponent.Discord.OTHER
    }

    private val pluginsIds = arrayOf(
        "com.tsunderebug.discordintellij",
        "com.my.fobes.intellij.discord"
    )

    private fun readPlugins(): DiagnoseComponent.Plugins {
        val matches = PluginManager.getPlugins()
            .asSequence()
            .map(IdeaPluginDescriptor::getPluginId)
            .map(PluginId::getIdString)
            .count(pluginsIds::contains)

        return when (matches) {
            0 -> DiagnoseComponent.Plugins.NONE
            1 -> DiagnoseComponent.Plugins.ONE
            else -> DiagnoseComponent.Plugins.MULTIPLE
        }
    }

    private fun readIDE(): DiagnoseComponent.IDE = when {
        SystemUtils.IS_OS_WINDOWS -> readIDEWindows()
        SystemUtils.IS_OS_LINUX -> readIDELinux()
        SystemUtils.IS_OS_MAC -> readIDEMac()
        else -> DiagnoseComponent.IDE.OTHER
    }

    private fun readIDEWindows(): DiagnoseComponent.IDE = DiagnoseComponent.IDE.OTHER

    private fun readIDELinux(): DiagnoseComponent.IDE {
        if (System.getenv("SNAP") != null) {
            return DiagnoseComponent.IDE.SNAP
        }

        return DiagnoseComponent.IDE.OTHER
    }

    private fun readIDEMac(): DiagnoseComponent.IDE = DiagnoseComponent.IDE.OTHER

    // override fun reportDiscordConnectionChange() = TODO("not implemented")
    // override fun reportInternetConnectionChange() = TODO("not implemented")

    override fun disposeComponent() {
        parentJob.cancel()
    }
}
