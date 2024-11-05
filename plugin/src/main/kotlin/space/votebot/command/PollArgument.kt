package space.votebot.command

import com.mongodb.client.model.Filters.and
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.ChannelBehavior
import dev.kord.core.behavior.channel.GuildMessageChannelBehavior
import dev.kord.core.behavior.interaction.suggestString
import dev.kord.core.entity.interaction.AutoCompleteInteraction
import dev.kord.core.entity.interaction.OptionValue
import dev.kord.core.entity.interaction.StringOptionValue
import dev.kord.rest.builder.interaction.StringChoiceBuilder
import dev.kordex.core.DiscordRelayedException
import dev.kordex.core.annotations.InternalAPI
import dev.kordex.core.annotations.converters.Converter
import dev.kordex.core.annotations.converters.ConverterType
import dev.kordex.core.checks.interactionFor
import dev.kordex.core.commands.Argument
import dev.kordex.core.commands.CommandContext
import dev.kordex.core.commands.OptionWrapper
import dev.kordex.core.commands.converters.SingleConverter
import dev.kordex.core.commands.converters.Validator
import dev.kordex.core.commands.converters.builders.ConverterBuilder
import dev.kordex.core.i18n.EMPTY_KEY
import dev.kordex.core.i18n.toKey
import dev.kordex.core.i18n.types.Key
import dev.kordex.core.i18n.withContext
import dev.kordex.core.utils.hasPermission
import dev.kordex.parser.StringParser
import dev.schlaubi.mikbot.plugin.api.util.discordError
import dev.schlaubi.mikbot.plugin.api.util.safeInput
import dev.schlaubi.stdx.core.limit
import info.debatty.java.stringsimilarity.Levenshtein
import mu.KotlinLogging
import org.litote.kmongo.eq
import space.votebot.common.models.Poll
import space.votebot.core.VoteBotDatabase
import space.votebot.core.findOneByMessage
import space.votebot.translations.VoteBotTranslations
import space.votebot.util.jumpUrl

private val levenshtein = Levenshtein()

private val LOG = KotlinLogging.logger { }

/**
 * Converter finding a [Poll] from a message link or id using auto-complete
 */
@Converter(
    "poll",

    types = [ConverterType.SINGLE]
)
// This is a modified version of: https://github.com/Kord-Extensions/kord-extensions/blob/f0334b7025d23874b37b2f9c82a1b12eb57efb0d/kord-extensions/src/main/kotlin/com/kotlindiscord/kord/extensions/commands/converters/impl/MessageConverter.kt
class PollConverter(validator: Validator<Poll> = null) : SingleConverter<Poll>(validator) {
    override val signatureType: Key = EMPTY_KEY

    override fun withBuilder(builder: ConverterBuilder<Poll>): SingleConverter<Poll> {
        val builderWithAutoComplete = builder.apply { autoComplete { onAutoComplete() } }
        return super.withBuilder(builderWithAutoComplete)
    }

    override suspend fun parse(parser: StringParser?, context: CommandContext, named: String?): Boolean {
        val text = parser?.parseNext()?.data ?: return false

        return parseText(text, context)
    }

    override suspend fun parseOption(context: CommandContext, option: OptionValue<*>): Boolean {
        val optionValue = (option as? StringOptionValue)?.value ?: return false

        return parseText(optionValue, context)
    }

    private suspend fun parseText(text: String, context: CommandContext): Boolean {
        val message = findMessage(text, context)

        val poll = VoteBotDatabase.polls.findOneByMessage(message)
            ?: discordError(VoteBotTranslations.Commands.Generic.pollNotFound)
        val user = context.getUser()
        if (user?.id?.value != poll.authorId &&
            context.getMember()?.run { asMember().hasPermission(Permission.ManageGuild) } != true
        ) {
            discordError(VoteBotTranslations.Commands.Generic.noPermission)
        }

        parsed = poll

        return true
    }

    @OptIn(InternalAPI::class)
    override suspend fun toSlashOption(arg: Argument<*>) =
        OptionWrapper(arg.displayName, arg.description, { required = true }, StringChoiceBuilder::class)

    private suspend fun AutoCompleteInteraction.onAutoComplete() {
        val safeInput = focusedOption.safeInput

        val possible = VoteBotDatabase.polls
            .find(
                and(
                    Poll::authorId eq user.id.value,
                    Poll::guildId eq data.guildId.value?.value
                )
            )
            .toList()
            .sortedBy { levenshtein.distance(safeInput, it.title, 50) }

        if (possible.isNotEmpty()) {
            suggestString {
                possible.subList(0, 25.coerceAtMost(possible.size)).forEach {
                    if (it.messages.isNotEmpty()) {
                        choice(it.title.limit(100), it.messages.first().jumpUrl)
                    } else {
                        VoteBotDatabase.polls.deleteOneById(it.id)
                    }
                }
            }
        }
    }

    private suspend fun findMessage(arg: String, context: CommandContext): Poll.Message {
        return if (arg.startsWith("https://")) { // It's a message URL
            @Suppress("MagicNumber")
            val split: List<String> = arg.substring(8).split("/").takeLast(3)

            @Suppress("MagicNumber")
            if (split.size < 3) {
                throw DiscordRelayedException(
                    "converters.message.error.invalidUrl".toKey().withOrdinalPlaceholders(arg)
                )
            }

            @Suppress("MagicNumber")
            val gid: Snowflake? = try {
                split.first().takeIf { it != "@me" }?.let(::Snowflake)
            } catch (_: NumberFormatException) {
                throw DiscordRelayedException(
                    "converters.message.error.invalidGuildId".toKey().withOrdinalPlaceholders(split[0])
                )
            }

            @Suppress("MagicNumber")
            val cid: Snowflake = try {
                Snowflake(split[1])
            } catch (_: NumberFormatException) {
                throw DiscordRelayedException(
                    "converters.message.error.invalidChannelId".toKey().withOrdinalPlaceholders(split[1])
                )
            }

            val channel = gid?.let {
                kord.getGuildOrNull(it)?.getChannel(cid)?.id
            } ?: interactionFor(context.eventObj)!!.channelId

            @Suppress("MagicNumber")
            val mid: Snowflake = try {
                Snowflake(split[2])
            } catch (_: NumberFormatException) {
                throw DiscordRelayedException(
                    "converters.message.error.invalidMessageId".toKey().withOrdinalPlaceholders(split[2])
                )
            }

            Poll.Message(mid.value, channel.value, gid?.value)
        } else { // Try a message ID
            val channel: ChannelBehavior = context.getChannel()

            if (channel !is GuildMessageChannelBehavior) {
                LOG.trace { "Current channel is not a guild message channel or DM channel." }

                errorNoMessage(arg, context)
            }

            @Suppress("MagicNumber")
            val messageId: ULong = try {
                arg.toULong()
            } catch (e: NumberFormatException) {
                throw DiscordRelayedException(
                    "converters.message.error.invalidMessageId".toKey().withOrdinalPlaceholders(arg)
                )
            }

            return Poll.Message(messageId, channel.id.value, channel.guildId.value)
        }
    }

    private suspend fun errorNoMessage(arg: String, context: CommandContext): Nothing {
        throw DiscordRelayedException(
            "converters.message.error.missing".toKey().withContext(context).withOrdinalPlaceholders(arg)
        )
    }
}
