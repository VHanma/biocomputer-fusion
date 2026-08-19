package com.vhanma.kialink

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var store: MetamorphStore
    private lateinit var servitorEngine: ServitorEngine
    private lateinit var dreamGate: DreamGateEngine
    private lateinit var statusText: TextView

    private val ink = Color.rgb(8, 6, 16)
    private val panel = Color.rgb(22, 17, 34)
    private val panel2 = Color.rgb(31, 24, 48)
    private val violet = Color.rgb(184, 115, 255)
    private val cyan = Color.rgb(103, 231, 255)
    private val gold = Color.rgb(255, 213, 103)
    private val soft = Color.rgb(207, 197, 222)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = MetamorphStore(this)
        servitorEngine = ServitorEngine(this, store)
        dreamGate = DreamGateEngine(this, store)
        window.statusBarColor = ink
        window.navigationBarColor = ink
        setContentView(buildDashboard())
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) refreshStatus()
    }

    private fun buildDashboard(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(ink)
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(22), dp(16), dp(34))
        }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(text("KIA // METAMORPH", 30f, Color.WHITE, true).apply { letterSpacing = .08f })
        root.addView(text("Recursive archetypal transmutation + crystal progression + servitor automation", 13f, soft).apply {
            setPadding(0, dp(4), 0, dp(12))
        })

        statusText = text("", 14f, cyan, true).apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(panel, 18f, Color.rgb(58, 45, 77))
        }
        root.addView(statusText)

        root.addView(sectionTitle("QUICK SUMMON"))
        root.addView(buttonGrid(listOf(
            "⚔ BATTLE" to { activate("battle") },
            "🧠 CLEAR MIND" to { activate("clear_mind") },
            "🔥 MOTIVATE" to { activate("motivate") },
            "✦ SOCIAL" to { activate("social") },
            "🛡 PROTECT" to { activate("protect") },
            "✚ RECOVER" to { activate("recover") }
        )))

        val complete = actionButton("GOAL COMPLETE → REABSORB + CRYSTAL XP", gold, Color.BLACK) {
            val touched = servitorEngine.completeCurrentGoal()
            if (touched.isEmpty()) toast("No active Paradigm")
            else {
                refreshStatus()
                AlertDialog.Builder(this)
                    .setTitle("Reabsorption complete")
                    .setMessage("Servitors returned after goal completion. Experience fed back into overlapping crystals:\n\n${touched.joinToString("\n• ", prefix = "• ")}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
        root.addView(complete, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(10) })

        root.addView(sectionTitle("CORE SYSTEMS"))
        root.addView(buttonGrid(listOf(
            "💎 CRYSTAL GRAPH" to { showCrystalGraph() },
            "🜏 SERVITORS" to { showServitors() },
            "⌘ COMMANDS" to { showCommands() },
            "⚙ AUTOSUMMON" to { showAutoSummon() },
            "🌙 DREAMGATE" to { showDreamGate() },
            "✧ ASTRAL" to { showAstral() },
            "🔥 PHOENIX" to { showPhoenix() },
            "⚔ PROTECTION" to { showProtection() },
            "◈ PARADIGMS" to { showParadigms() },
            "☰ LOG" to { showLog() }
        )))

        val recall = actionButton("EMERGENCY RECALL ALL", Color.rgb(118, 42, 60), Color.WHITE) {
            servitorEngine.emergencyRecall()
            refreshStatus()
            toast("SUSPEND → RECALL → REABSORB")
        }
        root.addView(recall, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(12) })

        root.addView(text(
            "v0.2 core build. One skill node can belong to many domains at once. Servitor paradigms are the automation layer; crystals are the mastery layer. DreamGate emits real scheduled phone cues. Astral Projection is a separate intentional state and never counts as Phoenix activation.",
            12f, Color.rgb(150, 143, 168)
        ).apply { setPadding(dp(2), dp(14), dp(2), 0) })

        return scroll
    }

    private fun refreshStatus() {
        val p = servitorEngine.currentParadigm()
        val state = store.consciousState().label
        val auto = if (store.autopilotEnabled()) "ON" else "OFF"
        val astral = if (store.astralSessionActive()) " | ASTRAL SESSION ACTIVE" else ""
        statusText.text = "STATE: $state$astral\nPARADIGM: ${p?.name ?: "DORMANT"}\nDAIMON AUTOPILOT: $auto"
    }

    private fun activate(id: String) {
        val result = servitorEngine.activateParadigm(id) ?: return
        refreshStatus()
        AlertDialog.Builder(this)
            .setTitle(result.paradigm.name)
            .setMessage(
                "ACTIVE: ${result.activeRoles}\nBACKGROUND: ${result.backgroundRoles}\n\nPURPOSE\n${result.paradigm.purpose}\n\nINNER SCRIPT\n${result.message}\n\nCompletion automatically reabsorbs the working and transfers experience into connected crystals."
            )
            .setPositiveButton("ACTIVE", null)
            .setNeutralButton("COMPLETE") { _, _ ->
                servitorEngine.completeCurrentGoal()
                refreshStatus()
            }
            .show()
    }

    private fun showCrystalGraph() {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        val detail = text("Tap a crystal. Connected lines are fusion relationships; one node may belong to many domains.", 13f, soft)
        val graph = CrystalGraphView(this, store).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(470))
            onNodeSelected = { node ->
                val xp = store.xpFor(node.id)
                val stage = store.stageFor(node.id)
                detail.text = "${node.name}  •  ${stage.label}  •  $xp XP\n${node.description}\n\nOVERLAPS: ${node.domains.joinToString()}\nSOURCES: ${node.sources.joinToString()}"
            }
        }
        wrap.addView(graph)
        wrap.addView(detail.apply { setPadding(dp(6), dp(8), dp(6), dp(8)) })
        val train = actionButton("TRAIN SELECTED / OPEN SKILL LIST", violet, Color.BLACK) { showSkillList(graph) }
        wrap.addView(train, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        AlertDialog.Builder(this)
            .setTitle("Universal Crystal Graph")
            .setView(wrap)
            .setPositiveButton("CLOSE", null)
            .show()
    }

    private fun showSkillList(graph: CrystalGraphView? = null) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        MetamorphData.skills.forEach { skill ->
            val xp = store.xpFor(skill.id)
            val stage = store.stageFor(skill.id)
            val b = actionButton("${skill.name}\n${stage.label} • $xp XP", panel2, Color.WHITE) {
                showSkill(skill, graph)
            }
            box.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)).apply { bottomMargin = dp(6) })
        }
        val scroll = ScrollView(this).apply { addView(box) }
        AlertDialog.Builder(this)
            .setTitle("Skill Shards")
            .setView(scroll)
            .setPositiveButton("CLOSE", null)
            .show()
    }

    private fun showSkill(skill: SkillNode, graph: CrystalGraphView?) {
        val message = buildString {
            append(skill.description)
            append("\n\nOVERLAPS\n")
            append(skill.domains.joinToString(" • "))
            if (skill.sources.isNotEmpty()) {
                append("\n\nSOURCES\n")
                append(skill.sources.joinToString(" • "))
            }
            if (skill.fusionPartners.isNotEmpty()) {
                append("\n\nFUSION LINKS\n")
                append(skill.fusionPartners.mapNotNull { id -> MetamorphData.skills.firstOrNull { it.id == id }?.name }.joinToString(" • "))
            }
            append("\n\nCURRENT: ${store.stageFor(skill.id).label} • ${store.xpFor(skill.id)} XP")
        }
        AlertDialog.Builder(this)
            .setTitle("💎 ${skill.name}")
            .setMessage(message)
            .setPositiveButton("+25 XP") { _, _ ->
                val oldStage = store.stageFor(skill.id)
                val xp = store.addXp(skill.id, 25)
                val newStage = store.stageFor(skill.id)
                graph?.invalidate()
                if (newStage != oldStage) toast("${skill.name} → ${newStage.label}")
                else toast("${skill.name}: $xp XP")
            }
            .setNeutralButton("+100 PRESSURE TEST") { _, _ ->
                val oldStage = store.stageFor(skill.id)
                store.addXp(skill.id, 100)
                val newStage = store.stageFor(skill.id)
                graph?.invalidate()
                if (newStage != oldStage) toast("CRYSTAL EVOLVED → ${newStage.label}")
            }
            .setNegativeButton("CLOSE", null)
            .show()
    }

    private fun showServitors() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        MetamorphData.servitors.forEach { servitor ->
            val b = actionButton("${servitor.role.code} // ${servitor.role.title}", panel2, Color.WHITE) {
                showServitor(servitor)
            }
            box.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { bottomMargin = dp(6) })
        }
        AlertDialog.Builder(this)
            .setTitle("Servitor Forge")
            .setMessage("Prefilled constitutions remain editable in later Forge passes. Every default uses auto-reabsorption after goal completion.")
            .setView(box)
            .setPositiveButton("CLOSE", null)
            .show()
    }

    private fun showServitor(s: ServitorConstitution) {
        AlertDialog.Builder(this)
            .setTitle("${s.role.code} // ${s.role.title}")
            .setMessage(
                "PURPOSE\n${s.purpose}\n\nINNER DOMAIN\n${s.innerDirective}\n\nDEFAULT ENERGY SOURCE\n${s.defaultEnergySource}\n\nAUTHORIZED\n• ${s.authorizedActions.joinToString("\n• ")}\n\nBOUNDARIES\n• ${s.prohibitedActions.joinToString("\n• ")}\n\nRECALL: ${s.recallCommand}\nREABSORB: ${s.reabsorbCommand}\nAUTO-REABSORB: ${s.autoReabsorb}"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showCommands() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(10), dp(10), dp(10)) }
        MetamorphData.commands.forEach { (group, commands) ->
            box.addView(text(group, 16f, cyan, true).apply { setPadding(0, dp(8), 0, dp(3)) })
            box.addView(text(commands.joinToString(" • "), 13f, Color.WHITE))
        }
        val scroll = ScrollView(this).apply { addView(box) }
        AlertDialog.Builder(this).setTitle("All Servitor Commands").setView(scroll).setPositiveButton("CLOSE", null).show()
    }

    private fun showParadigms() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        MetamorphData.paradigms.forEach { p ->
            val active = p.active.joinToString("/") { it.code }
            val b = actionButton("${p.name}  [$active]", panel2, Color.WHITE) { activate(p.id) }
            box.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { bottomMargin = dp(6) })
        }
        val scroll = ScrollView(this).apply { addView(box) }
        AlertDialog.Builder(this).setTitle("Paradigm Deck").setView(scroll).setPositiveButton("CLOSE", null).show()
    }

    @Suppress("DEPRECATION")
    private fun showAutoSummon() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)) }
        val master = Switch(this).apply {
            text = "DAIMON AUTOPILOT"
            setTextColor(Color.WHITE)
            isChecked = store.autopilotEnabled()
            setOnCheckedChangeListener { _, checked -> store.setAutopilot(checked); refreshStatus() }
        }
        box.addView(master)
        MetamorphData.autoRules.forEach { rule ->
            val check = CheckBox(this).apply {
                text = "${rule.trigger}\n→ ${MetamorphData.paradigms.firstOrNull { it.id == rule.paradigmId }?.name ?: rule.paradigmId}  •  intensity ${rule.intensity}"
                setTextColor(soft)
                isChecked = store.ruleEnabled(rule)
                setOnCheckedChangeListener { _, checked -> store.setRuleEnabled(rule.id, checked) }
            }
            box.addView(check)
        }
        val scroll = ScrollView(this).apply { addView(box) }
        AlertDialog.Builder(this)
            .setTitle("Autosummon Conditions")
            .setMessage("Standing rules use quick actions now; later builds can attach shortcuts and sensors where the phone can actually detect a condition.")
            .setView(scroll)
            .setPositiveButton("SAVE", null)
            .show()
    }

    private fun showDreamGate() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        val mission = EditText(this).apply {
            setText(store.dreamMission())
            hint = "Dream mission"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            minLines = 2
        }
        val delay = EditText(this).apply {
            hint = "First cue delay in minutes"
            setText("360")
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val count = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("1 cue", "2 cues", "3 cues", "4 cues", "5 cues"))
            setSelection(2)
        }
        val volumeLabel = text("Cue strength: 18%", 13f, soft)
        val volume = SeekBar(this).apply {
            max = 60
            progress = 18
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { volumeLabel.text = "Cue strength: ${progress.coerceAtLeast(1)}%" }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        val haptic = CheckBox(this).apply { text = "Haptic cue"; setTextColor(soft); isChecked = true }
        box.addView(text("Condition a distinctive cue while awake, then replay it during a later sleep window. v0.2 generates 400 → 600 → 800 Hz tones with fades plus optional haptics.", 13f, soft))
        box.addView(mission)
        box.addView(delay)
        box.addView(count)
        box.addView(volumeLabel)
        box.addView(volume)
        box.addView(haptic)
        val test = actionButton("TEST DREAM SIGNAL NOW", cyan, Color.BLACK) {
            dreamGate.testCue(volume.progress.coerceAtLeast(1), haptic.isChecked)
        }
        box.addView(test, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(8) })

        AlertDialog.Builder(this)
            .setTitle("🌙 DREAMGATE")
            .setView(box)
            .setPositiveButton("ARM") { _, _ ->
                val cfg = DreamGateConfig(
                    cueDelayMinutes = delay.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 360,
                    cueCount = count.selectedItemPosition + 1,
                    volumePercent = volume.progress.coerceAtLeast(1),
                    vibrationEnabled = haptic.isChecked,
                    mission = mission.text.toString().ifBlank { "Become lucid, remember the mission, enter the Dream Dojo." }
                )
                val times = dreamGate.arm(cfg)
                toast("DreamGate armed for ${times.size} cue(s)")
            }
            .setNeutralButton("DISARM") { _, _ -> dreamGate.disarm(); toast("DreamGate disarmed") }
            .setNegativeButton("CLOSE", null)
            .show()
    }

    private fun showAstral() {
        val active = store.astralSessionActive()
        AlertDialog.Builder(this)
            .setTitle("✧ Astral Projection")
            .setMessage(
                if (active) "ASTRAL SESSION IS MARKED ACTIVE.\n\nThis state is intentionally separate from sleep, lucid dream, unknown state, and Phoenix condition."
                else "Begin Journey marks intentional Astral Projection as its own state. Phoenix logic never treats Astral Projection as death.\n\nSuggested formation: SEN guards the anchor, MED maintains restoration/stability, Dream Dojo remains available."
            )
            .setPositiveButton(if (active) "RETURNED" else "BEGIN JOURNEY") { _, _ ->
                store.setAstralSession(!active)
                if (!active) servitorEngine.activateParadigm("dream_dojo", "astral journey")
                refreshStatus()
            }
            .setNegativeButton("CLOSE", null)
            .show()
    }

    private fun showPhoenix() {
        AlertDialog.Builder(this)
            .setTitle("🔥 PHOENIX DOWN")
            .setMessage(
                "MED Ultimate within your spiritual system: a preconfigured resurrection contingency for the creator.\n\nFORMATION\nMED / SEN / SYN\nBackground: SAB / RAV / COM\n\nASTRAL EXCLUSION\nAstral Projection, lucid dreaming, meditation, trance, sleep, and invocation are explicitly separate states and do not count as Phoenix conditions.\n\nThe phone does not claim to biologically detect death. Phoenix activation conditions belong to the magician's covenant; the software stores and organizes that working."
            )
            .setPositiveButton("OPEN PHOENIX PARADIGM") { _, _ -> activate("phoenix") }
            .setNegativeButton("CLOSE", null)
            .show()
    }

    private fun showProtection() {
        AlertDialog.Builder(this)
            .setTitle("⚔ Protection Armory")
            .setMessage(
                "SWORD BANISHING FORMULA\nYOHACH • KALACH • NATZARIEL • OZIEL\n\nMETAMORPH stores this as a protection working alongside custom Chaos banishing, Sentinel wards, emergency recall, and Fortress paradigms.\n\nUNKNOWN CONTACT doctrine:\nOBSERVE → PROTECT → SEVER UNWANTED ACCESS → BANISH → RESTORE → RECORD"
            )
            .setPositiveButton("ACTIVATE FORTRESS") { _, _ -> activate("protect") }
            .setNeutralButton("EMERGENCY RECALL") { _, _ -> servitorEngine.emergencyRecall(); refreshStatus() }
            .setNegativeButton("CLOSE", null)
            .show()
    }

    private fun showLog() {
        val raw = store.eventLog().ifBlank { "No events recorded yet." }
        val tv = text(raw, 12f, Color.WHITE).apply { setPadding(dp(10), dp(10), dp(10), dp(10)) }
        val scroll = ScrollView(this).apply { addView(tv) }
        AlertDialog.Builder(this).setTitle("Operation Log").setView(scroll).setPositiveButton("CLOSE", null).show()
    }

    private fun sectionTitle(title: String): TextView = text(title, 14f, violet, true).apply {
        setPadding(dp(2), dp(18), dp(2), dp(7))
        letterSpacing = .08f
    }

    private fun buttonGrid(items: List<Pair<String, () -> Unit>>): LinearLayout {
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        items.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { index, item ->
                val b = actionButton(item.first, panel2, Color.WHITE) { item.second() }
                row.addView(b, LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                    if (index == 0) marginEnd = dp(4) else marginStart = dp(4)
                    bottomMargin = dp(8)
                })
            }
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, dp(54), 1f))
            outer.addView(row)
        }
        return outer
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun actionButton(label: String, bg: Int = panel2, fg: Int = Color.WHITE, action: () -> Unit): Button = Button(this).apply {
        text = label
        setTextColor(fg)
        textSize = 13f
        gravity = Gravity.CENTER
        isAllCaps = false
        background = rounded(bg, 15f, Color.rgb(67, 53, 88))
        setOnClickListener { action() }
    }

    private fun rounded(fill: Int, radius: Float, stroke: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius.toInt()).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
