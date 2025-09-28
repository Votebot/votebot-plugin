package space.votebot.commands.vote.create

import dev.kord.common.asJavaLocale
import dev.kord.common.entity.ApplicationIntegrationType
import dev.kord.common.entity.ButtonStyle
import dev.kord.core.entity.channel.Channel
import dev.kord.x.emoji.DiscordEmoji
import dev.kord.x.emoji.Emojis
import dev.kordex.core.DiscordRelayedException
import dev.kordex.core.commands.Arguments
import dev.kordex.core.components.applyComponents
import dev.kordex.core.components.buttons.InteractionButtonWithAction
import dev.kordex.core.components.components
import dev.kordex.core.components.ephemeralButton
import dev.kordex.core.components.ephemeralStringSelectMenu
import dev.kordex.core.components.forms.ModalForm
import dev.kordex.core.components.types.emoji
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.core.i18n.toKey
import dev.kordex.core.i18n.types.Key
import dev.kordex.core.i18n.withContext
import dev.kordex.core.parsers.DurationParser
import dev.kordex.core.parsers.DurationParserException
import dev.kordex.core.utils.toDuration
import dev.schlaubi.mikbot.plugin.api.util.discordError
import dev.schlaubi.mikbot.plugin.api.util.kord
import dev.schlaubi.mikbot.plugin.api.util.translate
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import space.votebot.command.ChoiceEmojiMode
import space.votebot.command.PollSettingsArgumentsMixin
import space.votebot.command.toChoiceEmoji
import space.votebot.common.models.*
import space.votebot.core.VoteBotDatabase
import space.votebot.core.VoteBotModule
import space.votebot.core.selectEmojis
import space.votebot.core.toEmbed
import space.votebot.translations.VoteBotTranslations
import space.votebot.util.voteSafeGuild
import java.util.*
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

class CreateArguments : Arguments(), BasicCreateOptions, PollSettingsArgumentsMixin {
    override val title: String by voteTitle()
    override val channel: Channel? by voteChannel()
    val maxVotes by maxVotes(VoteBotTranslations.Poll.Create.Arguments.maxVotes)
    val maxChanges by maxChanges(VoteBotTranslations.Poll.Create.Arguments.maxChanges)
}

private class AddOptionModal : ModalForm() {
    override var title = VoteBotTranslations.Commands.Create.Interactive.Modal.AddOption.title

    val option = lineText {
        label = VoteBotTranslations.Commands.Create.Interactive.Modal.AddOption.Option.label
        maxLength = 50
    }
}

private class SetDurationModal : ModalForm() {
    override var title = VoteBotTranslations.Commands.Create.Interactive.Modal.SetDuration.title

    val duration = lineText {
        label = VoteBotTranslations.Commands.Create.Interactive.Modal.SetDuration.Option.label
    }
}

suspend fun VoteBotModule.createCommand() = ephemeralSlashCommand(::CreateArguments) {
    name = VoteBotTranslations.Commands.Create.name
    description = VoteBotTranslations.Commands.Create.description
    voteCommandContext()

    action commandAction@{
        if ((arguments.maxVotes != null && arguments.maxVotes != 1) && (arguments.maxChanges != null && arguments.maxChanges != 0)) {
            throw DiscordRelayedException(VoteBotTranslations.Vote.Create.invalidChangeConfiguration)
        }
        var currentSettings = (VoteBotDatabase.userSettings.findOneById(user.id)?.settings
            ?: StoredPollSettings()).merge(
            StoredPollSettings(
                maxVotes = arguments.maxVotes,
                maxChanges = arguments.maxChanges
            )
        )
        val emojis = currentSettings.selectEmojis(
            voteSafeGuild,
            25
        )
        var poll = Poll(
            "_",
            0u,
            user.id.value,
            arguments.title,
            emptyList(),
            emptyMap(),
            emptyList(),
            emptyList(),
            Clock.System.now(),
            currentSettings
        )
        val isGuildInstall =
            event.interaction.authorizingIntegrationOwners.containsKey(ApplicationIntegrationType.GuildInstall)
        val maxOptions = if (isGuildInstall) 25 else 20

        respond {
            content = translate(VoteBotTranslations.Commands.Create.Interactive.status, channel.mention)
            embeds = mutableListOf(poll.toEmbed(this@commandAction.kord, this@commandAction.voteSafeGuild))

            components(10.minutes) {
                suspend fun update() = edit {
                    val emojis = currentSettings.selectEmojis(
                        voteSafeGuild,
                        poll.options.size
                    )
                    // re-arrange emojis and update settings
                    poll = poll.copy(
                        options = poll.options.mapIndexed { index, option ->
                            (option as Poll.Option.ActualOption).copy(emoji = emojis.getOrNull(index))
                        },
                        votes = poll.options.flatMapIndexed { index, _ ->
                            (0..Random.nextInt(5)).map {
                                Poll.Vote(index, it.toULong())
                            }
                        },
                        settings = currentSettings
                    )
                    embeds = mutableListOf(poll.toEmbed(this@commandAction.kord, this@commandAction.voteSafeGuild))
                    applyComponents(this@components)
                }

                lateinit var addOptionButton: InteractionButtonWithAction<*, *>
                lateinit var removeOptionButton: InteractionButtonWithAction<*, *>
                lateinit var submitButton: InteractionButtonWithAction<*, *>

                addOptionButton = ephemeralButton(::AddOptionModal, row = 1) {
                    label = VoteBotTranslations.Commands.Create.Interactive.AddOption.label
                    style = ButtonStyle.Success
                    emoji(Emojis.heavyPlusSign.toString())

                    action { options ->
                        poll = poll.copy(
                            options = poll.options + Poll.Option.ActualOption(
                                poll.options.size,
                                options!!.option.value!!,
                                emojis[poll.options.size]
                            )
                        )
                        if (poll.options.size == maxOptions) {
                            disable()
                        }
                        removeOptionButton.enable()
                        if (poll.options.size >= 2) {
                            submitButton.enable()
                        }
                        update()
                    }
                }

                removeOptionButton = ephemeralButton(row = 1) {
                    label = VoteBotTranslations.Commands.Create.Interactive.RemoveOption.label
                    disabled = true
                    style = ButtonStyle.Danger
                    emoji(Emojis.heavyMinusSign.toString())

                    action {
                        respond {
                            content = translate(VoteBotTranslations.Commands.Create.Interactive.RemoveOption.select)

                            components(timeout) {
                                ephemeralStringSelectMenu {
                                    maximumChoices = poll.options.size
                                    poll.options.forEachIndexed { index, option ->
                                        val (_, label) = option as Poll.Option.ActualOption
                                        option("${index + 1} - $label".toKey(), index.toString())
                                    }

                                    action {
                                        poll = poll.copy(
                                            options = poll.options.filterIndexed { index, _ ->
                                                index.toString() !in selected
                                            }
                                        )
                                        if (poll.options.isEmpty()) {
                                            disable()
                                        }
                                        if (poll.options.size < 2) {
                                            submitButton.disable()
                                        }
                                        addOptionButton.enable()
                                        update()
                                        edit {
                                            content =
                                                translate(VoteBotTranslations.Commands.Create.Interactive.RemoveOption.done)
                                            components = mutableListOf()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                submitButton = ephemeralButton(row = 2) {
                    style = ButtonStyle.Success
                    emoji(Emojis.heavyCheckMark.toString())
                    label = VoteBotTranslations.Commands.Create.Interactive.submit
                    disabled = true

                    action {
                        val settings = object : CreateSettings {
                            override val answers: List<String> =
                                poll.options.map { (it as Poll.Option.ActualOption).option }
                            override val settings: PollSettings = poll.settings
                            override val title: String = poll.title
                            override val channel: Channel? = arguments.channel
                        }

                        val vote = try {
                            this@commandAction.createVote(interactionResponse, settings) ?: return@action
                        } catch (e: DiscordRelayedException) {
                            respond {
                                content = e.reason.withContext(this@action).translate()

                                components(5.minutes) {
                                    ephemeralButton {
                                        label = VoteBotTranslations.Common.retry
                                        emoji(Emojis.repeat.toString())

                                        action {
                                            edit { components = mutableListOf() }
                                            val vote = this@commandAction.createVote(interactionResponse, settings)
                                                ?: return@action

                                            this@commandAction.edit {
                                                content =
                                                    translate(VoteBotTranslations.Commands.Create.done, vote.jumpUrl)
                                                components = mutableListOf()
                                                embeds = mutableListOf()
                                            }
                                        }
                                    }
                                }
                            }
                            return@action
                        }

                        this@commandAction.edit {
                            content = translate(VoteBotTranslations.Commands.Create.done, vote.jumpUrl)
                            components = mutableListOf()
                            embeds = mutableListOf()
                        }
                    }
                }

                ephemeralButton(row = 2) {
                    style = ButtonStyle.Secondary
                    emoji(Emojis.gear.toString())
                    label = VoteBotTranslations.Commands.Create.Interactive.Settings.label

                    action {
                        respond {
                            content = translate(VoteBotTranslations.Commands.Create.Interactive.Settings.explainer)
                            components(timeout) {
                                suspend fun settingsToggle(
                                    row: Int,
                                    translateKey: Key,
                                    emoji: DiscordEmoji,
                                    option: FinalPollSettings.() -> Boolean,
                                    on: Key = VoteBotTranslations.Commands.Create.Interactive.Settings.on,
                                    off: Key = VoteBotTranslations.Commands.Create.Interactive.Settings.off,
                                    update: FinalPollSettings.(Boolean) -> FinalPollSettings
                                ) = ephemeralButton(row) {
                                    suspend fun rerender() {
                                        val state = if (currentSettings.option()) on else off

                                        label = translateKey.withOrdinalPlaceholders(translate(state))
                                        style = if (!poll.settings.option()) ButtonStyle.Danger else ButtonStyle.Success
                                    }
                                    emoji(emoji.toString())
                                    rerender()
                                    action {
                                        currentSettings = currentSettings.update(!poll.settings.option())
                                        rerender()
                                        update()
                                    }
                                }

                                settingsToggle(
                                    1,
                                    VoteBotTranslations.Commands.Create.Interactive.HideResults.label,
                                    Emojis.detective,
                                    FinalPollSettings::hideResults
                                ) {
                                    copy(hideResults = it)
                                }
                                settingsToggle(
                                    1,
                                    VoteBotTranslations.Commands.Create.Interactive.PublicResults.label,
                                    Emojis.chartWithUpwardsTrend,
                                    FinalPollSettings::publicResults
                                ) {
                                    copy(publicResults = it)
                                }
                                settingsToggle(
                                    1,
                                    VoteBotTranslations.Commands.Create.Interactive.ShowChart.label,
                                    Emojis.barChart,
                                    FinalPollSettings::showChartAfterClose,
                                    on = VoteBotTranslations.Commands.Create.Interactive.ShowChart.on,
                                    off = VoteBotTranslations.Commands.Create.Interactive.ShowChart.off,
                                ) {
                                    copy(showChartAfterClose = it)
                                }

                                if (isGuildInstall) {
                                    ephemeralButton(::SetDurationModal, row = 2) {
                                        emoji(Emojis.clock.toString())
                                        label = VoteBotTranslations.Commands.Create.Interactive.SetDuration.label

                                        action { modal ->
                                            try {
                                                val duration =
                                                    DurationParser.parse(
                                                        modal!!.duration.value!!,
                                                        event.interaction.locale?.asJavaLocale() ?: Locale.ENGLISH
                                                    ).toDuration(TimeZone.UTC)
                                                checkDuration(duration)

                                                currentSettings = currentSettings.copy(deleteAfter = duration)
                                                update()
                                            } catch (e: DurationParserException) {
                                                discordError(e.message!!.toKey())
                                            }
                                        }
                                    }
                                }
                                ephemeralStringSelectMenu(row = 3) {
                                    suspend fun render() {
                                        val name = translate(currentSettings.emojiMode.toChoiceEmoji().readableName)

                                        placeholder =
                                            VoteBotTranslations.Commands.Create.Interactive.EmojiMode.placeHolder
                                                .withOrdinalPlaceholders(name)
                                    }
                                    render()

                                    ChoiceEmojiMode.entries
                                        .filter { isGuildInstall || it != ChoiceEmojiMode.CUSTOM }
                                        .forEach {
                                            val label =
                                                VoteBotTranslations.Commands.Create.Interactive.EmojiMode.placeHolder
                                                    .withOrdinalPlaceholders(translate(it.readableName))

                                            option(label, it.mode.name)
                                        }

                                    action {
                                        currentSettings = currentSettings.copy(
                                            emojiMode = enumValueOf<PollSettings.EmojiMode>(selected.first())
                                        )
                                        render()
                                        update()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
