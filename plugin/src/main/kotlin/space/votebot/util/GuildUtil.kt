package space.votebot.util

import dev.kord.common.entity.ApplicationIntegrationType
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.entity.interaction.GuildInteraction
import dev.kordex.core.commands.application.slash.SlashCommandContext

val SlashCommandContext<*, *, *>.voteSafeGuild: GuildBehavior?
    get() = (event.interaction as? GuildInteraction)?.guild
        ?.takeIf { event.interaction.authorizingIntegrationOwners.containsKey(ApplicationIntegrationType.GuildInstall) }
