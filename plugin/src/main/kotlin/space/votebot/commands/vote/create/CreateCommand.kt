package space.votebot.commands.vote.create

import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.components.applyComponents
import com.kotlindiscord.kord.extensions.components.buttons.InteractionButtonWithAction
import com.kotlindiscord.kord.extensions.components.components
import com.kotlindiscord.kord.extensions.components.ephemeralButton
import com.kotlindiscord.kord.extensions.components.ephemeralStringSelectMenu
import com.kotlindiscord.kord.extensions.components.forms.ModalForm
import com.kotlindiscord.kord.extensions.components.types.emoji
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.parsers.DurationParser
import com.kotlindiscord.kord.extensions.parsers.DurationParserException
import dev.kord.common.asJavaLocale
import dev.kord.common.entity.ApplicationIntegrationType
import dev.kord.common.entity.ButtonStyle
import dev.kord.core.entity.channel.Channel
import dev.kord.x.emoji.DiscordEmoji
import dev.kord.x.emoji.Emojis
import dev.schlaubi.mikbot.plugin.api.util.discordError
import dev.schlaubi.mikbot.plugin.api.util.kord
import dev.schlaubi.mikbot.plugin.api.util.toDuration
import kotlinx.datetime.Clock
import space.votebot.command.ChoiceEmojiMode
import space.votebot.command.PollSettingsArgumentsMixin
import space.votebot.command.toChoiceEmoji
import space.votebot.common.models.FinalPollSettings
import space.votebot.common.models.Poll
import space.votebot.common.models.PollSettings
import space.votebot.common.models.StoredPollSettings
import space.votebot.common.models.merge
import space.votebot.core.VoteBotDatabase
import space.votebot.core.VoteBotModule
import space.votebot.core.selectEmojis
import space.votebot.core.toEmbed
import space.votebot.util.voteSafeGuild
import java.util.Locale
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

class CreateArguments : Arguments(), BasicCreateOptions, PollSettingsArgumentsMixin {
    override val title: String by voteTitle()
    override val channel: Channel? by voteChannel()
    val maxVotes by maxVotes("TBD")
    val maxCHanges by maxChanges("TBD")
}

private class AddOptionModal : ModalForm() {
    override var bundle: String? = "votebot"
    override var title: String = "commands.create.interactive.modal.add_option.title"

    val option = lineText {
        label = "commands.create.interactive.modal.add_option.option.label"
        maxLength = 50
    }
}

private class SetDurationModal : ModalForm() {
    override var bundle: String? = "votebot"
    override var title: String = "commands.create.interactive.modal.set_duration.title"

    val duration = lineText {
        label = "commands.create.interactive.modal.set_duration.option.label"
    }
}

suspend fun VoteBotModule.createCommand() = ephemeralSlashCommand(::CreateArguments) {
    name = "create-vote"
    description = "commands.create.interactive.description"
    voteCommandContext()

    action commandAction@{
        var currentSettings = (VoteBotDatabase.userSettings.findOneById(user.id)?.settings
            ?: StoredPollSettings()).merge(
            StoredPollSettings(
                maxVotes = arguments.maxVotes,
                maxChanges = arguments.maxCHanges
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
         val isGuildInstall = event.interaction.authorizingIntegrationOwners.containsKey(ApplicationIntegrationType.GuildInstall)
        val maxOptions = if(isGuildInstall) 25 else 20

        respond {
            content = translate("commands.create.interactive.status", arrayOf(channel.mention))
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
                    label = translate("commands.create.interactive.add_option.label")
                    bundle = this@createCommand.bundle
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
                    label = translate("commands.create.interactive.remove_option.label")
                    disabled = true
                    style = ButtonStyle.Danger
                    emoji(Emojis.heavyMinusSign.toString())
                    bundle = this@createCommand.bundle

                    action {
                        respond {
                            content = translate("commands.create.interactive.remove_option.select")

                            components(timeout) {
                                ephemeralStringSelectMenu {
                                    bundle = this@createCommand.bundle
                                    maximumChoices = poll.options.size
                                    poll.options.forEachIndexed { index, option ->
                                        val (_, label) = option as Poll.Option.ActualOption
                                        option("${index + 1} - $label", index.toString())
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
                                            content = translate("commands.create.interactive.remove_option.done")
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
                    label = translate("commands.create.interactive.submit")
                    bundle = this@createCommand.bundle
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
                                content = e.reason

                                components(5.minutes) {
                                    ephemeralButton {
                                        label = translate("common.retry")
                                        bundle = this@createCommand.bundle
                                        emoji(Emojis.repeat.toString())

                                        action {
                                            edit { components = mutableListOf() }
                                            val vote = this@commandAction.createVote(interactionResponse, settings)
                                                ?: return@action

                                            this@commandAction.edit {
                                                content = translate("commands.create.done", arrayOf(vote.jumpUrl))
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
                            content = translate("commands.create.done", arrayOf(vote.jumpUrl))
                            components = mutableListOf()
                            embeds = mutableListOf()
                        }
                    }
                }

                ephemeralButton(row = 2) {
                    style = ButtonStyle.Secondary
                    emoji(Emojis.gear.toString())
                    label = translate("commands.create.interactive.settings.label")
                    bundle = this@createCommand.bundle

                    action {
                        respond {
                            content = translate("commands.create.interactive.settings.explainer")
                            components(timeout) {
                                suspend fun settingsToggle(
                                    row: Int,
                                    translateKey: String,
                                    emoji: DiscordEmoji,
                                    option: FinalPollSettings.() -> Boolean,
                                    on: String = "commands.create.interactive.settings.on",
                                    off: String = "commands.create.interactive.settings.off",
                                    update: FinalPollSettings.(Boolean) -> FinalPollSettings
                                ) = ephemeralButton(row) {
                                    suspend fun rerender() {
                                        val state = if (currentSettings.option()) on else off

                                        label = translate(translateKey, arrayOf(translate(state)))
                                        style = if (!poll.settings.option()) ButtonStyle.Danger else ButtonStyle.Success
                                    }
                                    bundle = this@createCommand.bundle
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
                                    "commands.create.interactive.hide_results.label",
                                    Emojis.detective,
                                    FinalPollSettings::hideResults
                                ) {
                                    copy(hideResults = it)
                                }
                                settingsToggle(
                                    1,
                                    "commands.create.interactive.public_results.label",
                                    Emojis.chartWithUpwardsTrend,
                                    FinalPollSettings::publicResults
                                ) {
                                    copy(publicResults = it)
                                }
                                settingsToggle(
                                    1,
                                    "commands.create.interactive.show_chart.label",
                                    Emojis.barChart,
                                    FinalPollSettings::showChartAfterClose,
                                    on = "commands.create.interactive.show_chart.on",
                                    off = "commands.create.interactive.show_chart.off",
                                ) {
                                    copy(showChartAfterClose = it)
                                }

                                if(isGuildInstall) {
                                    ephemeralButton(::SetDurationModal, row = 2) {
                                        bundle = this@createCommand.bundle
                                        emoji(Emojis.clock.toString())
                                        label = translate("commands.create.interactive.set_duration.label")

                                        action { modal ->
                                            try {
                                                val duration =
                                                    DurationParser.parse(
                                                        modal!!.duration.value!!,
                                                        event.interaction.locale?.asJavaLocale() ?: Locale.ENGLISH
                                                    ).toDuration()
                                                checkDuration(duration)

                                                currentSettings = currentSettings.copy(deleteAfter = duration)
                                                update()
                                            } catch (e: DurationParserException) {
                                                discordError(e.message!!)
                                            }
                                        }
                                    }
                                }
                                ephemeralStringSelectMenu(row = 3) {
                                    bundle = this@createCommand.bundle
                                    suspend fun render() {
                                        val name = translate(currentSettings.emojiMode.toChoiceEmoji().readableName)

                                        placeholder = translate(
                                            "commands.create.interactive.emoji_mode.place_holder",
                                            arrayOf(name)
                                        )
                                    }
                                    render()

                                    ChoiceEmojiMode.entries
                                        .filter { isGuildInstall || it != ChoiceEmojiMode.CUSTOM }
                                        .forEach {
                                        val label = translate(
                                            "commands.create.interactive.emoji_mode.place_holder",
                                            arrayOf(translate(it.readableName))
                                        )

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
