package com.vhanma.kialink

import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

class MainActivity : Activity() {

    private lateinit var linkView: LinkCanvas
    private lateinit var targetName: EditText
    private lateinit var traits: EditText
    private lateinit var intention: EditText
    private lateinit var modeSpinner: Spinner
    private lateinit var gnosisSpinner: Spinner
    private lateinit var removeVowels: CheckBox
    private lateinit var resultText: TextView
    private lateinit var chargeButton: Button

    private var selfHash: ByteArray? = null
    private var targetHash: ByteArray? = null
    private var currentSeal: ByteArray? = null
    private var reducedLetters: String = ""

    private val violet = Color.rgb(185, 108, 255)
    private val cyan = Color.rgb(101, 231, 255)
    private val ink = Color.rgb(8, 5, 15)
    private val panel = Color.rgb(22, 16, 34)
    private val soft = Color.rgb(205, 191, 222)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ink
        window.navigationBarColor = ink
        setContentView(buildUi())
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(ink)
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(28))
        }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(text("KIA // LINK", 30f, Color.WHITE, true).apply { letterSpacing = .12f })
        root.addView(text("Two-photo chaos sigil engine • offline • local-only", 14f, soft, false).apply {
            setPadding(0, dp(4), 0, dp(14))
        })

        linkView = LinkCanvas(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(285))
            background = rounded(panel, 24f, Color.rgb(52, 37, 72))
        }
        root.addView(linkView)

        val photoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(8))
        }
        photoRow.addView(actionButton("SELF PHOTO") { pickPhoto(REQ_SELF) }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(6) })
        photoRow.addView(actionButton("TARGET PHOTO") { pickPhoto(REQ_TARGET) }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(6) })
        root.addView(photoRow)

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(panel, 20f, Color.rgb(55, 39, 77))
        }
        root.addView(form)

        form.addView(label("TARGET TYPE"))
        modeSpinner = Spinner(this)
        modeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Person", "Character", "Deity", "Archetype", "Future Self"))
        form.addView(modeSpinner, matchWrap())

        form.addView(label("NAME / ARCHETYPE"))
        targetName = field("Example: Yujiro Hanma, Athena, future self")
        form.addView(targetName)

        form.addView(label("TRAITS TO EMBODY"))
        traits = field("Example: composure, perception, fearless pressure, discipline")
        form.addView(traits)

        form.addView(label("STATEMENT OF INTENT"))
        intention = field("I embody the selected traits naturally in thought, posture and action.")
        form.addView(intention)

        removeVowels = CheckBox(this).apply {
            text = "Optional Spare-derived compression: remove vowels"
            setTextColor(soft)
            buttonTintList = android.content.res.ColorStateList.valueOf(violet)
            setPadding(0, dp(4), 0, dp(4))
        }
        form.addView(removeVowels)

        form.addView(label("GNOSIS / CHARGING STYLE"))
        gnosisSpinner = Spinner(this)
        gnosisSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Hybrid: stillness → invocation → peak", "Inhibitory: silence + fixation", "Excitatory: movement + emotion", "Dream gate: pre-sleep visualization"))
        form.addView(gnosisSpinner, matchWrap())

        val forge = actionButton("FORGE LINK SIGIL") { forgeLink() }.apply {
            textSize = 17f
            setTextColor(Color.BLACK)
            background = rounded(violet, 18f, violet)
        }
        root.addView(forge, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)).apply { topMargin = dp(14) })

        resultText = text("Choose both photos, define the traits, then forge the pair seal.", 14f, soft, false).apply {
            setPadding(dp(4), dp(12), dp(4), dp(10))
        }
        root.addView(resultText)

        chargeButton = actionButton("CHARGE + INVOKE") { startChargeProtocol() }.apply { isEnabled = false }
        root.addView(chargeButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))

        val utilities = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        utilities.addView(actionButton("JOURNAL") { showJournal() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(5) })
        utilities.addView(actionButton("METHOD") { showMethod() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(5) })
        root.addView(utilities)

        root.addView(text(
            "The app's literal link is deterministic cryptographic pair-binding: the two image hashes + your intent always generate the same seal. “Quantum entanglement” is an optional ritual metaphor; literal quantum entanglement between people or photographs is not established by current physics.",
            12f, Color.rgb(150, 137, 168), false
        ).apply { setPadding(dp(2), dp(14), dp(2), 0) })

        return scroll
    }

    private fun pickPhoto(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, requestCode)
    }

    @Deprecated("Activity result API retained to keep this APK dependency-free")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { }

        val bitmap = loadBitmap(uri)
        val digest = hashUri(uri)
        when (requestCode) {
            REQ_SELF -> {
                selfHash = digest
                linkView.selfBitmap = bitmap
            }
            REQ_TARGET -> {
                targetHash = digest
                linkView.targetBitmap = bitmap
            }
        }
        currentSeal = null
        chargeButton.isEnabled = false
        resultText.text = "Photo loaded. Forge again whenever either image or intent changes."
        linkView.invalidate()
    }

    private fun forgeLink() {
        val a = selfHash
        val b = targetHash
        if (a == null || b == null) {
            toast("Choose both photos first")
            return
        }
        val name = targetName.text.toString().trim().ifBlank { "selected archetype" }
        val traitText = traits.text.toString().trim().ifBlank { "chosen qualities" }
        val rawIntent = intention.text.toString().trim().ifBlank {
            "I embody $traitText through the archetype of $name"
        }

        reducedLetters = spareReduction(rawIntent, removeVowels.isChecked)
        val mode = modeSpinner.selectedItem.toString()
        val pairMaterial = buildString {
            append(hex(a)); append('|')
            append(hex(b)); append('|')
            append(rawIntent.uppercase(Locale.US)); append('|')
            append(reducedLetters); append('|')
            append(mode)
        }.toByteArray(Charsets.UTF_8)

        currentSeal = MessageDigest.getInstance("SHA-256").digest(pairMaterial)
        linkView.seal = currentSeal
        linkView.startPulse()
        chargeButton.isEnabled = true

        val code = hex(currentSeal!!).take(16).uppercase(Locale.US)
        resultText.text = "PAIR SEAL  $code\nReduced intent letters: $reducedLetters\nThe seal is deterministic for this photo pair + intent."
        toast("Link sigil forged")
    }

    private fun spareReduction(text: String, dropVowels: Boolean): String {
        val seen = linkedSetOf<Char>()
        val vowels = setOf('A', 'E', 'I', 'O', 'U')
        text.uppercase(Locale.US).forEach { c ->
            if (c in 'A'..'Z' && (!dropVowels || c !in vowels)) seen.add(c)
        }
        return seen.joinToString("").ifBlank { "KIA" }
    }

    private fun startChargeProtocol() {
        val seal = currentSeal ?: run {
            toast("Forge a link sigil first")
            return
        }
        val profile = gnosisSpinner.selectedItemPosition
        val stages = when (profile) {
            1 -> listOf(
                Stage("QUIET", 60, "Become physically still. Let internal speech thin out. Keep attention on one point."),
                Stage("FIXATE", 75, "Gaze at the central seal. Hold the trait-intent without mentally repeating the sentence."),
                Stage("VOID", 35, "Allow the symbol to remain while the verbal meaning drops away."),
                Stage("RELEASE", 20, "Look away. Shift attention completely. The traditional chaos-magick move is to drop lust-of-result.")
            )
            2 -> listOf(
                Stage("BUILD", 55, "Use rhythmic movement, posture and breath to build emotional intensity around the chosen traits."),
                Stage("INVOKE", 80, "Move and hold yourself as the target archetype would. Copy decision rhythm, gaze, stance and emotional tone."),
                Stage("PEAK", 30, "At the strongest point, stop movement and lock attention onto the seal."),
                Stage("RELEASE", 20, "Break state deliberately. Laugh, shake out, or switch to an unrelated task.")
            )
            3 -> listOf(
                Stage("PRE-SLEEP", 70, "Relax and visualize both images connected by the seal. Keep the scene simple and vivid."),
                Stage("IDENTITY SCENE", 90, "Imagine one concrete tomorrow-scene in first person where you act through the selected traits."),
                Stage("SEAL", 30, "Fix the sigil as the final mental image, then close the session and sleep when ready.")
            )
            else -> listOf(
                Stage("STILLNESS", 45, "Quiet the body and narrow attention. Let the ordinary self-story soften."),
                Stage("FIXATION", 50, "Gaze at the pair seal until the lines feel visually automatic rather than verbally interpreted."),
                Stage("INVOCATION", 80, "Embody the target's selected traits now: posture, breath, gaze, tempo, choices and inner voice."),
                Stage("PEAK CHARGE", 30, "Build one clean surge of emotion and attention, then place it on the seal."),
                Stage("BANISH / RELEASE", 20, "Break the state on purpose. Look away, laugh or shift attention. Leave the result alone.")
            )
        }
        runRitualDialog(stages, seal)
    }

    private fun runRitualDialog(stages: List<Stage>, seal: ByteArray) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(12))
        }
        val stageTitle = text("", 24f, Color.WHITE, true)
        val timerText = text("", 44f, cyan, true).apply { gravity = Gravity.CENTER }
        val instruction = text("", 15f, Color.LTGRAY, false)
        val progress = text("", 12f, soft, false)
        box.addView(stageTitle)
        box.addView(timerText)
        box.addView(instruction)
        box.addView(progress)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Charge protocol")
            .setView(box)
            .setNegativeButton("STOP", null)
            .setPositiveButton("ADVANCE", null)
            .create()

        var stageIndex = 0
        var timer: CountDownTimer? = null

        fun launchStage() {
            if (stageIndex >= stages.size) {
                dialog.dismiss()
                linkView.stopPulse()
                showIntegration(seal)
                return
            }
            val s = stages[stageIndex]
            stageTitle.text = s.name
            instruction.text = s.instruction
            progress.text = "Stage ${stageIndex + 1} of ${stages.size}"
            timer?.cancel()
            timer = object : CountDownTimer(s.seconds * 1000L, 1000L) {
                override fun onTick(ms: Long) {
                    val sec = (ms / 1000L).toInt()
                    timerText.text = String.format(Locale.US, "%d:%02d", sec / 60, sec % 60)
                }
                override fun onFinish() {
                    stageIndex++
                    launchStage()
                }
            }.start()
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                stageIndex++
                launchStage()
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                timer?.cancel()
                linkView.stopPulse()
                dialog.dismiss()
            }
            linkView.startPulse()
            launchStage()
        }
        dialog.setOnDismissListener { timer?.cancel() }
        dialog.show()
    }

    private fun showIntegration(seal: ByteArray) {
        val input = EditText(this).apply {
            hint = "When [cue] happens, I immediately [trait-based action]."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            minLines = 3
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle("Integration lock")
            .setMessage("Turn the ritual into a real-world trigger. One specific if-then action makes the selected identity easier to enact repeatedly.")
            .setView(input)
            .setPositiveButton("SAVE") { _, _ ->
                saveJournal(input.text.toString(), seal)
                toast("Session sealed in journal")
            }
            .setNegativeButton("SKIP", null)
            .show()
    }

    private fun saveJournal(plan: String, seal: ByteArray) {
        val prefs = getSharedPreferences("kia_link", MODE_PRIVATE)
        val old = prefs.getString("journal", "") ?: ""
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val entry = listOf(
            stamp,
            targetName.text.toString().replace("|", "/"),
            traits.text.toString().replace("|", "/"),
            hex(seal).take(16).uppercase(Locale.US),
            plan.replace("|", "/").replace("\n", " ")
        ).joinToString("|")
        val combined = (entry + "\n" + old).lineSequence().take(30).joinToString("\n")
        prefs.edit().putString("journal", combined).apply()
    }

    private fun showJournal() {
        val raw = getSharedPreferences("kia_link", MODE_PRIVATE).getString("journal", "") ?: ""
        val display = if (raw.isBlank()) "No charged sessions yet." else raw.lineSequence().joinToString("\n\n") { line ->
            val p = line.split('|')
            if (p.size >= 5) "${p[0]}  •  ${p[1]}\nTraits: ${p[2]}\nSeal: ${p[3]}\nIntegration: ${p[4]}" else line
        }
        AlertDialog.Builder(this)
            .setTitle("Magical journal")
            .setMessage(display)
            .setPositiveButton("CLOSE", null)
            .show()
    }

    private fun showMethod() {
        val message = """
            KIA Link combines several historically influential methods rather than inventing a random sigil filter.

            1. SPARE / CHAOS SIGIL: state the desire, remove repeated letters, stylize the remainder into a glyph. Vowel removal is optional because it is a later/common compression variant, not a required rule in Liber Null.

            2. MAGICAL LINK: the two selected photographs act as symbolic links. The app also gives them a literal digital bond by hashing both images together with the intent. Same inputs = same pair seal.

            3. GNOSIS + CHARGE: inhibitory stillness/fixation, excitatory embodiment, or a hybrid are used to narrow attention and charge the symbol.

            4. INVOCATION / METAMORPHOSIS: instead of merely wishing to “become” the target, you select traits and rehearse the target identity through posture, imagery, decisions and behavior.

            5. RELEASE: after charging, attention is deliberately broken. This follows the chaos-magick emphasis on dropping conscious obsession with the result.

            6. INTEGRATION LOCK: an if-then cue turns the archetypal work into a repeatable behavior. Mental imagery and implementation-intention research give this layer a conventional psychological pathway alongside the occult model.

            Sources behind the design: Austin Osman Spare; Peter J. Carroll, Liber Null & Psychonaut and Liber Kaos; Phil Hine, Condensed Chaos; Grant Morrison, Pop Magic; J. G. Frazer on sympathetic magic; Gollwitzer's implementation-intention research; modern sport-imagery reviews.
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("Why this method")
            .setMessage(message)
            .setPositiveButton("CLOSE", null)
            .show()
    }

    private fun loadBitmap(uri: Uri): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 28) {
            val source = android.graphics.ImageDecoder.createSource(contentResolver, uri)
            android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val maxSide = max(info.size.width, info.size.height)
                if (maxSide > 1600) decoder.setTargetSampleSize(ceil(maxSide / 1600.0).toInt())
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(contentResolver, uri)
        }
    } catch (_: Exception) { null }

    private fun hashUri(uri: Uri): ByteArray? = try {
        val md = MessageDigest.getInstance("SHA-256")
        contentResolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        } ?: return null
        md.digest()
    } catch (_: Exception) { null }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun text(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun label(value: String) = text(value, 11f, cyan, true).apply {
        letterSpacing = .08f
        setPadding(dp(2), dp(10), dp(2), dp(4))
    }

    private fun field(hintText: String) = EditText(this).apply {
        hint = hintText
        setTextColor(Color.WHITE)
        setHintTextColor(Color.rgb(132, 119, 147))
        textSize = 15f
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = rounded(Color.rgb(14, 10, 23), 14f, Color.rgb(71, 51, 96))
        minLines = 1
    }

    private fun actionButton(label: String, click: () -> Unit) = Button(this).apply {
        text = label
        textSize = 13f
        setTextColor(Color.WHITE)
        background = rounded(Color.rgb(46, 31, 65), 16f, Color.rgb(89, 59, 121))
        setOnClickListener { click() }
    }

    private fun rounded(fill: Int, radiusDp: Float, stroke: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    data class Stage(val name: String, val seconds: Int, val instruction: String)

    companion object {
        private const val REQ_SELF = 1001
        private const val REQ_TARGET = 1002
    }
}

class LinkCanvas(context: android.content.Context) : View(context) {
    var selfBitmap: Bitmap? = null
    var targetBitmap: Bitmap? = null
    var seal: ByteArray? = null
    private var phase = 0f
    private var animator: ValueAnimator? = null

    private val violet = Color.rgb(185, 108, 255)
    private val cyan = Color.rgb(101, 231, 255)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(13, 8, 22))
        val w = width.toFloat()
        val h = height.toFloat()
        val r = min(w * .205f, h * .31f)
        val cy = h * .43f
        val lx = w * .25f
        val rx = w * .75f

        drawPhoto(canvas, selfBitmap, lx, cy, r, "SELF")
        drawPhoto(canvas, targetBitmap, rx, cy, r, "TARGET")

        val linkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cyan
            strokeWidth = 2.2f + 1.5f * sin(phase * Math.PI).toFloat().absoluteValue
            alpha = 110 + (90 * phase).toInt()
        }
        val points = 7
        for (i in 0 until points) {
            val yOffset = (i - points / 2f) * r * .17f
            val wobble = sin(phase * Math.PI * 2 + i) * r * .06f
            canvas.drawLine(lx + r * .72f, cy + yOffset, rx - r * .72f, cy - yOffset + wobble.toFloat(), linkPaint)
        }

        val cx = w / 2f
        drawSigil(canvas, cx, cy, r * .62f)

        val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(177, 163, 196)
            textAlign = Paint.Align.CENTER
            textSize = 12f * resources.displayMetrics.scaledDensity
        }
        canvas.drawText(if (seal == null) "PHOTO A  ⇄  INTENT  ⇄  PHOTO B" else "PAIR-BOUND SIGIL ACTIVE", cx, h * .86f, caption)
    }

    private fun drawPhoto(canvas: Canvas, bitmap: Bitmap?, cx: Float, cy: Float, r: Float, label: String) {
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = if (label == "SELF") cyan else violet
            alpha = 220
        }
        canvas.drawCircle(cx, cy, r + 5f, ring)
        canvas.drawCircle(cx, cy, r + 13f + 5f * sin(phase * Math.PI * 2).toFloat(), Paint(ring).apply { alpha = 60 })

        if (bitmap != null) {
            val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val bw = bitmap.width.toFloat()
            val bh = bitmap.height.toFloat()
            val scale = max((2 * r) / bw, (2 * r) / bh)
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(cx - bw * scale / 2f, cy - bh * scale / 2f)
            }
            shader.setLocalMatrix(matrix)
            canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
        } else {
            canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(31, 23, 43) })
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.LTGRAY
                textAlign = Paint.Align.CENTER
                textSize = 12f * resources.displayMetrics.scaledDensity
            }
            canvas.drawText("CHOOSE", cx, cy, p)
        }

        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 11f * resources.displayMetrics.scaledDensity
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(label, cx, cy + r + 30f, p)
    }

    private fun drawSigil(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val data = seal ?: ByteArray(32) { ((it * 37 + 11) and 0xff).toByte() }
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = violet
            strokeWidth = 8f
            alpha = if (seal == null) 35 else 55 + (35 * phase).toInt()
        }
        canvas.drawCircle(cx, cy, radius * .78f, glow)

        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.3f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.WHITE
            alpha = if (seal == null) 105 else 245
        }
        val path = Path()
        val n = 12
        for (i in 0 until n) {
            val b = data[i].toInt() and 0xff
            val angle = (b / 255.0 * Math.PI * 2.0) + i * .31
            val rr = radius * (.32f + ((data[(i + 12) % data.size].toInt() and 0xff) / 255f) * .55f)
            val x = cx + cos(angle).toFloat() * rr
            val y = cy + sin(angle).toFloat() * rr
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            if ((b and 3) == 0) canvas.drawCircle(x, y, 4.5f, Paint(p).apply { style = Paint.Style.FILL })
        }
        path.close()
        canvas.drawPath(path, p)

        val axis = Paint(p).apply { color = cyan; alpha = if (seal == null) 60 else 180; strokeWidth = 1.8f }
        canvas.drawLine(cx, cy - radius * .92f, cx, cy + radius * .92f, axis)
        canvas.drawCircle(cx, cy, radius * .14f, Paint(axis).apply { strokeWidth = 2.5f })
    }

    fun startPulse() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1700L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopPulse() {
        animator?.cancel()
        animator = null
        phase = 0f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
