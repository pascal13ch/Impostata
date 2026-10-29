// GameLogic.kt (with GameSession abstraction)
// GameLogic.kt (Fix Jester in Grid Mode)
package com.freeze.impostata

import android.content.Context
import android.util.Log
import com.freeze.impostata.model.Player
import com.freeze.impostata.model.Role
import com.freeze.impostata.model.WordPair
import kotlin.random.Random

object GameLogic {
    private val wordRepository = WordRepository()
    private val roleAssigner = RoleAssigner()
    private var session = GameSession()

    var chanceAllImpostor = 1
    var chanceNoImpostor = 1
    var chanceAllJester = 1
    var chanceJester = 1
    var chanceRandomWords = 1
    var chanceOneCrew = 1

    // name, probability (in %), callback method
    // The callback returns true if it fully set up the game and normal role assignment should be skipped.
    val chaosModes: List<ChaosMode> = listOf(
        ChaosMode("No Impostor", { chanceNoImpostor }) { ctx ->
            ctx.impostorCount = 0
            ctx.session.isCrewGame = true
            false
        },
        ChaosMode("All Impostor", { chanceAllImpostor }) { ctx ->
            if (ctx.isGrid) {
                ctx.session.gridAssignedPairs = MutableList(ctx.playerCount) { Role.IMPOSTOR to null }
                ctx.session.selectedPair = wordRepository.nextWordPair()
            } else {
                ctx.session.players.forEach { it.role = Role.IMPOSTOR }
            }
            ctx.session.remainingImpostors = ctx.playerCount
            ctx.session.startImpostors = ctx.playerCount
            ctx.session.isImpostorGame = true
            true
        },
        ChaosMode("One Crew", { chanceOneCrew }) { ctx ->
            ctx.impostorCount = Random.nextInt(1, ctx.playerCount - 1)
            false
        },
        ChaosMode("All Jester", { chanceAllJester }) { ctx ->
            ctx.impostorCount = 0
            ctx.session.isJesterGame = true
            if (ctx.isGrid) {
                ctx.session.gridAssignedPairs = MutableList(ctx.playerCount) { Role.JESTER to null}
            } else {
                ctx.session.players.forEach { it.role = Role.JESTER }
            }
            ctx.session.selectedPair = wordRepository.nextWordPair()
            true
        },
        ChaosMode("All random Words", { chanceRandomWords }) { ctx ->
            ctx.impostorCount = 0
            ctx.session.isRandomWordGame = true
            false
        }
    )

    private fun rollChaosMode(): ChaosMode? {
        val roll = Random.nextInt(100)
        var cumulative = 0
        val mode = chaosModes.firstOrNull {
            cumulative += it.chance()
            roll < cumulative
        }
        Log.d("GameMode", "Roll=$roll, mode=${mode?.name ?: "Normal"}")
        return mode
    }

    val players get() = session.players
    val gridAssignedPairs get() = session.gridAssignedPairs

    fun initWordPairs(context: Context) {
        wordRepository.init(context)
    }

    val gameEnded: Boolean
        get() = session.gameEnded

    fun setupGame(playerNames: List<String>, undercoverCount: Int, impostorCountOriginal: Int): Boolean {
        session = GameSession()
        var impostorCount = impostorCountOriginal
        if (undercoverCount + impostorCount >= playerNames.size) return false

        session.players = playerNames.mapIndexed { i, name ->
            Player(name.trim().ifEmpty { "Spieler ${i + 1}" })
        }.toMutableList()

        val ctx = ChaosContext(session, playerNames.size, isGrid = false, impostorCount = impostorCount)
        if (rollChaosMode()?.apply?.invoke(ctx) == true) return true

        session.selectedPair = wordRepository.nextWordPair()

        session.players.forEach { it.role = Role.CREW }
        roleAssigner.assign(session.players, Role.UNDERCOVER, undercoverCount)
        roleAssigner.assign(session.players, Role.IMPOSTOR, impostorCount)
        if (session.players.count { it.role == Role.CREW } > 0 && Random.nextInt(100) < chanceJester) {
            roleAssigner.assign(session.players, Role.JESTER, 1)
        }

        session.players.forEach { it.originalRole = it.role }
        session.remainingImpostors = impostorCount
        session.startImpostors = impostorCount
        return true
    }

    fun prepareRolesForGrid(playerNames: List<String>, undercoverCount: Int, impostorCountOriginal: Int) {
        session = GameSession()
        val playerCount = playerNames.size
        var impostorCount = impostorCountOriginal

        session.players = playerNames.mapIndexed { i, name ->
            Player(name.trim().ifEmpty { "Spieler ${i + 1}" })
        }.toMutableList()

        // Determine chaos mode like setupGame()
        val ctx = ChaosContext(session, playerCount, isGrid = true, impostorCount = impostorCount)
        if (rollChaosMode()?.apply?.invoke(ctx) == true) return
        impostorCount = ctx.impostorCount

        // Proceed as usual otherwise
        val selected = wordRepository.nextWordPair()

        val roles = buildList {
            repeat(impostorCount) { add(Role.IMPOSTOR) }
            repeat(undercoverCount) { add(Role.UNDERCOVER) }
            repeat(playerCount - impostorCount - undercoverCount) { add(Role.CREW) }
        }.toMutableList()

        if (roles.count { it == Role.CREW } > 0 && Random.nextInt(100) < chanceJester && !ctx.session.isJesterGame) {
            val crewIndices = roles.withIndex().filter { it.value == Role.CREW }.map { it.index }
            if (crewIndices.isNotEmpty()) {
                val jesterIndex = crewIndices.random()
                roles[jesterIndex] = Role.JESTER
            }
        }

        session.gridAssignedPairs = roles.shuffled().map { role ->
            val word = when (role) {
                Role.IMPOSTOR -> null
                Role.UNDERCOVER -> selected.undercoverWord
                Role.CREW, Role.JESTER -> selected.crewWord
                else -> null
            }
            role to word
        }.toMutableList()

        session.selectedPair = selected
        session.startImpostors = roles.count { it == Role.IMPOSTOR }
        session.remainingImpostors = session.startImpostors
    }

    fun getWordForPlayer(index: Int): String? {
        var word = session.selectedPair?.crewWord

        if (session.isRandomWordGame) {
            word = wordRepository.nextWordPair().crewWord;
        }

        return when (session.players.getOrNull(index)?.role) {
            Role.CREW -> word
            Role.JESTER -> "Du bist Jester. Das Wort ist: \n$word"
            Role.UNDERCOVER -> session.selectedPair?.undercoverWord
            Role.IMPOSTOR -> "Du bist der Impostor."
            else -> null
        }
    }

    fun ejectPlayer(index: Int) {
        val ejected = session.players.count { it.isEjected }
        session.players.getOrNull(index)?.let {
            if (!it.isEjected) {
                if (it.role == Role.JESTER && ejected == 0) session.gameEnded = true
                if (it.role == Role.IMPOSTOR) session.remainingImpostors--
                it.role = Role.EJECTED
                it.isEjected = true
            }
        }

        Log.d("Ejected Player", "playerCount=${session.players.count()}, ejected=${ejected}")

        if(session.players.count() == (ejected + 1)) {
            session.gameEnded = true
        }
    }

    fun checkImpostorGuessAndEndGame(guess: String): Boolean {
        val correct = guess.trim().lowercase() == session.selectedPair?.crewWord?.lowercase()
        if (correct) session.gameEnded = true
        return correct
    }

    fun getRemainingRoleCount(role: Role): Int =
        session.players.count { !it.isEjected && it.role == role }

    fun isGameOver(): Boolean = !session.isCrewGame && !session.isJesterGame && !session.isRandomWordGame && session.remainingImpostors == 0 || session.gameEnded

    fun getGameSummary(): String {
        val grouped = session.players.groupBy { it.originalRole }
        return buildString {
            append("Spielzusammenfassung:")

            if(!session.isRandomWordGame && !session.isImpostorGame) {
                append("\n\nDas Wort war: ${session.selectedPair?.crewWord}")
                append("\nUndercover Wort: ${session.selectedPair?.undercoverWord}")
            }

            if(session.isRandomWordGame) append("\n\nJeder hatte ein anderes Wort")
            append("\n\n")

            Role.entries.forEach { role ->
                grouped[role]?.let {
                    append("🔹 ${role.name}:\n")
                    it.forEach { p -> append("• ${p.name}\n") }
                    append("\n")
                }
            }
        }.trim()
    }

    fun resetGame() {
        session = GameSession()
        wordRepository.reset()
    }

    fun addWordPair(crew: String, undercover: String) {
        wordRepository.addCustomPair(WordPair(crew.trim(), undercover.trim()))
    }
}

class ChaosMode(
    val name: String,
    val chance: () -> Int,
    val apply: (ChaosContext) -> Boolean
)

class ChaosContext(
    val session: GameSession,
    val playerCount: Int,
    val isGrid: Boolean,
    var impostorCount: Int
)

class GameSession {
    var players: MutableList<Player> = mutableListOf()
    var selectedPair: WordPair? = null
    var remainingImpostors: Int = 0
    var startImpostors: Int = 0
    var gameEnded: Boolean = false
    var isCrewGame: Boolean = false
    var isImpostorGame: Boolean = false
    var isJesterGame: Boolean = false
    var isRandomWordGame: Boolean = false
    var gridAssignedPairs: MutableList<Pair<Role, String?>> = mutableListOf()
}

class WordRepository {
    private val wordPairsMaster = mutableListOf<WordPair>()
    private val usedWordPairs = mutableListOf<WordPair>()
    private val customWordPairs = mutableListOf<WordPair>()
    private var wordPairs = mutableListOf<WordPair>()

    fun init(context: Context) {
        if (wordPairsMaster.isEmpty()) {
            wordPairsMaster += context.assets.open("word_pairs.txt")
                .bufferedReader()
                .lineSequence()
                .mapNotNull { line ->
                    val parts = line.split(',')
                    if (parts.size == 2) WordPair(parts[0].trim(), parts[1].trim()) else null
                }
                .toList()
        }
        reset()
    }

    fun nextWordPair(): WordPair {
        if (wordPairs.isEmpty()) {
            usedWordPairs.clear()
            wordPairs = (wordPairsMaster + customWordPairs).toMutableList()
        }
        return wordPairs.removeAt(Random.nextInt(wordPairs.size)).also { usedWordPairs.add(it) }
    }

    fun reset() {
        wordPairs = (wordPairsMaster + customWordPairs).toMutableList()
    }

    fun addCustomPair(pair: WordPair) {
        customWordPairs.add(pair)
        wordPairs = (wordPairsMaster + customWordPairs).toMutableList()
    }
}

class RoleAssigner {
    fun assign(players: MutableList<Player>, role: Role, count: Int) {
        players.withIndex()
            .filter { it.value.role == Role.CREW }
            .shuffled()
            .take(count)
            .forEach { players[it.index].role = role }
    }
}
