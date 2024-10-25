package space.votebot.commands.vote.create

import com.kotlindiscord.kord.extensions.commands.converters.impl.defaultingString
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import dev.kord.core.behavior.interaction.response.createEphemeralFollowup
import dev.kord.core.entity.channel.Channel
import space.votebot.command.AbstractPollSettingsArguments
import space.votebot.common.models.PollSettings
import space.votebot.core.VoteBotModule

class YesNoArguments : AbstractPollSettingsArguments(), CreateSettings {
    override val settings: PollSettings = this
    override val answers: List<String> by lazy { listOf(yesWord, noWord) }
    override val title: String by voteTitle()
    override val channel: Channel? by voteChannel()
    private val yesWord by defaultingString {
        name = "yes-word"
        description = "commands.yes_no.arguments.yes_word.description"
        defaultValue = "Yes"
    }
    private val noWord by defaultingString {
        name = "no-word"
        description = "commands.yes_no.arguments.no_word.description"
        defaultValue = "No"
    }
}

suspend fun VoteBotModule.yesNowCommand() = publicSlashCommand(::YesNoArguments) {
    name = "yes-no"
    description = "commands.yes_no.description"
    voteCommandContext()

    action {
        createVote(interactionResponse) ?: return@action
        interactionResponse.createEphemeralFollowup {
            content = translate("commands.yes_no.success")
        }
    }
}
