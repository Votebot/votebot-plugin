package space.votebot.commands.vote.create

import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.optionalChannel
import dev.kordex.core.commands.converters.impl.string
import dev.kord.common.entity.ChannelType
import dev.kord.core.entity.channel.Channel
import space.votebot.common.models.PollSettings
import space.votebot.translations.VoteBotTranslations

interface CreateSettings : BasicCreateOptions {
    val answers: List<String>
    val settings: PollSettings
}

interface BasicCreateOptions {
    val title: String
    val channel: Channel?

    fun Arguments.voteChannel() = optionalChannel {
        name = VoteBotTranslations.Generic.CreateArguments.Channel.name
        description = VoteBotTranslations.Generic.CreateArguments.channel

        requiredChannelTypes.add(ChannelType.GuildText)
    }

    fun Arguments.voteTitle() = string {
        name = VoteBotTranslations.Generic.CreateArguments.Title.name
        description = VoteBotTranslations.Generic.CreateArguments.title
    }
}

