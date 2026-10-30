//GameFragment.kt Main Code (Refactored)
package com.freeze.impostata

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.freeze.impostata.GameLogic.players
import com.freeze.impostata.model.GamePhase
import com.freeze.impostata.model.RevealMode
import com.freeze.impostata.model.Role

class GameFragment : Fragment() {
    private lateinit var gameLogic: GameLogic

    // Layout sections
    private lateinit var setupLayout: ViewGroup
    private lateinit var gameLayout: ViewGroup
    private lateinit var votingLayout: ViewGroup
    private lateinit var resultLayout: ViewGroup

    // Setup phase views
    private lateinit var nameInputsContainer: GridLayout
    private lateinit var playerCountLabel: TextView
    private lateinit var playerCountSlider: SeekBar
    private lateinit var btnStartGame: Button
    private lateinit var btnSelectGroup: Button
    private lateinit var undercoverCountText: TextView
    private lateinit var impostorCountText: TextView
    private lateinit var civilianCountText: TextView
    private lateinit var btnUndercoverPlus: Button
    private lateinit var btnUndercoverMinus: Button
    private lateinit var btnImpostorPlus: Button
    private lateinit var btnImpostorMinus: Button
    private lateinit var groupContainer: LinearLayout

    // Playing phase views
    private lateinit var currentPlayerText: TextView
    private lateinit var btnNextPlayer: Button
    private lateinit var wordTextClick: TextView
    private lateinit var wordTextHold: TextView

    // Voting & result phase views
    private lateinit var votingButtonsContainer: LinearLayout
    private lateinit var voteResultText: TextView
    private lateinit var impostorGuessLayout: ViewGroup
    private lateinit var impostorGuessInput: EditText
    private lateinit var btnConfirmGuess: Button
    private lateinit var btnContinueVoting: Button
    private lateinit var btnRestartGame: Button

    // Misc views
    private lateinit var btnSettings: ImageButton
    private lateinit var remainingRoleLayout: LinearLayout

    // Game state
    private var selectedPlayerCount = 3
    private var currentPlayerIndex = 0
    private var flippedOnce = 0
    private lateinit var revealMode: RevealMode
    private val cardToPlayerMap = mutableMapOf<Int, Int>()
    private var currentGridSelectIndex = 0
    private val revealedCardIndices = mutableSetOf<Int>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_game, container, false)
        initViews(view)
        setupListeners()
        addNameInputs()
        updateRoleSection()
        GameLogic.initWordPairs(requireContext())
        loadPreferences()
        return view
    }

    private fun loadPreferences() {
        val prefs = requireContext().getSharedPreferences("impostata_prefs", Context.MODE_PRIVATE)
        GameLogic.chanceAllImpostor = prefs.getInt("chance_all_impostor", 1)
        GameLogic.chanceNoImpostor = prefs.getInt("chance_no_impostor", 1)
        GameLogic.chanceAllJester = prefs.getInt("chance_jester", 20)
        GameLogic.chanceAllJester = prefs.getInt("change_all_jester", 1)
        GameLogic.chanceRandomWords = prefs.getInt("chance_random_words", 1)
        revealMode = RevealMode.entries[prefs.getInt("reveal_mode", 0)]
    }

    private fun setupFlipRevealUI(view: View) {
        val clickFlip = view.findViewById<View>(R.id.revealClickFlip)
        val grid = view.findViewById<View>(R.id.revealGrid)

        clickFlip?.visibility = View.GONE
        grid?.visibility = View.GONE

        view.findViewById<View>(R.id.cardFront)?.apply {
            visibility = View.VISIBLE
            setOnClickListener(null)
            setOnTouchListener(null)
        }
        view.findViewById<View>(R.id.cardBack)?.visibility = View.GONE
    }

    private fun updateRoleSection() {
        val (undercover, impostor, civilian) = getRoleCounts()
        val maxRoles = selectedPlayerCount / 2 + 1

        undercoverCountText.text = undercover.coerceAtMost(maxRoles).toString()
        impostorCountText.text = (maxRoles - undercover.coerceAtMost(maxRoles)).coerceAtMost(impostor).toString()
        civilianCountText.text = civilian.toString()

        val totalRoles = undercover + impostor
        btnUndercoverPlus.visibility = if (totalRoles < maxRoles) View.VISIBLE else View.INVISIBLE
        btnImpostorPlus.visibility = if (totalRoles < maxRoles) View.VISIBLE else View.INVISIBLE
        btnUndercoverMinus.visibility = if (undercover > 0) View.VISIBLE else View.INVISIBLE
        btnImpostorMinus.visibility = if (impostor > 0) View.VISIBLE else View.INVISIBLE
    }

    private fun getRoleCounts(): Triple<Int, Int, Int> {
        val undercover = undercoverCountText.text.toString().toIntOrNull() ?: 0
        val impostor = impostorCountText.text.toString().toIntOrNull() ?: 0
        val civilian = (selectedPlayerCount - undercover - impostor).coerceAtLeast(0)
        return Triple(undercover, impostor, civilian)
    }

    @SuppressLint("SetTextI18n")
    private fun changeRoleCount(textView: TextView, delta: Int) {
        val current = textView.text.toString().toIntOrNull() ?: 0
        textView.text = (current + delta).coerceAtLeast(0).toString()
        updateRoleSection()
    }

    @SuppressLint("SetTextI18n")
    private fun startGame() {
        setupFlipRevealUI(requireView())

        val playerNames = nameInputsContainer.children
            .mapNotNull { it.findViewById<TextView>(R.id.playerNameText)?.text?.toString()?.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        val (undercoverCount, impostorCount, _) = getRoleCounts()

        if (revealMode == RevealMode.GRID_SELECTION) {
            gameLogic.prepareRolesForGrid(playerNames, undercoverCount, impostorCount)
        } else {
            if (!gameLogic.setupGame(playerNames, undercoverCount, impostorCount)) {
                Toast.makeText(requireContext(), "Zu viele Undercover/Impostor für diese Spieleranzahl.", Toast.LENGTH_SHORT).show()
                return
            }
        }

        setGamePhase(GamePhase.PLAYING)
        when (revealMode) {
            RevealMode.CLICK_TO_FLIP -> {
                requireView().findViewById<View>(R.id.revealClickFlip)?.visibility = View.VISIBLE
                RevealManager(
                    rootView = requireView(),
                    revealMode = revealMode,
                    getCurrentPlayerIndex = { currentPlayerIndex },
                    onWordRevealed = { flippedOnce = 1 }
                ).setup()
                btnNextPlayer.visibility = View.VISIBLE
            }
            RevealMode.HOLD_TO_REVEAL -> {

                requireView().findViewById<View>(R.id.revealClickFlip)?.visibility = View.VISIBLE
                RevealManager(
                    rootView = requireView(),
                    revealMode = revealMode,
                    getCurrentPlayerIndex = { currentPlayerIndex },
                    onWordRevealed = { flippedOnce = 1 }
                ).setup()
                btnNextPlayer.visibility = View.VISIBLE
            }
            RevealMode.GRID_SELECTION -> {
                requireView().findViewById<View>(R.id.revealGrid)?.visibility = View.VISIBLE
                setupCardGrid()
            }
        }

        currentPlayerIndex = 0
        updateCurrentPlayer()
    }


//Define all views
    private fun initViews(view: View) {
        gameLogic = GameLogic

        setupLayout = view.findViewById(R.id.setupLayout)
        gameLayout = view.findViewById(R.id.gameLayout)
        votingLayout = view.findViewById(R.id.votingLayout)
        resultLayout = view.findViewById(R.id.resultLayout)

        playerCountSlider = view.findViewById(R.id.playerCountSlider)
        playerCountLabel = view.findViewById(R.id.playerCountLabel)

        undercoverCountText = view.findViewById(R.id.undercoverCountText)
        impostorCountText = view.findViewById(R.id.ImpostorCountText)

        btnStartGame = view.findViewById(R.id.btnStartGame)
        nameInputsContainer = view.findViewById(R.id.nameInputsContainer)
        currentPlayerText = view.findViewById(R.id.currentPlayerText)

        btnNextPlayer = view.findViewById(R.id.btnNextPlayer)
        votingButtonsContainer = view.findViewById(R.id.votingButtonsLayout)
        voteResultText = view.findViewById(R.id.voteResultText)
        impostorGuessLayout = view.findViewById(R.id.ImpostorGuessLayout)
        impostorGuessInput = view.findViewById(R.id.ImpostorGuessInput)
        btnConfirmGuess = view.findViewById(R.id.btnConfirmGuess)
        btnContinueVoting = view.findViewById(R.id.btnContinueVoting)
        btnRestartGame = view.findViewById(R.id.btnRestartGame)
        btnUndercoverPlus = view.findViewById(R.id.btnUndercoverPlus)
        btnUndercoverMinus = view.findViewById(R.id.btnUndercoverMinus)
        btnImpostorPlus = view.findViewById(R.id.btnImpostorPlus)
        btnImpostorMinus = view.findViewById(R.id.btnImpostorMinus)
        civilianCountText = view.findViewById(R.id.civilianCountText)
        groupContainer = view.findViewById(R.id.selectedGroupContainer)
        btnSelectGroup = view.findViewById(R.id.btnSelectGroup)
        wordTextClick = view.findViewById(R.id.wordTextClick)
        wordTextHold = view.findViewById(R.id.wordTextClick)


        remainingRoleLayout = view.findViewById(R.id.remainingRolesLayout)

        btnSettings = view.findViewById(R.id.settingsButton)
    }

    //Sets up all the listeners
    private fun setupListeners() {
        playerCountSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            @SuppressLint("SetTextI18n")
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                selectedPlayerCount = progress + 3
                playerCountLabel.text = "$selectedPlayerCount Spieler"

                updateRoleSection()
                addNameInputs()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnStartGame.setOnClickListener { startGame() }
        btnNextPlayer.setOnClickListener { nextPlayer() }
        btnConfirmGuess.setOnClickListener { confirmImpostorGuess() }
        btnContinueVoting.setOnClickListener { startVoting() }
        btnRestartGame.setOnClickListener { restartGame() }

        btnUndercoverPlus.setOnClickListener { changeRoleCount(undercoverCountText, +1) }
        btnUndercoverMinus.setOnClickListener { changeRoleCount(undercoverCountText, -1) }
        btnImpostorPlus.setOnClickListener { changeRoleCount(impostorCountText, +1) }
        btnImpostorMinus.setOnClickListener { changeRoleCount(impostorCountText, -1) }

        groupContainer.setOnClickListener { removeGroupView() }
        btnSelectGroup.setOnClickListener {
            val intent = Intent(requireContext(), GroupActivity::class.java)
            selectGroupLauncher.launch(intent)
        }

        btnSettings.setOnClickListener {
            showSettingsDialog()
        }
    }

    //Helper method to quickly change views
    private fun setGamePhase(phase: GamePhase) {
        setupLayout.visibility = if (phase == GamePhase.SETUP) View.VISIBLE else View.GONE
        gameLayout.visibility = if (phase == GamePhase.PLAYING) View.VISIBLE else View.GONE
        votingLayout.visibility = if (phase == GamePhase.VOTING) View.VISIBLE else View.GONE
        resultLayout.visibility = if (phase == GamePhase.RESULT) View.VISIBLE else View.GONE
    }


    //Dialog for editing the names in the setup layout from the Playerslots
    private fun showNameEditDialog(initialName: String, onNameConfirmed: (String) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_name, null)
        val input = dialogView.findViewById<EditText>(R.id.editNameInput).apply {
            setText(initialName)
            setSelection(initialName.length)
            requestFocus()
        }

        val dialog = AlertDialog.Builder(requireContext(), R.style.PixelDialog)
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.btnAdd).setOnClickListener {
            val name = input.text.toString().trim()
            if (name.isNotEmpty()) {
                onNameConfirmed(name)
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Bitte einen gültigen Namen eingeben.", Toast.LENGTH_SHORT).show()
            }
        }

        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnShowListener {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }

        dialog.show()
    }

    //Creates reusable Playerslots for listing the players in the setupscreen
    //Returns the playerslot as a view
    private fun createPlayerSlot(name: String, onEdit: (String) -> Unit): View {
        val playerSlot = layoutInflater.inflate(R.layout.item_player_slot, nameInputsContainer, false)

        playerSlot.layoutParams = GridLayout.LayoutParams().apply {
            width = 0
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(8, 8, 8, 8)
        }

        val nameText = playerSlot.findViewById<TextView>(R.id.playerNameText)
        val editBtn = playerSlot.findViewById<ImageButton>(R.id.editBtn)

        nameText.text = name
        nameText.setOnClickListener {
            editBtn.visibility = if (editBtn.isVisible) View.GONE else View.VISIBLE
        }

        editBtn.setOnClickListener {
            showNameEditDialog(nameText.text.toString()) { newName ->
                nameText.text = newName
                onEdit(newName)
            }
        }

        return playerSlot
    }

    //Function to add customizable players to the playerslist when no group is selected
    //Uses the playerslots from earlier
    private fun addNameInputs() {
        nameInputsContainer.removeAllViews()

        for (i in 0 until selectedPlayerCount) {
            val name = "Spieler ${i + 1}"
            val playerSlot = createPlayerSlot(name) {}
            nameInputsContainer.addView(playerSlot)
        }

        btnStartGame.visibility = View.VISIBLE
    }

    @SuppressLint("SetTextI18n")
    private fun setupCardGrid() {
        val grid = requireView().findViewById<GridLayout>(R.id.cardGrid)
        Log.d("DEBUG", "Setting up card grid with ${players.size} players")

        val (undercoverCount, impostorCount, _) = getRoleCounts()

        if (GameLogic.gridAssignedPairs.size != players.size) {
            // Rebuild roles only if needed
            GameLogic.prepareRolesForGrid(players.map { it.name }, undercoverCount, impostorCount)
        }


        grid?.removeAllViews()
        if (GameLogic.gridAssignedPairs.isEmpty()) {
            Toast.makeText(requireContext(), "Keine Rollen mehr verfügbar.", Toast.LENGTH_SHORT).show()
            return
        }

        val playerLabel = requireView().findViewById<TextView>(R.id.currentPlayerText)
        playerLabel.text = "Karte wählen für: ${players[currentGridSelectIndex].name}"

        for (i in players.indices) {
            val card = LayoutInflater.from(context).inflate(R.layout.card_preview_small, grid, false)
            card.tag = i

            val cardLabel = card.findViewById<TextView>(R.id.cardLabel)
            cardLabel.text = "?"

            card.setOnClickListener {
                if (cardToPlayerMap.containsKey(i) || GameLogic.gridAssignedPairs.isEmpty()) return@setOnClickListener
                card.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).withEndAction {
                    card.animate().scaleX(1f).scaleY(1f).setDuration(100).start()

                    flippedOnce = 1
                    val (role, _) = GameLogic.gridAssignedPairs.removeAt(0)
                    val playerIndex = currentGridSelectIndex

                    players[playerIndex].role = role
                    players[playerIndex].originalRole = role
                    card.setBackgroundResource(R.drawable.ic_cross)
                    cardToPlayerMap[i] = playerIndex
                    card.isEnabled = false

                    showCardRevealDialog(playerIndex)

                    currentGridSelectIndex++
                    if (currentGridSelectIndex < players.size) {
                        playerLabel.text = "Karte wählen für: ${players[currentGridSelectIndex].name}"
                    } else {
                        playerLabel.text = "Alle Karten wurden verteilt."
                        btnNextPlayer.text = "Spiel starten"
                    }
                }.start()
            }

            grid.addView(card)
        }

    }

    private fun showCardRevealDialog(playerIndex: Int) {
        if (playerIndex in revealedCardIndices) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_card_reveal, null)
        val nameText = dialogView.findViewById<TextView>(R.id.DrevealName)
        val wordText = dialogView.findViewById<TextView>(R.id.DrevealWord)
        val roleImage = dialogView.findViewById<ImageView>(R.id.role_image)

        val player = players[playerIndex]
        nameText.text = player.name
        wordText.text = GameLogic.getWordForPlayer(playerIndex) ?: "Kein Wort"

        if(wordText.text.contains("Impostor")) {
            roleImage.visibility = View.VISIBLE
            roleImage.setImageResource(R.drawable.maru)
        }

        val dialog = AlertDialog.Builder(requireContext(), R.style.PixelDialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<Button>(R.id.btnRevealClose).setOnClickListener {
            revealedCardIndices.add(playerIndex)
            flippedOnce = 1
            currentPlayerIndex++
            if (currentGridSelectIndex >= players.size) {
                startVoting()
            }
            dialog.dismiss()
        }

        dialog.show()
    }




    //Handles the passing on of the phone, nextplayer button is then clickable
    @SuppressLint("SetTextI18n")
    private fun updateCurrentPlayer() {
        if (currentPlayerIndex >= players.size) {
            startVoting()
            return
        }

        val player = players[currentPlayerIndex]
        currentPlayerText.text = "Gerät an: \n${player.name}"

        when (revealMode) {
            RevealMode.CLICK_TO_FLIP, RevealMode.HOLD_TO_REVEAL -> {
                RevealManager(
                    rootView = requireView(),
                    revealMode = revealMode,
                    getCurrentPlayerIndex = { currentPlayerIndex },
                    onWordRevealed = { flippedOnce = 1 }
                ).setup()
            }

            RevealMode.GRID_SELECTION -> {
                currentPlayerText.text = "Karte für ${player.name} auswählen"
                btnNextPlayer.visibility = if (currentGridSelectIndex >= players.size) View.VISIBLE else View.GONE
            }
        }
    }

    //Handles the passing on of the phone further
    private fun nextPlayer() {
        if (flippedOnce == 1) {
            when (revealMode) {
                RevealMode.CLICK_TO_FLIP, RevealMode.HOLD_TO_REVEAL -> {
                    view?.findViewById<View>(R.id.cardFront)?.visibility = View.VISIBLE
                    view?.findViewById<View>(R.id.cardBack)?.visibility = View.GONE
                }

                RevealMode.GRID_SELECTION -> {

                }
            }
            currentPlayerIndex++
            flippedOnce = 0
            updateCurrentPlayer()
        } else {
            Toast.makeText(requireContext(), "Schaue dir zuerst das Wort an ;)" , Toast.LENGTH_SHORT).show()
        }
    }

    //Shows the starting and rotation info
    @SuppressLint("SetTextI18n")
    private fun showGameStartDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_game_start, null)
        val messageView = dialogView.findViewById<TextView>(R.id.dialogMessage)

        val activePlayers = gameLogic.players.filter { !it.isEjected }
        if (activePlayers.isEmpty()) return

        val starter = activePlayers.random().name
        val direction = if ((0..1).random() == 0) "Uhrzeigersinn" else "Gegen den Uhrzeigersinn"
        messageView.text = "$starter beginnt.\n\nDie Runde geht in Richtung: \n$direction"

        val dialog = AlertDialog.Builder(requireContext(), R.style.PixelDialog)
            .setView(dialogView)
            .setCancelable(false)
            .show()

        dialogView.findViewById<Button>(R.id.okButton)?.setOnClickListener {
            dialog.dismiss()
        }
    }


    private fun startVoting() {
        if (gameLogic.gameEnded) return
        setGamePhase(GamePhase.VOTING)

        votingButtonsContainer.removeAllViews()

        showGameStartDialog()

        gameLogic.players.forEachIndexed { index, player ->
            if (!player.isEjected) {
                val voteButton = Button(ContextThemeWrapper(requireContext(), R.style.PixelButton)).apply {
                    text = player.name
                    typeface = ResourcesCompat.getFont(context, R.font.pixel_font)
                    setTextColor(ContextCompat.getColor(context, R.color.pixel_text))
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 8, 0, 0)
                    }

                    setOnClickListener { resolveVote(index) }
                }
                votingButtonsContainer.addView(voteButton)

            }
        }
        val noImpostorButton = Button(ContextThemeWrapper(requireContext(), R.style.PixelButton)).apply {
            text = "Kein Impostor vorhanden"
            typeface = ResourcesCompat.getFont(context, R.font.pixel_font)
            setTextColor(ContextCompat.getColor(context, R.color.pixel_text))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0,0,0,0)
            }

            setOnClickListener { voteNoImpostor() }
        }

        votingButtonsContainer.addView(noImpostorButton)
    }

    private fun voteNoImpostor() {
        setGamePhase(GamePhase.RESULT)
        voteResultText.text = "Das Spiel wurde beendet";
        btnContinueVoting.visibility = View.GONE
        showGameEndDialog()
        btnRestartGame.visibility = View.VISIBLE
    }
    @SuppressLint("SetTextI18n")
    private fun resolveVote(index: Int) {
        setGamePhase(GamePhase.RESULT)

        val role = gameLogic.players[index].role
        gameLogic.ejectPlayer(index)

        val playerName = gameLogic.players[index].name
        voteResultText.text = "$playerName wurde rausgeworfen.\n\nRolle: $role"
        val ejectedPlayers = players.count { it.isEjected }


        if (role == Role.IMPOSTOR) {
            impostorGuessLayout.visibility = View.VISIBLE
            btnContinueVoting.visibility = View.GONE
            btnConfirmGuess.visibility = View.VISIBLE
        } else if (role == Role.JESTER && ejectedPlayers == 0) {
            voteResultText.append("\n\nDer Jester wurde erfolgreich rausgeworfen! Er gewinnt alleine.")
            btnContinueVoting.visibility = View.GONE
            btnRestartGame.visibility = View.VISIBLE
            return
        } else {
            impostorGuessLayout.visibility = View.GONE
            btnContinueVoting.visibility = View.VISIBLE
        }

        if (role != Role.IMPOSTOR && gameLogic.isGameOver()) {
            voteResultText.append("\n\nDas Spiel ist beendet")
            btnContinueVoting.visibility = View.GONE
            showGameEndDialog()
            btnRestartGame.visibility = View.VISIBLE
        }
    }

    private fun confirmImpostorGuess() {
        val guess = impostorGuessInput.text.toString()
        val correct = gameLogic.checkImpostorGuessAndEndGame(guess)

        voteResultText.text = if (correct) {
            "Impostor hat richtig geraten! Impostor gewinnen."

        } else {
            "Falsches Wort"
        }

        impostorGuessInput.text?.clear()

        impostorGuessLayout.visibility = View.GONE
        btnConfirmGuess.visibility = View.GONE
        if (!correct && !gameLogic.isGameOver()) {
            btnContinueVoting.visibility = View.VISIBLE
        } else {
            btnRestartGame.visibility = View.VISIBLE
            showGameEndDialog()
        }
    }

    private fun restartGame() {
        gameLogic.resetGame()
        revealedCardIndices.clear()
        cardToPlayerMap.clear()
        remainingRoleLayout.visibility = View.GONE
        currentGridSelectIndex = 0

        view?.findViewById<GridLayout>(R.id.cardGrid)?.removeAllViews()
        view?.findViewById<TextView>(R.id.currentPlayerText)?.text = ""

        btnRestartGame.visibility = View.GONE
        setGamePhase(GamePhase.SETUP)
        impostorGuessInput.setText("")
        val prefs = requireContext().getSharedPreferences("impostata_prefs", Context.MODE_PRIVATE)
        val modeOrdinal = prefs.getInt("reveal_mode", 0)
        revealMode = RevealMode.entries[modeOrdinal]
        val clickFlip = view?.findViewById<View>(R.id.revealClickFlip)
        clickFlip?.let {
            it.findViewById<View>(R.id.cardFront)?.visibility = View.VISIBLE
            it.findViewById<View>(R.id.cardBack)?.visibility = View.GONE }
    }


    private fun removeGroupView() {
        groupContainer.removeAllViews()
        playerCountLabel.visibility = View.VISIBLE
        playerCountSlider.visibility = View.VISIBLE

        // Reset selectedPlayerCount from nameInputs
        selectedPlayerCount = nameInputsContainer.childCount.takeIf { it > 0 } ?: 3
        playerCountSlider.progress = selectedPlayerCount - 3

        updateRoleSection()
        addNameInputs()
    }

    private fun showGameEndDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_game_end, null)
        val summaryText = dialogView.findViewById<TextView>(R.id.endGameSummary)
        summaryText.text = GameLogic.getGameSummary()

        val dialog = AlertDialog.Builder(requireContext(), R.style.PixelDialog)
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.btnEndClose).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }


    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)

        val chanceAll = dialogView.findViewById<EditText>(R.id.editChanceAllImpostors)
        val chanceNone = dialogView.findViewById<EditText>(R.id.editChanceNoImpostors)
        val chanceJester = dialogView.findViewById<EditText>(R.id.editChanceJester)
        val chanceOne = dialogView.findViewById<EditText>(R.id.editChanceOne)
        val chanceAllJester = dialogView.findViewById<EditText>(R.id.editChanceAllJester)
        val chanceAllRandomWords = dialogView.findViewById<EditText>(R.id.editChanceAllRandomWords)
        val importBtn = dialogView.findViewById<Button>(R.id.btnImportWords)

        val prefs = requireContext().getSharedPreferences("impostata_prefs", Context.MODE_PRIVATE)
        chanceAll.setText(prefs.getInt("chance_all_impostor", 1).toString())
        chanceNone.setText(prefs.getInt("chance_no_impostor", 1).toString())
        chanceJester.setText(prefs.getInt("chance_jester", 1).toString())
        chanceAllJester.setText(prefs.getInt("chance_all_jester", 1).toString())
        chanceAllRandomWords.setText(prefs.getInt("chance_random_words", 1).toString())
        chanceOne.setText(prefs.getInt("chance_one_crew", 1).toString())

        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.revealModeRadioGroup)
        when (prefs.getInt("reveal_mode", 0)) {
            0 -> radioGroup.check(R.id.radioClickFlip)
            1 -> radioGroup.check(R.id.radioHoldReveal)
            2 -> radioGroup.check(R.id.radioGridSelect)
        }

        val dialog = AlertDialog.Builder(requireContext(), R.style.PixelDialog)
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.btnAdd).setOnClickListener {
            val mode = when (radioGroup.checkedRadioButtonId) {
                R.id.radioClickFlip -> 0
                R.id.radioHoldReveal -> 1
                R.id.radioGridSelect -> 2
                else -> 0
            }

            prefs.edit {
                putInt("chance_all_impostor", chanceAll.text.toString().toIntOrNull() ?: 0)
                putInt("chance_no_impostor", chanceNone.text.toString().toIntOrNull() ?: 0)
                putInt("chance_jester", chanceJester.text.toString().toIntOrNull() ?: 0)
                putInt("chance_one_crew", chanceOne.text.toString().toIntOrNull() ?: 0)
                putInt("chance_all_jester", chanceAllJester.text.toString().toIntOrNull() ?: 0)
                putInt("chance_random_words", chanceAllRandomWords.text.toString().toIntOrNull() ?: 0)
                putInt("reveal_mode", mode)
            }


            GameLogic.chanceNoImpostor = prefs.getInt("chance_no_impostor", 1)
            GameLogic.chanceAllImpostor = prefs.getInt("chance_all_impostor", 1)
            GameLogic.chanceJester = prefs.getInt("chance_jester", 1)
            GameLogic.chanceAllJester = prefs.getInt("chance_all_jester", 1)
            GameLogic.chanceRandomWords = prefs.getInt("chance_random_words", 1)
            GameLogic.chanceOneCrew = prefs.getInt("chance_one_crew", 1)

            restartGame()
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        importBtn.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "text/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            importWordsLauncher.launch(intent)
        }
        dialog.show()
    }

    private fun showConfirmExitDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_confirm_exit, null)

        val dialog = AlertDialog.Builder(requireContext(), R.style.PixelDialog)
            .setView(dialogView)
            .setCancelable(false)
            .show()

        dialogView.findViewById<Button>(R.id.btnConfirm)?.setOnClickListener {
            restartGame()
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnCancel)?.setOnClickListener {
            dialog.dismiss()
        }
    }

    // Activity result launchers
    private val importWordsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.forEachLine { line ->
                    val parts = line.split(",")
                    if (parts.size == 2) {
                        GameLogic.addWordPair(parts[0].trim(), parts[1].trim())
                    }
                }
                Toast.makeText(requireContext(), "Wörter erfolgreich importiert.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private val selectGroupLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val groupName = result.data?.getStringExtra("selectedGroupName")
            val playerNames = result.data?.getStringArrayListExtra("selectedGroupPlayers") ?: arrayListOf()

            Log.d("TEST", "Group Name: $groupName, Player Names: $playerNames")

            selectedPlayerCount = playerNames.size

            groupContainer.removeAllViews()
            val groupView = LayoutInflater.from(requireContext()).inflate(R.layout.item_group, groupContainer, false)
            groupView.findViewById<TextView>(R.id.groupNameText).text = groupName
            groupView.findViewById<TextView>(R.id.playerListText).text = playerNames.joinToString(", ")
            groupView.findViewById<Button>(R.id.editGroupBtn).visibility = View.GONE
            groupContainer.addView(groupView)

            playerCountLabel.visibility = View.GONE
            playerCountSlider.visibility = View.GONE

            playerCountLabel.text = "$selectedPlayerCount Spieler"
            nameInputsContainer.removeAllViews()

            playerNames.forEach { name ->
                val playerSlot = createPlayerSlot(name) {}
                nameInputsContainer.addView(playerSlot)
            }

            updateRoleSection()
            btnStartGame.visibility = View.VISIBLE
        }
    }
    override fun onResume() {
        super.onResume()
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // If not in setup phase, reset game
                if (setupLayout.visibility != View.VISIBLE) {
                    showConfirmExitDialog()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
}
