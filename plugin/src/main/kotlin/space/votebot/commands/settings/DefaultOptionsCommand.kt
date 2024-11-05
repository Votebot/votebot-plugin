package space.votebot.commands.settings

import dev.kordex.core.commands.Arguments
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.schlaubi.mikbot.plugin.api.settings.SettingsModule
import dev.schlaubi.mikbot.plugin.api.util.executableEverywhere
import dev.schlaubi.mikbot.plugin.api.util.translate
import space.votebot.command.PollSettingsArguments
import space.votebot.command.decide
import space.votebot.common.models.PollSettings
import space.votebot.common.models.StoredPollSettings
import space.votebot.core.VoteBotDatabase
import space.votebot.models.UserSettings
import space.votebot.translations.VoteBotTranslations

class DefaultOptionsArgument : Arguments(), PollSettingsArguments {
    override val maxVotes by maxVotes(VoteBotTranslations.Commands.DefaultOptions.MaxVotes.description)
    override val maxChanges by maxChanges(VoteBotTranslations.Commands.DefaultOptions.MaxChanges.description)
    override val deleteAfterPeriod by voteDuration(VoteBotTranslations.Commands.DefaultOptions.DeleteAfterPeriod.description)
    override val showChartAfterClose: Boolean? by showChart(VoteBotTranslations.Commands.DefaultOptions.ShowChartAfterClose.description)
    override val hideResults: Boolean? by hideResults(VoteBotTranslations.Commands.DefaultOptions.HideResults.description)
    override val publicResults: Boolean? by publicResults(VoteBotTranslations.Commands.DefaultOptions.PublicResults.description)
    private val emojiModeOption by emojiMode(VoteBotTranslations.Commands.DefaultOptions.EmojiMode.description)
    override val emojiMode: PollSettings.EmojiMode?
        get() = emojiModeOption?.mode
}

suspend fun SettingsModule.defaultOptionsCommand() = ephemeralSlashCommand(::DefaultOptionsArgument) {
    name = VoteBotTranslations.Commands.DefaultOptions.name
    description = VoteBotTranslations.Commands.DefaultOptions.description
    executableEverywhere()

    action {
        val currentSettings = VoteBotDatabase.userSettings.findOneById(user.id)?.settings

        val newSettings = StoredPollSettings(
            decide(currentSettings?.deleteAfter, arguments.deleteAfter),
            decide(currentSettings?.showChartAfterClose, arguments.showChartAfterClose),
            decide(currentSettings?.maxVotes, arguments.maxVotes),
            decide(currentSettings?.maxChanges, arguments.maxChanges),
            decide(currentSettings?.hideResults, arguments.hideResults),
            decide(currentSettings?.publicResults, arguments.publicResults),
        )

        VoteBotDatabase.userSettings.save(UserSettings(user.id, newSettings))

        respond {
            content = translate(VoteBotTranslations.Commands.DefaultOptions.saved)
        }
    }
}
