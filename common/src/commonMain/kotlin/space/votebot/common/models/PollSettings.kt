@file:OptIn(ExperimentalTime::class)

package space.votebot.common.models

import kotlinx.serialization.Serializable
import space.votebot.common.serializers.DurationSerializer
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/**
 * interface for available settings for polls.
 *
 * @property deleteAfter the [Duration] after which the [Poll] should be closed automatically
 * @property showChartAfterClose whether to show a pie chart of the results, when the [Poll] gets closed
 * @property maxVotes the maximum amount of votes per user
 * @property maxChanges the maximum amount of times a user is allowed to change their vote
 * @property hideResults whether to hide results from users, who don't have voted yet
 * @property publicResults whether to share who voted for what with the author of the poll
 * @property emojiMode how to use emojis on this vote
 */
public interface PollSettings {
    public val deleteAfter: Duration?
    public val showChartAfterClose: Boolean?
    public val maxVotes: Int?
    public val maxChanges: Int?
    public val hideResults: Boolean?
    public val publicResults: Boolean?
    public val emojiMode: EmojiMode?

    /**
     * Indicated whether this instance already contains all available information.
     */
    public val complete: Boolean
        get() = deleteAfter != null &&
                showChartAfterClose != null &&
                maxVotes != null &&
                maxChanges != null &&
                hideResults != null &&
                publicResults != null

    /**
     * Describes how to use emojis on this poll.
     */
    @Serializable
    public enum class EmojiMode {
        ON,
        OFF,
        CUSTOM
    }
}

/**
 * Implementation of [PollSettings] used to store in settings, therefor all fields are optional.
 */
@Serializable
public data class StoredPollSettings(
    @Serializable(with = DurationSerializer::class) override val deleteAfter: Duration? = null,
    override val showChartAfterClose: Boolean? = null,
    override val maxVotes: Int? = null,
    override val maxChanges: Int? = null,
    override val hideResults: Boolean? = null,
    override val publicResults: Boolean? = null,
    override val emojiMode: PollSettings.EmojiMode? = null
) : PollSettings

/**
 * Implementation of [PollSettings] where all fields are not optional, used to create polls.
 */
@Serializable
public data class FinalPollSettings(
    @Serializable(with = DurationSerializer::class) override val deleteAfter: Duration?,
    override val showChartAfterClose: Boolean,
    override val maxVotes: Int,
    override val maxChanges: Int,
    override val hideResults: Boolean,
    override val publicResults: Boolean,
    override val emojiMode: PollSettings.EmojiMode = PollSettings.EmojiMode.OFF
) : PollSettings

/**
 * Merges this instance of [PollSettings] with [other] to create a [FinalPollSettings] instance.
 */
public fun PollSettings.merge(other: PollSettings?): FinalPollSettings {
    return FinalPollSettings(
        deleteAfter ?: other?.deleteAfter,
        (showChartAfterClose ?: other?.showChartAfterClose) != false,
        (maxVotes ?: other?.maxVotes ?: 1).takeIf { maxChanges == null || maxChanges == 1 } ?: 0,
        (maxChanges ?: other?.maxChanges ?: 1).takeIf { maxVotes == null || maxVotes == 1 } ?: 0,
        (hideResults ?: other?.hideResults) == true,
        (publicResults ?: other?.publicResults) == true,
        emojiMode ?: other?.emojiMode ?: PollSettings.EmojiMode.ON
    )
}
