package space.votebot.commands.vote.create

import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kord.core.entity.channel.Channel
import dev.schlaubi.mikbot.plugin.api.util.translate
import space.votebot.command.AbstractPollSettingsArguments
import space.votebot.common.models.PollSettings
import space.votebot.core.VoteBotModule
import space.votebot.translations.VoteBotTranslations

private val optionsRegex = "\\s*\\|\\s*".toRegex()

// For the weird order and reverse(): https://github.com/Kord-Extensions/kord-extensions/issues/123
class CreateOptions : AbstractPollSettingsArguments(), CreateSettings {
    override val channel: Channel? by voteChannel()

    private val answersOptions by string {
        name = VoteBotTranslations.Commands.Create.Arguments.Answers.name
        description = VoteBotTranslations.Commands.Create.Arguments.Answers.description
    }
    override val title: String by voteTitle()

    override val answers: List<String> by lazy { answersOptions.split(optionsRegex) }

    override val settings: PollSettings get() = this
}

suspend fun VoteBotModule.quickCommand() = ephemeralSlashCommand(::CreateOptions) {
    name = VoteBotTranslations.Commands.QuickCreate.name
    description = VoteBotTranslations.Commands.QuickCreate.description
    voteCommandContext()

    action {
        createVote(interactionResponse) ?: return@action
        respond {
            content = translate(VoteBotTranslations.Commands.Create.success, arguments.title)
        }
    }
}
