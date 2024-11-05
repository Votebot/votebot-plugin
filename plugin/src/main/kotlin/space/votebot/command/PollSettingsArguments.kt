package space.votebot.command

import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.ChoiceEnum
import dev.kordex.core.commands.application.slash.converters.impl.optionalEnumChoice
import dev.kordex.core.commands.converters.impl.optionalBoolean
import dev.kordex.core.commands.converters.impl.optionalDuration
import dev.kordex.core.commands.converters.impl.optionalInt
import dev.kordex.core.i18n.EMPTY_KEY
import dev.kordex.core.i18n.types.Key
import dev.kordex.core.utils.toDuration
import dev.schlaubi.mikbot.plugin.api.util.IKnowWhatIAmDoing
import dev.schlaubi.mikbot.plugin.api.util.SortedArguments
import dev.schlaubi.mikbot.plugin.api.util.discordError
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.TimeZone
import space.votebot.common.models.PollSettings
import space.votebot.translations.VoteBotTranslations
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

interface PollSettingsArguments : PollSettings, PollSettingsArgumentsMixin {
    val deleteAfterPeriod: DateTimePeriod?
    override val deleteAfter: Duration?
        get() = deleteAfterPeriod?.toDuration(TimeZone.UTC)
}

interface PollSettingsArgumentsMixin {
    fun Arguments.voteDuration(description: Key) = optionalDuration {
        name = VoteBotTranslations.Common.Duration.name
        this.description = description

        validate {
            if (value != null && value!!.toDuration(TimeZone.UTC) < 1.minutes) {
                discordError(VoteBotTranslations.Vote.Create.tooShort)
            }
        }
    }

    fun Arguments.maxVotes(description: Key) = optionalInt {
        name = VoteBotTranslations.Common.MaxVotes.name
        this.description = description
    }

    fun Arguments.maxChanges(description: Key) = optionalInt {
        name = VoteBotTranslations.Common.MaxChanges.name
        this.description = description
    }

    fun Arguments.showChart(description: Key) = optionalBoolean {
        name = VoteBotTranslations.Common.ShowChart.name
        this.description = description
    }

    fun Arguments.hideResults(description: Key) = optionalBoolean {
        name = VoteBotTranslations.Common.HideResults.name
        this.description = description
    }

    fun Arguments.publicResults(description: Key) = optionalBoolean {
        name = VoteBotTranslations.Common.PublicResults.name
        this.description = description
    }

    fun Arguments.emojiMode(description: Key) = optionalEnumChoice<ChoiceEmojiMode> {
        name = VoteBotTranslations.Common.EmojiMode.name
        this.description = description
        typeName = EMPTY_KEY
    }
}

enum class ChoiceEmojiMode(override val readableName: Key, val mode: PollSettings.EmojiMode) : ChoiceEnum {
    ON(VoteBotTranslations.Vote.EmojiMode.on, PollSettings.EmojiMode.ON),
    OFF(VoteBotTranslations.Vote.EmojiMode.off, PollSettings.EmojiMode.OFF),
    CUSTOM(VoteBotTranslations.Vote.EmojiMode.custom, PollSettings.EmojiMode.CUSTOM)
}

fun PollSettings.EmojiMode.toChoiceEmoji() = when (this) {
    PollSettings.EmojiMode.ON -> ChoiceEmojiMode.ON
    PollSettings.EmojiMode.OFF -> ChoiceEmojiMode.OFF
    PollSettings.EmojiMode.CUSTOM -> ChoiceEmojiMode.CUSTOM
}

@Suppress("LeakingThis")
@OptIn(IKnowWhatIAmDoing::class)
abstract class AbstractPollSettingsArguments : SortedArguments(), PollSettingsArguments {
    override val maxVotes by maxVotes(VoteBotTranslations.Poll.Create.Arguments.maxVotes)
    override val maxChanges by maxChanges(VoteBotTranslations.Poll.Create.Arguments.maxChanges)
    override val hideResults: Boolean? by hideResults(VoteBotTranslations.Poll.Create.Arguments.hideResults)
    override val publicResults: Boolean? by publicResults(VoteBotTranslations.Poll.Create.Arguments.publicResults)
    override val deleteAfterPeriod by voteDuration(VoteBotTranslations.Poll.Create.Arguments.deleteAfterPeriod)
    override val showChartAfterClose: Boolean? by showChart(VoteBotTranslations.Poll.Create.Arguments.showChartAfterClose)
    private val emojiModeOption by emojiMode(VoteBotTranslations.Poll.Create.Arguments.emojiModeOption)
    override val emojiMode: PollSettings.EmojiMode?
        get() = emojiModeOption?.mode
}

fun <T> decide(current: T?, new: T?): T? = new ?: current
