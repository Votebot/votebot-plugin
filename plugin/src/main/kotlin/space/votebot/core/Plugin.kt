package space.votebot.core

import dev.kordex.core.builders.ExtensibleBotBuilder
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kord.common.Locale
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.gateway.Intent
import dev.kordex.core.builders.ExtensionsBuilder
import dev.schlaubi.mikbot.plugin.api.Plugin
import dev.schlaubi.mikbot.plugin.api.PluginContext
import dev.schlaubi.mikbot.plugin.api.PluginMain
import dev.schlaubi.mikbot.plugin.api.config.Config
import dev.schlaubi.mikbot.plugin.api.config.Environment
import kotlinx.coroutines.cancel
import kotlinx.serialization.builtins.serializer
import org.litote.kmongo.serialization.registerSerializer
import space.votebot.commands.commands

@PluginMain
class VoteBotPlugin(wrapper: PluginContext) : Plugin(wrapper) {
    override fun start() {
        registerSerializer(ULong.serializer())
    }

    override fun ExtensionsBuilder.addExtensions() {
        add(::VoteBotModule)
    }

    override suspend fun ExtensibleBotBuilder.apply() {
        extensions {
            sentry {
                dsn = Config.SENTRY_TOKEN
                enable = Config.ENVIRONMENT == Environment.PRODUCTION
            }
        }
        kord {
            // Disable non essential intents to keep down processing and traffic
            intents(false, false) {
                // Required for permissions
                +Intent.Guilds
            }
        }
        i18n {
            applicationCommandLocales.add(Locale.FRENCH)
        }
    }

    override fun stop() {
        ExpirationScope.cancel()
    }
}

class VoteBotModule : Extension() {
    override val name: String = "votebot"
    override val allowApplicationCommandInDMs: Boolean = false

    override suspend fun setup() {
        commands()
        voteExecutor()

        event<ReadyEvent> {
            action {
                rescheduleAllPollExpires(kord)
            }
        }
    }
}
