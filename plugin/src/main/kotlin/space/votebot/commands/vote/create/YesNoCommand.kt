package space.votebot.commands.vote.create

import dev.kord.core.behavior.interaction.response.createEphemeralFollowup
import dev.kord.core.entity.channel.Channel
import dev.kordex.core.commands.converters.impl.defaultingString
import dev.kordex.core.extensions.publicSlashCommand
import dev.schlaubi.mikbot.plugin.api.util.translate
import space.votebot.command.AbstractPollSettingsArguments
import space.votebot.common.models.PollSettings
import space.votebot.core.VoteBotModule
import space.votebot.translations.VoteBotTranslations

class YesNoArguments : AbstractPollSettingsArguments(), CreateSettings {
    override val settings: PollSettings = this
    override val answers: List<String> by lazy { listOf(yesWord, noWord) }
    override val title: String by voteTitle()
    override val channel: Channel? by voteChannel()
    private val yesWord by defaultingString {
        name = VoteBotTranslations.Commands.YesNo.Arguments.YesWord.name
        description = VoteBotTranslations.Commands.YesNo.Arguments.YesWord.description
        defaultValue = "Yes"
    }
    private val noWord by defaultingString {
        name = VoteBotTranslations.Commands.YesNo.Arguments.NoWord.name
        description = VoteBotTranslations.Commands.YesNo.Arguments.NoWord.description
        defaultValue = "No"
    }
}

suspend fun VoteBotModule.yesNowCommand() = publicSlashCommand(::YesNoArguments) {
    name = VoteBotTranslations.Commands.YesNo.name
    description = VoteBotTranslations.Commands.YesNo.description
    voteCommandContext()

    action {
        createVote(interactionResponse) ?: return@action
        interactionResponse.createEphemeralFollowup {
            content = translate(VoteBotTranslations.Commands.YesNo.success)
        }
    }
}
