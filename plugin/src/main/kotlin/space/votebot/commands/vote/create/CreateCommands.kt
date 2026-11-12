package space.votebot.commands.vote.create

import dev.kordex.core.commands.application.ApplicationCommand
import dev.kord.common.entity.ApplicationIntegrationType
import dev.kord.common.entity.InteractionContextType
import io.ktor.server.config.configLoaders
import space.votebot.core.VoteBotModule

fun ApplicationCommand<*>.voteCommandContext() {
    scope {
        guildInstallable()
        userInstallable {
            allowInDirectDMs()
            allowInGroupDMs()
            allowInGuilds()
        }
    }
}

fun ApplicationCommand<*>.guildOnlyCommand() {
    scope {
        guildInstallable()
        contexts.allowInGuilds()
    }
}

suspend fun VoteBotModule.createCommands() {
    createCommand()
    yesNowCommand()
    quickCommand()
}
