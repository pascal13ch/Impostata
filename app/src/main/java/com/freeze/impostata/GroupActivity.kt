package com.freeze.impostata


import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.freeze.impostata.model.Group
import androidx.core.graphics.toColorInt
import com.freeze.impostata.model.Player
import com.google.gson.Gson
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import android.annotation.SuppressLint
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView


class GroupActivity : AppCompatActivity() {

    private var groups = mutableListOf<Group>() // Ideally load from local storage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        setContentView(R.layout.activity_group)
        supportActionBar?.hide()

        groups = loadGroups(this)
        displayGroups(findViewById(R.id.groupListContainer))

        val container = findViewById<LinearLayout>(R.id.groupListContainer)
        displayGroups(container)

        val addGroupButton = findViewById<Button>(R.id.addGroupButton)
        addGroupButton.setOnClickListener {
            showGroupDialog(
                onSave = { newGroup ->
                    groups.add(newGroup)
                    saveGroups(this, groups)
                    displayGroups(findViewById(R.id.groupListContainer))
                }
            )

            Log.d("AddGroupButton","Clicked")
        }

    }

    private fun displayGroups(container: LinearLayout) {
        container.removeAllViews()
        for ((index, group) in groups.withIndex()) {
            val view = layoutInflater.inflate(R.layout.item_group, container, false)
            view.findViewById<TextView>(R.id.groupNameText).text = group.name
            view.findViewById<TextView>(R.id.playerListText).text = group.players.joinToString(", ") { it.name }
            view.findViewById<LinearLayout>(R.id.groupCard).setBackgroundColor(group.colorHex.toColorInt())

            // Edit button listener
            view.findViewById<Button>(R.id.editGroupBtn).setOnClickListener {
                showGroupDialog(
                    initialGroup = groups[index],
                    onSave = { updatedGroup ->
                        groups[index] = updatedGroup
                        saveGroups(this, groups)
                        displayGroups(findViewById(R.id.groupListContainer))
                    },
                    onDelete = {
                        groups.removeAt(index)
                        saveGroups(this, groups)
                        displayGroups(findViewById(R.id.groupListContainer))
                    }
                )

            }

            // Group card click - select group and return
            view.findViewById<LinearLayout>(R.id.groupCard).setOnClickListener {
                selectGroup(group)
            }

            container.addView(view)
        }
    }


    private fun showGroupDialog(
        initialGroup: Group? = null,
        onSave: (Group) -> Unit,
        onDelete: (() -> Unit)? = null
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_group, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.editGroupName)
        val addPlayerButton = dialogView.findViewById<Button>(R.id.btnAddPlayer)
        val playerContainer = dialogView.findViewById<LinearLayout>(R.id.playerInputsContainer)
        val deleteButton = dialogView.findViewById<Button?>(R.id.btnDelete)

        if (initialGroup != null) {
            nameInput.setText(initialGroup.name)
            initialGroup.players.forEach { player ->
                addPlayerInput(playerContainer, player.name)
            }
            deleteButton.visibility = View.VISIBLE
        } else {
            repeat(3) { addPlayerInput(playerContainer) }
            deleteButton.visibility = View.GONE
        }

        addPlayerButton.setOnClickListener {
            if (playerContainer.childCount < 20) {
                addPlayerInput(playerContainer)
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val addButton = dialogView.findViewById<Button>(R.id.btnAdd)
        val cancelButton = dialogView.findViewById<Button>(R.id.btnCancel)

        val layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = 8
        }

        addButton.layoutParams = layoutParams
        cancelButton.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        addButton.setPadding(0, 8, 0, 8)
        cancelButton.setPadding(0, 8, 0, 8)

        listOf(addButton, cancelButton).forEach { btn ->
            btn.setTextColor(ContextCompat.getColor(this, R.color.pixel_text))
            btn.backgroundTintList = ColorStateList.valueOf("#444444".toColorInt())
            btn.typeface = ResourcesCompat.getFont(this, R.font.pixel_font)
            btn.textSize = 12f
            btn.ellipsize = null
            btn.maxLines = 1
        }

        addButton.setOnClickListener {
            val groupName = nameInput.text.toString().trim()
            val playerNames = playerContainer.children
                .mapNotNull { row ->
                    (row as? LinearLayout)?.children
                        ?.filterIsInstance<EditText>()
                        ?.firstOrNull()
                        ?.text
                        ?.toString()
                        ?.trim()
                }
                .filter { it.isNotEmpty() }

            if (groupName.isNotEmpty() && !playerNames.none()) {
                val group = Group(
                    name = groupName,
                    colorHex = initialGroup?.colorHex ?: getRandomColorHex(),
                    players = playerNames.map { Player(it) }.toMutableList()
                )
                onSave(group)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Bitte Gruppennamen und Spieler eingeben", Toast.LENGTH_SHORT).show()
            }
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        deleteButton?.setOnClickListener {
            onDelete?.invoke()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun addPlayerInput(container: LinearLayout, name: String = "") {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
        }

        val input = EditText(this).apply {
            hint = "Spielername"
            setText(name)
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(ContextCompat.getColor(context, R.color.pixel_text))
            setHintTextColor(Color.GRAY)
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.pixel_text))
            typeface = ResourcesCompat.getFont(context, R.font.pixel_font)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val deleteBtn = Button(this, null, 0, R.style.PixelXButton).apply {
            text = "x"
            setTextColor(ContextCompat.getColor(this@GroupActivity, R.color.pixel_text))
            backgroundTintList = ColorStateList.valueOf("#444444".toColorInt())
            typeface = ResourcesCompat.getFont(this@GroupActivity, R.font.pixel_font)
            textSize = 14f
            setOnClickListener {
                if (container.childCount > 3) {
                    container.removeView(row)
                } else {
                    Toast.makeText(this@GroupActivity, "Mindestens 3 Spieler benötigt", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val dragHandle = ImageView(this).apply {
            setImageResource(R.drawable.ic_drag_handle)
            contentDescription = "Verschieben"
            val size = (40 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            setPadding(size / 5, size / 5, size / 5, size / 5)
        }
        attachDragHandle(dragHandle, row, container)

        row.addView(dragHandle)
        row.addView(input)
        row.addView(deleteBtn)
        container.addView(row)
    }

    // Reorders rows by swapping the dragged row with its neighbours. The neighbour is moved
    // rather than the dragged row, because removing the dragged row would cancel the gesture.
    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragHandle(handle: View, row: View, container: LinearLayout) {
        var lastRawY = 0f
        var awaitingLayout = false

        // After a swap the row's top jumps; offset translationY so it stays under the finger
        val layoutListener = View.OnLayoutChangeListener { _, _, top, _, _, _, oldTop, _, _ ->
            if (top != oldTop) row.translationY -= (top - oldTop)
            awaitingLayout = false
        }

        handle.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.parent.requestDisallowInterceptTouchEvent(true)
                    lastRawY = event.rawY
                    awaitingLayout = false
                    row.addOnLayoutChangeListener(layoutListener)
                    row.animate().cancel()
                    row.alpha = 0.7f
                    row.translationZ = 16f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    row.translationY += event.rawY - lastRawY
                    lastRawY = event.rawY

                    if (!awaitingLayout) {
                        val index = container.indexOfChild(row)
                        val center = row.top + row.translationY + row.height / 2f
                        val next = container.getChildAt(index + 1)
                        val prev = if (index > 0) container.getChildAt(index - 1) else null

                        if (next != null && center > next.top + next.height / 2f) {
                            awaitingLayout = true
                            container.removeView(next)
                            container.addView(next, index)
                        } else if (prev != null && center < prev.top + prev.height / 2f) {
                            awaitingLayout = true
                            container.removeView(prev)
                            container.addView(prev, index)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent.requestDisallowInterceptTouchEvent(false)
                    row.removeOnLayoutChangeListener(layoutListener)
                    row.animate()
                        .translationY(0f)
                        .translationZ(0f)
                        .alpha(1f)
                        .setDuration(150)
                        .start()
                    true
                }
                else -> false
            }
        }
    }



    private fun getRandomColorHex(): String {
        val colors = listOf("#F44336", "#2196F3", "#4CAF50", "#FF9800", "#9C27B0", "#00BCD4", "#FF5722")
        return colors.random()
    }

    fun saveGroups(context: Context, groups: List<Group>) {
        val prefs = context.getSharedPreferences("impostata_prefs", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = gson.toJson(groups)
        prefs.edit { putString("groups_json", json) }
    }

    fun loadGroups(context: Context): MutableList<Group> {
        val prefs = context.getSharedPreferences("impostata_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("groups_json", null) ?: return mutableListOf()
        val gson = Gson()
        val type = com.google.gson.reflect.TypeToken.getParameterized(MutableList::class.java, Group::class.java).type
        return gson.fromJson(json, type)
    }

    private fun selectGroup(group: Group) {
        // Pass selected group back to caller activity or wherever needed
        val intent = Intent()
        intent.putExtra("selectedGroupName", group.name)
        intent.putExtra("selectedGroupPlayers", ArrayList(group.players.map { it.name }))
        setResult(RESULT_OK, intent)
        finish()
    }


}
