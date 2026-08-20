package com.vhanma.kialink

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var store: MetamorphStore
    private lateinit var servitorEngine: ServitorEngine
    private lateinit var dreamGate: DreamGateEngine
    private lateinit var spiritStore: SpiritProfileStore
    private lateinit var statusText: TextView

    private val ink = Color.rgb(8, 6, 16)
    private val panel = Color.rgb(22, 17, 34)
    private val panel2 = Color.rgb(31, 24, 48)
    private val violet = Color.rgb(184, 115, 255)
    private val cyan = Color.rgb(103, 231, 255)
    private val gold = Color.rgb(255, 213, 103)
    private val soft = Color.rgb(207, 197, 222)

    private var selfUri: Uri? = null
    private var targetUri: Uri? = null
    private var selfBitmap: Bitmap? = null
    private var targetBitmap: Bitmap? = null
    private var currentPairSeal: PairSeal? = null
    private var forgeCanvas: SigilCanvas? = null
    private var forgeTargetName: EditText? = null
    private var forgeTraits: EditText? = null
    private var forgeIntent: EditText? = null
    private var forgeMode: Spinner? = null
    private var forgeResult: TextView? = null
    private var saveSigilButton: Button? = null
    private var pendingSpiritId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = MetamorphStore(this)
        servitorEngine = ServitorEngine(this, store)
        dreamGate = DreamGateEngine(this, store)
        spiritStore = SpiritProfileStore(this)
        window.statusBarColor = ink
        window.navigationBarColor = ink
        restorePairUris()
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
        root.addView(text("v0.3 • identity forge • crystal graph • spirit automation • dream / astral systems", 13f, soft).apply {
            setPadding(0, dp(4), 0, dp(12))
        })

        val forge = actionButton("◈ METAMORPH FORGE\nSELF  ↔  TARGET  →  EMERGENT", violet, Color.BLACK) { showMetamorphForge() }
        root.addView(forge, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)))

        statusText = text("", 14f, cyan, true).apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(panel, 18f, Color.rgb(58, 45, 77))
        }
        root.addView(statusText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10)
        })

        root.addView(sectionTitle("QUICK SUMMON"))
        root.addView(buttonGrid(listOf(
            "⚔ BATTLE" to { activate("battle") },
            "🧠 CLEAR MIND" to { activate("clear_mind") },
            "🔥 MOTIVATE" to { activate("motivate") },
            "✦ SOCIAL" to { activate("social") },
            "🛡 PROTECT" to { activate("protect") },
            "✚ RECOVER" to { activate("recover") }
        )))

        root.addView(actionButton("GOAL COMPLETE → AUTO-REABSORB + CRYSTAL XP", gold, Color.BLACK) {
            val touched = servitorEngine.completeCurrentGoal()
            if (touched.isEmpty()) toast("No active Paradigm")
            else {
                refreshStatus()
                AlertDialog.Builder(this)
                    .setTitle("Reabsorption complete")
                    .setMessage("Experience returned into overlapping crystals:\n\n${touched.joinToString("\n• ", prefix = "• ")}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(10) })

        root.addView(sectionTitle("FORGES + PROGRESSION"))
        root.addView(buttonGrid(listOf(
            "💎 CRYSTAL GRAPH" to { showCrystalGraph() },
            "🜏 SERVITOR / TULPA" to { showSpiritLibrary() },
            "◉ JUNG LAB" to { showJungLab() },
            "∞ HYPERSIGIL" to { showHypersigil() },
            "⌘ COMMANDS" to { showCommands() },
            "◈ PHYSICAL TREE" to { showPhysicalDoctrine() }
        )))

        root.addView(sectionTitle("AUTOMATION + OTHER DOMAINS"))
        root.addView(buttonGrid(listOf(
            "⚙ AUTOSUMMON" to { showAutoSummon() },
            "🧠 INNER DOMAIN" to { showInnerDomain() },
            "◈ PARADIGMS" to { showParadigms() },
            "🌙 DREAMGATE" to { showDreamGate() },
            "✧ ASTRAL / DOJO" to { showAstralDojo() },
            "⚔ PROTECTION" to { showProtection() },
            "🔥 PHOENIX DOWN" to { showPhoenix() },
            "☰ JOURNAL / LOG" to { showLog() }
        )))

        root.addView(actionButton("EMERGENCY RECALL ALL", Color.rgb(118, 42, 60), Color.WHITE) {
            servitorEngine.emergencyRecall()
            refreshStatus()
            toast("SUSPEND → RECALL → REABSORB")
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(12) })

        root.addView(text(
            "The photo forge, emergent shards, servitor images, custom constitutions, pair anchors and sigils are now part of the same persistent system. A trait forged from a photo working becomes a real Crystal node and can gain XP through relevant Paradigms.",
            12f, Color.rgb(157, 148, 174)
        ).apply { setPadding(dp(2), dp(14), dp(2), 0) })

        return scroll
    }

    private fun refreshStatus() {
        val p = servitorEngine.currentParadigm()
        val state = store.consciousState().label
        val auto = if (store.autopilotEnabled()) "ON" else "OFF"
        val anchor = store.pairTarget().ifBlank { "NONE" }
        statusText.text =
            "STATE: $state\nPARADIGM: ${p?.name ?: "DORMANT"}\nDAIMON AUTOPILOT: $auto\nPHOTO ANCHOR: $anchor"
    }

    private fun activate(id: String) {
        val result = servitorEngine.activateParadigm(id) ?: return
        refreshStatus()
        AlertDialog.Builder(this)
            .setTitle(result.paradigm.name)
            .setMessage(
                "ACTIVE: ${result.activeRoles}\nBACKGROUND: ${result.backgroundRoles}\n\nPURPOSE\n${result.paradigm.purpose}\n\nSUBCONSCIOUS / INNER DOMAIN SCRIPT\n${result.message}\n\nCompletion reabsorbs the working and returns XP into every connected Crystal node."
            )
            .setPositiveButton("ACTIVE", null)
            .setNeutralButton("COMPLETE") { _, _ ->
                servitorEngine.completeCurrentGoal()
                refreshStatus()
            }
            .show()
    }

    private fun showMetamorphForge() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(16))
        }
        val canvas = SigilCanvas(this).apply {
            selfBitmap = this@MainActivity.selfBitmap
            targetBitmap = this@MainActivity.targetBitmap
            targetName = store.pairTarget().ifBlank { "TARGET" }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300))
            background = rounded(panel, 18f, Color.rgb(60, 43, 78))
        }
        forgeCanvas = canvas
        content.addView(canvas)

        val photos = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, dp(4)) }
        photos.addView(actionButton("SELF PHOTO", panel2, Color.WHITE) { pickImage(REQ_SELF) },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(5) })
        photos.addView(actionButton("TARGET PHOTO", panel2, Color.WHITE) { pickImage(REQ_TARGET) },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(5) })
        content.addView(photos)

        content.addView(label("TARGET / ARCHETYPE / PERSON / DEITY / FUTURE SELF"))
        val target = field("Name of target or archetype").apply { setText(store.pairTarget()) }
        forgeTargetName = target
        content.addView(target)

        content.addView(label("TRAITS TO INTEGRATE"))
        val traits = multiline("Example: composure, perception, courage, creativity, tactical intelligence").apply {
            setText(store.pairTraits())
        }
        forgeTraits = traits
        content.addView(traits)

        content.addView(label("STATEMENT OF INTENT"))
        val intent = multiline("I integrate the chosen qualities into my own identity, behavior, dreams and skill network.")
        forgeIntent = intent
        content.addView(intent)

        content.addView(label("SIGIL METHOD"))
        val mode = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf(
                    "COMPOSITE • word + pictorial + mantra",
                    "SPARE • word reduction",
                    "PICTORIAL • deterministic pair seal",
                    "MANTRIC • reduced phonetic seal"
                )
            )
        }
        forgeMode = mode
        content.addView(mode)

        val result = text(
            if (store.pairSeal().isBlank()) "Choose both photos and forge the operation."
            else "CURRENT PAIR SEAL: ${store.pairSeal()}\nTarget: ${store.pairTarget()}\nTraits: ${store.pairTraits()}",
            13f, soft
        ).apply { setPadding(dp(4), dp(8), dp(4), dp(8)) }
        forgeResult = result
        content.addView(result)

        content.addView(actionButton("FORGE PHOTO SYNERGY + MASTER SIGIL", violet, Color.BLACK) { forgeCurrentPair() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(7), 0, 0) }
        val save = actionButton("SAVE SIGIL PNG", panel2, Color.WHITE) { saveCurrentSigil() }.apply {
            isEnabled = currentPairSeal != null || store.pairSeal().isNotBlank()
        }
        saveSigilButton = save
        row.addView(save, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) })
        row.addView(actionButton("CHARGE / INVOKE", panel2, Color.WHITE) { showInvocation() },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(4) })
        content.addView(row)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(7), 0, 0) }
        row2.addView(actionButton("ANCHOR TO SPIRIT", panel2, Color.WHITE) { anchorPairToSpirit() },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) })
        row2.addView(actionButton("JUNG INTEGRATION", panel2, Color.WHITE) { showJungLab() },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(4) })
        content.addView(row2)

        val scroll = ScrollView(this).apply { addView(content) }
        AlertDialog.Builder(this)
            .setTitle("◈ METAMORPH FORGE")
            .setView(scroll)
            .setPositiveButton("CLOSE", null)
            .show()
    }

    private fun forgeCurrentPair() {
        val su = selfUri
        val tu = targetUri
        if (su == null || tu == null) {
            toast("Assign SELF and TARGET photos first")
            return
        }
        val targetName = forgeTargetName?.text?.toString()?.trim().orEmpty().ifBlank { "Selected Target" }
        val traitText = forgeTraits?.text?.toString()?.trim().orEmpty()
        if (traitText.isBlank()) {
            toast("Add at least one trait")
            return
        }
        val intent = forgeIntent?.text?.toString().orEmpty()
        val mode = forgeMode?.selectedItem?.toString() ?: "COMPOSITE"
        runCatching {
            PairForge.forge(
                PairForge.hashUri(this, su),
                PairForge.hashUri(this, tu),
                targetName,
                traitText,
                intent,
                mode
            )
        }.onSuccess { seal ->
            currentPairSeal = seal
            store.setPairAnchor(seal.code, targetName, traitText, su.toString(), tu.toString())
            forgeCanvas?.apply {
                selfBitmap = this@MainActivity.selfBitmap
                targetBitmap = this@MainActivity.targetBitmap
                this.seal = seal.digest
                this.targetName = targetName
                traits = seal.traits
                invalidate()
            }

            val traitNodes = seal.traits.map { trait ->
                store.addCustomSkill(
                    name = trait.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() },
                    description = "Emergent trait shard forged through the SELF ↔ $targetName photo-link operation.",
                    domains = setOf("MIND", "SPIRIT", "IDENTITY", "SYN", "MAGICK"),
                    sources = setOf("PHOTO SYNERGY", targetName),
                    tags = setOf("EMERGENT", "IDENTITY", "PAIR:${seal.code}"),
                    initialXp = 25
                )
            }
            store.addCustomSkill(
                name = "$targetName Integration",
                description = "Master identity crystal binding the selected target qualities into one evolving integration node.",
                domains = setOf("MIND", "BODY", "SPIRIT", "IDENTITY", "MAGICK", "SYN"),
                sources = setOf("METAMORPH FORGE", targetName),
                fusionPartners = traitNodes.map { it.id }.toSet(),
                tags = setOf("EMERGENT", "IDENTITY", "MASTER", "PAIR:${seal.code}"),
                initialXp = 50
            )
            store.appendLog("${stamp()} | PHOTO SYNERGY FORGED | $targetName | ${seal.code} | ${seal.traits.joinToString()}")
            forgeResult?.text =
                "PAIR SEAL: ${seal.code}\nREDUCED INTENT: ${seal.reducedIntent}\nMANTRIC SEAL: ${seal.mantra}\nEMERGENT SHARDS: ${seal.traits.joinToString(" • ")}"
            saveSigilButton?.isEnabled = true
            refreshStatus()
            toast("${seal.traits.size} emergent Crystal Shards created")
        }.onFailure { toast("Forge error: ${it.message}") }
    }

    private fun showInvocation() {
        val pairCode = currentPairSeal?.code ?: store.pairSeal()
        if (pairCode.isBlank()) {
            toast("Forge a photo pair first")
            return
        }
        val methods = arrayOf(
            "Hybrid • stillness → fixation → embodiment → peak → release",
            "Inhibitory • silence + fixation",
            "Excitatory • movement + emotion",
            "Dream Gate • pre-sleep identity scene",
            "Godform • total archetype assumption"
        )
        var selected = 0
        AlertDialog.Builder(this)
            .setTitle("Charge / Invocation")
            .setSingleChoiceItems(methods, 0) { _, which -> selected = which }
            .setMessage(
                "Pair seal: $pairCode\n\nUse the selected target qualities as the operation. Posture, imagery, sound/mantra, emotion, symbol and first-person identity can all point toward the same state."
            )
            .setPositiveButton("BEGIN") { _, _ ->
                store.setConsciousState(ConsciousState.INVOCATION)
                store.appendLog("${stamp()} | INVOCATION BEGIN | ${methods[selected]} | pair=$pairCode")
                AlertDialog.Builder(this)
                    .setTitle("Invocation active")
                    .setMessage("PAIR: $pairCode\n\nTARGET: ${store.pairTarget()}\nTRAITS: ${store.pairTraits()}\n\nWhen complete, seal the useful state into the Crystal network.")
                    .setPositiveButton("COMPLETE + INTEGRATE") { _, _ ->
                        val affected = store.customSkills().filter { it.tags.contains("PAIR:$pairCode") }
                        affected.forEach { store.addXp(it.id, 25) }
                        store.setConsciousState(ConsciousState.AWAKE)
                        store.appendLog("${stamp()} | INVOCATION COMPLETE | pair=$pairCode | +25 XP to ${affected.size} nodes")
                        toast("Invocation integrated into ${affected.size} crystals")
                    }
                    .setNegativeButton("END", null)
                    .show()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun anchorPairToSpirit() {
        val seal = currentPairSeal?.code ?: store.pairSeal()
        if (seal.isBlank()) {
            toast("Forge a pair seal first")
            return
        }
        val profiles = spiritStore.loadAll()
        if (profiles.isEmpty()) return
        val names = profiles.map { "${it.name}  [${it.roleCode}]" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Use pair seal as spirit anchor")
            .setItems(names) { _, which ->
                val p = profiles[which]
                spiritStore.save(p.copy(anchorSeal = seal))
                store.appendLog("${stamp()} | PAIR ANCHOR → ${p.name} | $seal")
                toast("${p.name} anchored to current pair seal")
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun saveCurrentSigil() {
        val canvas = forgeCanvas
        val seal = currentPairSeal
        if (canvas == null || (seal == null && store.pairSeal().isBlank())) {
            toast("Open the forge and generate a sigil first")
            return
        }
        runCatching {
            val bitmap = canvas.renderBitmap()
            val name = "KIA-Metamorph-${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KIA-Metamorph")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Unable to create image")
                contentResolver.openOutputStream(uri).use { out ->
                    requireNotNull(out)
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
                uri
            } else {
                val dir = File(getExternalFilesDir(null), "sigils").apply { mkdirs() }
                val file = File(dir, name)
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                Uri.fromFile(file)
            }
        }.onSuccess { toast("Sigil saved: $it") }
            .onFailure { toast("Save failed: ${it.message}") }
    }

    private fun showSpiritLibrary() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(10))
        }
        val profiles = spiritStore.loadAll()
        profiles.forEach { p ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(panel2, 14f, Color.rgb(62, 49, 78))
                setPadding(dp(8), dp(6), dp(8), dp(6))
            }
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.rgb(45, 35, 58))
                if (p.imageUri.isNotBlank()) runCatching { setImageURI(Uri.parse(p.imageUri)) }
            }
            row.addView(image, LinearLayout.LayoutParams(dp(62), dp(62)).apply { marginEnd = dp(8) })
            row.addView(actionButton("${p.name}\n${p.type} • ${p.roleCode}", panel2, Color.WHITE) { showSpiritEditor(p) },
                LinearLayout.LayoutParams(0, dp(62), 1f))
            box.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76)).apply { bottomMargin = dp(6) })
        }
        box.addView(actionButton("+ CREATE CUSTOM SERVITOR", violet, Color.BLACK) {
            val p = spiritStore.newProfile("SERVITOR")
            spiritStore.save(p)
            showSpiritEditor(p)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(6) })
        box.addView(actionButton("+ CREATE TULPA", cyan, Color.BLACK) {
            val p = spiritStore.newProfile("TULPA")
            spiritStore.save(p)
            showSpiritEditor(p)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(6) })

        val scroll = ScrollView(this).apply { addView(box) }
        AlertDialog.Builder(this)
            .setTitle("🜏 Servitor / Tulpa Forge")
            .setMessage("Every entity can have its own image, sigil, constitution, energy source, commands, autosummon rule and photo-pair anchor.")
            .setView(scroll)
            .setPositiveButton("CLOSE", null)
            .show()
    }

    private fun showSpiritEditor(original: SpiritProfile) {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(12)) }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(45, 35, 58))
            if (original.imageUri.isNotBlank()) runCatching { setImageURI(Uri.parse(original.imageUri)) }
        }
        content.addView(image, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190)))
        content.addView(actionButton("ASSIGN / CHANGE IMAGE", panel2, Color.WHITE) {
            pendingSpiritId = original.id
            pickImage(REQ_SPIRIT)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(6) })

        content.addView(label("NAME"))
        val name = field("Entity name").apply { setText(original.name) }
        content.addView(name)

        content.addView(label("TYPE"))
        val type = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("SERVITOR", "TULPA", "EGREGORE", "CONTROLLER", "THOUGHTFORM"))
            setSpinnerSelection(this, original.type)
        }
        content.addView(type)

        content.addView(label("ROLE"))
        val role = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("COM", "RAV", "SEN", "SAB", "SYN", "MED", "CUSTOM"))
            setSpinnerSelection(this, original.roleCode)
        }
        content.addView(role)

        content.addView(label("PURPOSE"))
        val purpose = multiline("One clear purpose").apply { setText(original.purpose) }
        content.addView(purpose)

        content.addView(label("INNER / SUBCONSCIOUS DIRECTIVE"))
        val inner = multiline("Trigger → state → action → completion").apply { setText(original.innerDirective) }
        content.addView(inner)

        content.addView(label("SOURCE OF ENERGY / FEEDING"))
        val energy = multiline("Prefilled and editable").apply { setText(original.energySource) }
        content.addView(energy)

        content.addView(label("AUTHORIZED ACTIONS"))
        val auth = multiline("Allowed actions").apply { setText(original.authorizedActions) }
        content.addView(auth)

        content.addView(label("BOUNDARIES / PROHIBITED ACTIONS"))
        val prohib = multiline("Core covenant").apply { setText(original.prohibitedActions) }
        content.addView(prohib)

        content.addView(label("AUTOSUMMON CONDITION"))
        val auto = multiline("When should this entity automatically activate?").apply { setText(original.autoSummon) }
        content.addView(auto)

        content.addView(label("GOAL / COMPLETION CONDITION"))
        val completion = multiline("When is the task considered complete?").apply { setText(original.completionRule) }
        content.addView(completion)

        val reabsorb = CheckBox(this).apply {
            text = "AUTO-REABSORB WHEN GOAL COMPLETES"
            setTextColor(Color.WHITE)
            isChecked = original.autoReabsorb
        }
        content.addView(reabsorb)

        content.addView(label("COMMANDS"))
        val commands = multiline("SUMMON, ACTIVATE, REPORT, RECALL...").apply { setText(original.commands) }
        content.addView(commands)

        content.addView(label("PHOTO / MASTER ANCHOR SEAL"))
        val anchor = field("Pair seal or other anchor").apply { setText(original.anchorSeal) }
        content.addView(anchor)
        content.addView(actionButton("USE CURRENT METAMORPH PAIR SEAL", panel2, Color.WHITE) {
            val pair = store.pairSeal()
            if (pair.isBlank()) toast("No pair seal forged") else anchor.setText(pair)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))

        content.addView(label("NOTES / WONDERLAND / RELATIONSHIP"))
        val notes = multiline("For tulpas: identity, relationship, wonderland, dialogue and development notes.").apply { setText(original.notes) }
        content.addView(notes)

        val scroll = ScrollView(this).apply { addView(content) }
        val dialog = AlertDialog.Builder(this)
            .setTitle(original.name)
            .setView(scroll)
            .setPositiveButton("SAVE", null)
            .setNeutralButton("SIGIL", null)
            .setNegativeButton("CLOSE", null)
            .create()

        fun collect(): SpiritProfile = original.copy(
            name = name.text.toString().trim().ifBlank { "Unnamed" },
            type = type.selectedItem.toString(),
            roleCode = role.selectedItem.toString(),
            imageUri = spiritStore.find(original.id)?.imageUri ?: original.imageUri,
            purpose = purpose.text.toString(),
            innerDirective = inner.text.toString(),
            energySource = energy.text.toString(),
            authorizedActions = auth.text.toString(),
            prohibitedActions = prohib.text.toString(),
            autoSummon = auto.text.toString(),
            completionRule = completion.text.toString(),
            autoReabsorb = reabsorb.isChecked,
            commands = commands.text.toString(),
            anchorSeal = anchor.text.toString().trim(),
            notes = notes.text.toString()
        )

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val p = collect()
                spiritStore.save(p)
                store.appendLog("${stamp()} | SPIRIT PROFILE SAVED | ${p.name} | ${p.type}/${p.roleCode}")
                toast("${p.name} saved")
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val p = collect()
                spiritStore.save(p)
                showSpiritSigil(p)
            }
        }
        dialog.show()
    }

    private fun showSpiritSigil(profile: SpiritProfile) {
        val view = SigilGlyphView(this).apply {
            seal = PairForge.digestText(profile.sigilSeed())
            title = profile.name
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(360))
        }
        AlertDialog.Builder(this)
            .setTitle("${profile.name} // SIGIL")
            .setMessage("Deterministically generated from this entity's constitution, commands and anchor. Editing the constitution changes the sigil.")
            .setView(view)
            .setPositiveButton("CLOSE", null)
            .show()
    }

    private fun showCrystalGraph() {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val detail = text("Tap a crystal. Cyan links = shared domains; violet links = explicit fusion; gold root links = emergent identity nodes.", 13f, soft)
        val graph = CrystalGraphView(this, store).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(500))
            onNodeSelected = { node ->
                detail.text = "${node.name} • ${store.stageFor(node.id).label} • ${store.xpFor(node.id)} XP\n${node.description}\n\nOVERLAPS: ${node.domains.joinToString()}\nSOURCES: ${node.sources.joinToString()}"
            }
        }
        wrap.addView(graph)
        wrap.addView(detail.apply { setPadding(dp(6), dp(8), dp(6), dp(8)) })
        wrap.addView(actionButton("OPEN ALL SKILL SHARDS", violet, Color.BLACK) { showSkillList(graph) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        AlertDialog.Builder(this)
            .setTitle("Universal Crystal Graph • ${MetamorphData.skills.size + store.customSkills().size} nodes")
            .setView(wrap)
            .setPositiveButton("CLOSE", null)
            .show()
    }

    private fun showSkillList(graph: CrystalGraphView? = null) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        val all = MetamorphData.skills + store.customSkills()
        all.forEach { skill ->
            val prefix = if (skill.tags.contains("EMERGENT")) "✦ " else "💎 "
            box.addView(actionButton("$prefix${skill.name}\n${store.stageFor(skill.id).label} • ${store.xpFor(skill.id)} XP", panel2, Color.WHITE) {
                showSkill(skill, graph)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)).apply { bottomMargin = dp(5) })
        }
        val scroll = ScrollView(this).apply { addView(box) }
        AlertDialog.Builder(this).setTitle("Skill Shards").setView(scroll).setPositiveButton("CLOSE", null).show()
    }

    private fun showSkill(skill: SkillNode, graph: CrystalGraphView?) {
        AlertDialog.Builder(this)
            .setTitle("💎 ${skill.name}")
            .setMessage(
                "${skill.description}\n\nOVERLAPS\n${skill.domains.joinToString(" • ")}\n\nSOURCES\n${skill.sources.joinToString(" • ")}\n\nCURRENT\n${store.stageFor(skill.id).label} • ${store.xpFor(skill.id)} XP"
            )
            .setPositiveButton("+25 TRAIN") { _, _ ->
                val old = store.stageFor(skill.id)
                val xp = store.addXp(skill.id, 25)
                val next = store.stageFor(skill.id)
                graph?.invalidate()
                if (old != next) toast("CRYSTAL EVOLVED → ${next.label}") else toast("$xp XP")
            }
            .setNeutralButton("+100 PRESSURE TEST") { _, _ ->
                store.addXp(skill.id, 100)
                graph?.invalidate()
                toast("${store.stageFor(skill.id).label} • ${store.xpFor(skill.id)} XP")
            }
            .setNegativeButton("CLOSE", null)
            .show()
    }

    private fun showJungLab() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(10)) }
        box.addView(text("Projection Retrieval → Shadow/Opposite → Coniunctio → Emergent Third Form", 14f, cyan, true))
        val projection = multiline("What fascinates, intimidates, attracts, angers or awes you about the target?")
        val hidden = multiline("What quality in that reaction may also exist, underdeveloped or disowned, in you?")
        val opposite = multiline("What is the opposite/counter-quality that keeps this trait balanced?")
        val third = multiline("Describe the integrated third form that can contain both sides.")
        listOf(
            "PROJECTION" to projection,
            "RETRIEVED QUALITY" to hidden,
            "SHADOW / OPPOSITE" to opposite,
            "CONIUNCTIO / THIRD FORM" to third
        ).forEach { (name, field) -> box.addView(label(name)); box.addView(field) }
        box.addView(actionButton("CRYSTALLIZE JUNG WORK", violet, Color.BLACK) {
            val entries = listOf(
                "Projection Retrieval" to projection.text.toString(),
                "Shadow Integration" to hidden.text.toString(),
                "Counter-Opposite" to opposite.text.toString(),
                "Coniunctio Third Form" to third.text.toString()
            ).filter { it.second.isNotBlank() }
            entries.forEach { (name, value) ->
                store.addCustomSkill(
                    name = name,
                    description = value,
                    domains = setOf("MIND", "SPIRIT", "JUNG", "IDENTITY", "MAGICK"),
                    sources = setOf("JUNG", "ACTIVE IMAGINATION", "METAMORPH"),
                    tags = setOf("EMERGENT", "IDENTITY", "JUNG"),
                    initialXp = 25
                )
            }
            store.appendLog("${stamp()} | JUNG LAB | ${entries.joinToString { it.first }}")
            toast("${entries.size} Jungian shards crystallized")
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(8) })
        val scroll = ScrollView(this).apply { addView(box) }
        AlertDialog.Builder(this).setTitle("◉ Jungian Integration Lab").setView(scroll).setPositiveButton("CLOSE", null).show()
    }

    private fun showHypersigil() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(10)) }
        val name = field("Hypersigil / identity arc name").apply { setText(store.ritualValue("hyper_name")) }
        val attractor = multiline("Qualities / strange attractor").apply { setText(store.ritualValue("hyper_attractor")) }
        val chapter = multiline("Current chapter, symbol, dream, conflict or transformation").apply { setText(store.ritualValue("hyper_chapter")) }
        val action = multiline("One real-world action that advances the story").apply { setText(store.ritualValue("hyper_action")) }
        listOf("NAME" to name, "IDENTITY ATTRACTOR" to attractor, "CURRENT CHAPTER" to chapter, "EMBODIMENT ACTION" to action).forEach {
            box.addView(label(it.first)); box.addView(it.second)
        }
        box.addView(actionButton("SEAL CHAPTER + EVOLVE HYPERSIGIL", violet, Color.BLACK) {
            store.putRitualValue("hyper_name", name.text.toString())
            store.putRitualValue("hyper_attractor", attractor.text.toString())
            store.putRitualValue("hyper_chapter", chapter.text.toString())
            store.putRitualValue("hyper_action", action.text.toString())
            val title = name.text.toString().ifBlank { "Living Hypersigil" }
            val node = store.addCustomSkill(
                title,
                "Living narrative identity arc: ${attractor.text}",
                setOf("MIND", "SPIRIT", "IDENTITY", "MAGICK", "SYN"),
                setOf("HYPERSIGIL", "CHAOS MAGICK"),
                tags = setOf("EMERGENT", "IDENTITY", "HYPERSIGIL"),
                initialXp = 20
            )
            store.addXp(node.id, 15)
            store.appendLog("${stamp()} | HYPERSIGIL CHAPTER | $title | ${chapter.text} | action=${action.text}")
            showSpiritSigil(
                SpiritProfile(
                    "hyper_${node.id}", title, "HYPERSIGIL", "CUSTOM",
                    purpose = attractor.text.toString(), innerDirective = chapter.text.toString(),
                    energySource = "Narrative attention + completed embodiment actions",
                    authorizedActions = action.text.toString(), prohibitedActions = "Do not erase conscious authority.",
                    autoSummon = "When the current chapter is encountered.", completionRule = "Identity arc consciously integrated.",
                    autoReabsorb = false, commands = "EVOLVE, RECORD, INTEGRATE", anchorSeal = store.pairSeal()
                )
            )
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(8) })
        val scroll = ScrollView(this).apply { addView(box) }
        AlertDialog.Builder(this).setTitle("∞ Living Hypersigil").setView(scroll).setPositiveButton("CLOSE", null).show()
    }

    private fun showInnerDomain() {
        val global = multiline("Global subconscious directive").apply {
            setText(store.ritualValue("inner_global", "Notice the actual situation, bring the useful Crystal Shards forward, adapt the Paradigm, complete the objective, integrate the lesson, then release."))
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(10)) }
        box.addView(text("INNER DOMAIN is the standing subconscious-development layer under the role system.", 13f, soft))
        box.addView(label("GLOBAL DIRECTIVE"))
        box.addView(global)
        MetamorphData.servitors.forEach {
            box.addView(text("${it.role.code} • ${it.innerDirective}", 13f, Color.WHITE, true).apply { setPadding(0, dp(8), 0, 0) })
        }
        box.addView(actionButton("SAVE INNER DOMAIN", violet, Color.BLACK) {
            store.putRitualValue("inner_global", global.text.toString())
            store.appendLog("${stamp()} | INNER DOMAIN DIRECTIVE UPDATED")
            toast("Inner Domain saved")
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(10) })
        val scroll = ScrollView(this).apply { addView(box) }
        AlertDialog.Builder(this).setTitle("🧠 Inner Domain").setView(scroll).setPositiveButton("CLOSE", null).show()
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
            box.addView(CheckBox(this).apply {
                text = "${rule.trigger}\n→ ${MetamorphData.paradigms.firstOrNull { it.id == rule.paradigmId }?.name ?: rule.paradigmId} • intensity ${rule.intensity}"
                setTextColor(soft)
                isChecked = store.ruleEnabled(rule)
                setOnCheckedChangeListener { _, checked -> store.setRuleEnabled(rule.id, checked) }
            })
        }
        box.addView(text("\nCustom servitors also carry editable autosummon conditions in their own constitutions.", 12f, cyan))
        val scroll = ScrollView(this).apply { addView(box) }
        AlertDialog.Builder(this).setTitle("Autosummon Conditions").setView(scroll).setPositiveButton("SAVE", null).show()
    }

    private fun showParadigms() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        MetamorphData.paradigms.forEach { p ->
            val active = servitorEngine.formatFormation(p.active)
            val bg = servitorEngine.formatFormation(p.background).ifBlank { "none" }
            box.addView(actionButton("${p.name}\nACTIVE $active • BG $bg", panel2, Color.WHITE) { activate(p.id) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)).apply { bottomMargin = dp(6) })
        }
        val scroll = ScrollView(this).apply { addView(box) }
        AlertDialog.Builder(this).setTitle("Paradigm Deck").setView(scroll).setPositiveButton("CLOSE", null).show()
    }

    private fun showCommands() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(10), dp(10), dp(10)) }
        MetamorphData.commands.forEach { (group, commands) ->
            box.addView(text(group, 16f, cyan, true).apply { setPadding(0, dp(8), 0, dp(3)) })
            box.addView(text(commands.joinToString(" • "), 13f, Color.WHITE))
        }
        box.addView(text("\nCUSTOM ENTITY COMMANDS", 16f, gold, true).apply { setPadding(0, dp(12), 0, dp(4)) })
        spiritStore.loadAll().forEach { p -> box.addView(text("${p.name}: ${p.commands}", 12f, soft).apply { setPadding(0, dp(4), 0, dp(4)) }) }
        val scroll = ScrollView(this).apply { addView(box) }
        AlertDialog.Builder(this).setTitle("All Servitor Commands").setView(scroll).setPositiveButton("CLOSE", null).show()
    }

    private fun showDreamGate() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(10)) }
        val mission = multiline("Dream mission").apply { setText(store.dreamMission()) }
        val delay = field("First cue delay in minutes").apply {
            setText("360")
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val count = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("1", "2", "3", "4", "5"))
            setSelection(2)
        }
        val strengthText = text("Cue strength: 18%", 13f, soft)
        val strength = SeekBar(this).apply {
            max = 60
            progress = 18
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    strengthText.text = "Cue strength: ${progress.coerceAtLeast(1)}%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        val vibrate = CheckBox(this).apply { text = "HAPTIC CUE"; setTextColor(Color.WHITE); isChecked = true }
        box.addView(label("MISSION")); box.addView(mission)
        box.addView(label("FIRST CUE DELAY")); box.addView(delay)
        box.addView(label("CUE COUNT")); box.addView(count)
        box.addView(strengthText); box.addView(strength); box.addView(vibrate)
        box.addView(text("Conditioned signal: ascending 400 → 600 → 800 Hz cue with fades. The same signal is used for awake conditioning and later sleep-window cueing.", 12f, cyan))
        box.addView(actionButton("TEST DREAMGATE CUE", panel2, Color.WHITE) { dreamGate.testCue(strength.progress.coerceAtLeast(1), vibrate.isChecked) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(6) })
        box.addView(actionButton("ARM DREAMGATE", violet, Color.BLACK) {
            val cfg = DreamGateConfig(
                cueDelayMinutes = delay.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 360,
                cueCount = count.selectedItem.toString().toInt(),
                spacingMinutes = 30,
                volumePercent = strength.progress.coerceAtLeast(1),
                vibrationEnabled = vibrate.isChecked,
                mission = mission.text.toString().ifBlank { "Become lucid and remember the mission." }
            )
            val times = dreamGate.arm(cfg)
            toast("DreamGate armed: ${times.size} cues")
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(6) })
        box.addView(actionButton("DISARM", Color.rgb(95, 46, 65), Color.WHITE) { dreamGate.disarm(); toast("DreamGate disarmed") },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(6) })

        val scroll = ScrollView(this).apply { addView(box) }
        AlertDialog.Builder(this).setTitle("🌙 DreamGate Lucid Inducer").setView(scroll).setPositiveButton("CLOSE", null).show()
    }

    private fun showAstralDojo() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(10)) }
        box.addView(text(
            "Astral Projection is an intentional state in METAMORPH. Sleep, lucid dreams, meditation, trance and astral projection are explicitly excluded from Phoenix activation.",
            13f, soft
        ))
        val mission = multiline("Astral / Dream Dojo mission").apply {
            setText(store.ritualValue("dojo_mission", "Enter the persistent dojo. Summon the selected team. Train one Skill Shard. Return with a clear lesson."))
        }
        box.addView(label("DOJO MISSION")); box.addView(mission)
        box.addView(actionButton("BEGIN ASTRAL JOURNEY", violet, Color.BLACK) {
            store.setAstralSession(true)
            store.putRitualValue("dojo_mission", mission.text.toString())
            store.setDreamMission(mission.text.toString())
            store.appendLog("${stamp()} | ASTRAL JOURNEY BEGIN | ${mission.text}")
            refreshStatus()
            toast("Astral state active • Phoenix remains separate")
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(8) })
        box.addView(actionButton("RETURNED / CLOSE JOURNEY", panel2, Color.WHITE) {
            store.setAstralSession(false)
            store.appendLog("${stamp()} | ASTRAL JOURNEY RETURNED")
            refreshStatus()
            toast("Astral session closed")
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(6) })
        box.addView(actionButton("OPEN DREAMGATE FOR DOJO", panel2, Color.WHITE) {
            store.setDreamMission(mission.text.toString())
            showDreamGate()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(6) })
        AlertDialog.Builder(this).setTitle("✧ Astral / Dream Dojo").setView(box).setPositiveButton("CLOSE", null).show()
    }

    private fun showProtection() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(10)) }
        box.addView(text("PROTECTION // ARMORY", 18f, cyan, true))
        box.addView(text("Sword Banishing formula names:\nYOHACH • KALACH • NATZARIEL • OZIEL", 15f, Color.WHITE, true).apply { setPadding(0, dp(8), 0, dp(10)) })
        val methods = listOf(
            "⚔ SWORD BANISHING" to "Sword Banishing protection working using Yohach, Kalach, Natzariel and Oziel. Keep your own book/source wording and visualization details.",
            "✦ LBRP / CEREMONIAL" to "Pentagram / elemental-space banishing and centering slot.",
            "🜏 CHAOS WARD" to "Custom symbol + word + gesture + boundary + servitor formation.",
            "◉ UNKNOWN CONTACT" to "Observe → Protect → Sever unwanted access → Banish → Restore → Record.",
            "⌂ HOUSEHOLD WARD" to "Protection anchor for self, loved ones, home, pets or custom protected points."
        )
        methods.forEach { (name, detail) ->
            box.addView(actionButton(name, panel2, Color.WHITE) {
                store.appendLog("${stamp()} | PROTECTION START | $name")
                AlertDialog.Builder(this).setTitle(name).setMessage(detail).setPositiveButton("ACTIVATE") { _, _ ->
                    servitorEngine.activateParadigm("protect", "protection:$name")
                    refreshStatus()
                }.setNegativeButton("CLOSE", null).show()
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { bottomMargin = dp(6) })
        }
        val scroll = ScrollView(this).apply { addView(box) }
        AlertDialog.Builder(this).setTitle("⚔ Protection Armory").setView(scroll).setPositiveButton("CLOSE", null).show()
    }

    private fun showPhoenix() {
        val armed = store.ritualValue("phoenix_armed", "false") == "true"
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(10)) }
        val status = text(if (armed) "PHOENIX DOWN: ARMED" else "PHOENIX DOWN: DORMANT", 18f, if (armed) gold else soft, true)
        box.addView(status)
        box.addView(text(
            "Intended magical operation: self-resurrection contingency. It is NOT triggered by sleep, meditation, trance, lucid dreaming, astral projection, invocation, phone inactivity or loss of motion.\n\nFORMATION: MED • SEN • SYN • SAB • RAV • COM\nPriority: restoration / return / protection / removal of obstruction / pathway search / completion.",
            13f, Color.WHITE
        ).apply { setPadding(0, dp(8), 0, dp(8)) })
        val anchor = multiline("Phoenix anchor / return command / identity coordinates").apply {
            setText(store.ritualValue("phoenix_anchor", "Golden Spot + full identity + personal sigil + chosen return command."))
        }
        box.addView(label("ANCHOR VAULT INSTRUCTION")); box.addView(anchor)
        box.addView(actionButton("ARM PHOENIX DOWN", Color.rgb(190, 83, 45), Color.WHITE) {
            store.putRitualValue("phoenix_armed", "true")
            store.putRitualValue("phoenix_anchor", anchor.text.toString())
            store.appendLog("${stamp()} | PHOENIX DOWN ARMED")
            toast("Phoenix Down armed")
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(6) })
        box.addView(actionButton("DISARM PHOENIX", panel2, Color.WHITE) {
            store.putRitualValue("phoenix_armed", "false")
            store.appendLog("${stamp()} | PHOENIX DOWN DISARMED")
            toast("Phoenix Down disarmed")
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(6) })
        AlertDialog.Builder(this).setTitle("🔥 MED ULTIMATE // PHOENIX DOWN").setView(box).setPositiveButton("CLOSE", null).show()
    }

    private fun showPhysicalDoctrine() {
        val sourceNames = setOf("ANATOMY TRAINS", "PUNCH DOCTOR", "ALEXANDER", "BAKI", "KENGAN")
        val physical = (MetamorphData.skills + store.customSkills()).filter { s ->
            s.domains.any { it in setOf("BODY", "COMBAT", "BOXING", "MUAY THAI", "WRESTLING", "GRAPPLING", "MOVEMENT") } ||
                s.sources.any { it in sourceNames }
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        box.addView(text("Physical doctrine sources: Anatomy Trains • Punch Doctor • Delsarte/Alexander • Baki • Kengan. Shared mechanics remain one overlapping Crystal instead of duplicates.", 13f, cyan).apply { setPadding(0, 0, 0, dp(8)) })
        physical.forEach { s ->
            box.addView(actionButton("${s.name}\n${s.sources.joinToString(" • ")}", panel2, Color.WHITE) {
                showSkill(s, null)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)).apply { bottomMargin = dp(5) })
        }
        val scroll = ScrollView(this).apply { addView(box) }
        AlertDialog.Builder(this).setTitle("◈ Physical Crystal Tree").setView(scroll).setPositiveButton("CLOSE", null).show()
    }

    private fun showLog() {
        val content = store.eventLog().ifBlank { "No operations logged yet." }
        val tv = text(content, 12f, Color.WHITE).apply { setPadding(dp(10), dp(10), dp(10), dp(10)) }
        val scroll = ScrollView(this).apply { addView(tv) }
        AlertDialog.Builder(this).setTitle("Journal / Operation Log").setView(scroll).setPositiveButton("CLOSE", null).show()
    }

    private fun pickImage(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, requestCode)
    }

    @Deprecated("Activity result retained to keep build dependency-free")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        when (requestCode) {
            REQ_SELF -> {
                selfUri = uri
                selfBitmap = loadBitmap(uri)
                forgeCanvas?.selfBitmap = selfBitmap
                forgeCanvas?.invalidate()
                toast("Self photo assigned")
            }
            REQ_TARGET -> {
                targetUri = uri
                targetBitmap = loadBitmap(uri)
                forgeCanvas?.targetBitmap = targetBitmap
                forgeCanvas?.invalidate()
                toast("Target photo assigned")
            }
            REQ_SPIRIT -> {
                val id = pendingSpiritId
                if (id != null) {
                    spiritStore.updateImage(id, uri.toString())
                    store.appendLog("${stamp()} | SPIRIT IMAGE ASSIGNED | ${spiritStore.find(id)?.name ?: id}")
                    toast("Entity image assigned")
                }
                pendingSpiritId = null
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun loadBitmap(uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            MediaStore.Images.Media.getBitmap(contentResolver, uri)
        }
    }.getOrNull()

    private fun restorePairUris() {
        store.pairSelfUri().takeIf { it.isNotBlank() }?.let {
            selfUri = runCatching { Uri.parse(it) }.getOrNull()
            selfUri?.let { u -> selfBitmap = loadBitmap(u) }
        }
        store.pairTargetUri().takeIf { it.isNotBlank() }?.let {
            targetUri = runCatching { Uri.parse(it) }.getOrNull()
            targetUri?.let { u -> targetBitmap = loadBitmap(u) }
        }
    }

    private fun buttonGrid(items: List<Pair<String, () -> Unit>>): LinearLayout {
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        items.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { index, item ->
                row.addView(
                    actionButton(item.first, panel2, Color.WHITE, item.second),
                    LinearLayout.LayoutParams(0, dp(56), 1f).apply {
                        if (index == 0) marginEnd = dp(4) else marginStart = dp(4)
                        bottomMargin = dp(7)
                    }
                )
            }
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            outer.addView(row)
        }
        return outer
    }

    private fun sectionTitle(value: String): TextView = text(value, 14f, gold, true).apply {
        letterSpacing = .08f
        setPadding(dp(2), dp(18), 0, dp(8))
    }

    private fun label(value: String): TextView = text(value, 12f, cyan, true).apply {
        setPadding(dp(2), dp(10), 0, dp(4))
    }

    private fun field(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        setTextColor(Color.WHITE)
        setHintTextColor(Color.GRAY)
        background = rounded(panel2, 12f, Color.rgb(67, 52, 83))
        setPadding(dp(12), dp(10), dp(12), dp(10))
        singleLine = true
    }

    private fun multiline(hintText: String): EditText = field(hintText).apply {
        singleLine = false
        minLines = 2
        maxLines = 6
        gravity = Gravity.TOP
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun actionButton(label: String, bg: Int, fg: Int, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setTextColor(fg)
            background = rounded(bg, 14f, bg)
            setPadding(dp(6), dp(3), dp(6), dp(3))
            setOnClickListener { action() }
        }

    private fun rounded(fill: Int, radiusDp: Float, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun setSpinnerSelection(spinner: Spinner, value: String) {
        val adapter = spinner.adapter ?: return
        for (i in 0 until adapter.count) {
            if (adapter.getItem(i).toString().equals(value, true)) {
                spinner.setSelection(i)
                return
            }
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun stamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQ_SELF = 3101
        private const val REQ_TARGET = 3102
        private const val REQ_SPIRIT = 3103
    }
}
