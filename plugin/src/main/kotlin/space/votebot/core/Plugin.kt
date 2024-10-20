package space.votebot.core

import com.kotlindiscord.kord.extensions.builders.ExtensibleBotBuilder
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import dev.kord.common.Locale
import dev.kord.core.event.gateway.ReadyEvent
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

    override fun ExtensibleBotBuilder.ExtensionsBuilder.addExtensions() {
        add(::VoteBotModule)
    }

    override suspend fun ExtensibleBotBuilder.apply() {
        extensions {
            sentry {
                dsn = Config.SENTRY_TOKEN
                enable = Config.ENVIRONMENT == Environment.PRODUCTION
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
    override val bundle: String = "votebot"
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
