package space.votebot.commands

import space.votebot.commands.vote.*
import space.votebot.commands.vote.create.createCommands
import space.votebot.core.VoteBotModule

suspend fun VoteBotModule.commands() {
    createCommands()
    closeCommand()
    closeMessageCommand()
    statusCommand()
    changeHeadingCommand()
    addOptionCommand()
    removeOptionCommand()

//    infoCommand()
}
