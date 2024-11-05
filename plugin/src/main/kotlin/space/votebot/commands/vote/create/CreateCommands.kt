package space.votebot.commands.vote.create

import dev.kordex.core.commands.application.ApplicationCommand
import dev.kord.common.entity.ApplicationIntegrationType
import dev.kord.common.entity.InteractionContextType
import space.votebot.core.VoteBotModule

fun ApplicationCommand<*>.voteCommandContext() {
    allowedContexts.addAll(listOf(InteractionContextType.Guild, InteractionContextType.PrivateChannel))
    allowedInstallTypes.addAll(ApplicationIntegrationType.entries)
}

fun ApplicationCommand<*>.guildOnlyCommand() {
    allowedContexts.add(InteractionContextType.Guild)
    allowedInstallTypes.add(ApplicationIntegrationType.GuildInstall)
}

suspend fun VoteBotModule.createCommands() {
    createCommand()
    yesNowCommand()
    quickCommand()
}
