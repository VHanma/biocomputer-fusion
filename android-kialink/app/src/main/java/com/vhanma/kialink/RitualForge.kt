package com.vhanma.kialink

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.view.View
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin


data class PairSeal(
    val digest: ByteArray,
    val code: String,
    val reducedIntent: String,
    val mantra: String,
    val traits: List<String>,
    val mode: String,
    val targetName: String
)

object PairForge {
    fun hashUri(context: Context, uri: Uri): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open image" }
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                md.update(buffer, 0, read)
            }
        }
        return md.digest()
    }

    fun forge(
        selfHash: ByteArray,
        targetHash: ByteArray,
        targetName: String,
        traitsText: String,
        intention: String,
        mode: String
    ): PairSeal {
        val traits = traitsText.split(',', ';', '\n').map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
        val intent = intention.trim().ifBlank { "I integrate ${traits.joinToString()} through $targetName" }
        val reduced = spareReduction(intent)
        val material = buildString {
            append(hex(selfHash)); append('|')
            append(hex(targetHash)); append('|')
            append(targetName.uppercase(Locale.US)); append('|')
            append(traits.joinToString("|") { it.uppercase(Locale.US) }); append('|')
            append(intent.uppercase(Locale.US)); append('|')
            append(reduced); append('|')
            append(mode)
        }
        val digest = digestText(material)
        return PairSeal(
            digest = digest,
            code = hex(digest).take(20).uppercase(Locale.US),
            reducedIntent = reduced,
            mantra = makeMantra(reduced, digest),
            traits = traits,
            mode = mode,
            targetName = targetName
        )
    }

    fun digestText(text: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun spareReduction(text: String): String {
        val seen = linkedSetOf<Char>()
        text.uppercase(Locale.US).forEach { c -> if (c in 'A'..'Z') seen += c }
        return seen.joinToString("").ifBlank { "KIA" }
    }

    private fun makeMantra(reduced: String, digest: ByteArray): String {
        if (reduced.isBlank()) return "KIA"
        val letters = reduced.toMutableList()
        val shift = (digest.firstOrNull()?.toInt()?.and(0xff) ?: 0) % letters.size
        val rotated = letters.drop(shift) + letters.take(shift)
        return rotated.chunked(2).joinToString("-") { it.joinToString("") }
    }
}

class SigilCanvas(context: Context) : View(context) {
    var selfBitmap: Bitmap? = null
    var targetBitmap: Bitmap? = null
    var seal: ByteArray? = null
    var targetName: String = "TARGET"
    var traits: List<String> = emptyList()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.rgb(116, 232, 255)
    }
    private val violet = Color.rgb(190, 115, 255)
    private val cyan = Color.rgb(103, 231, 255)
    private val ink = Color.rgb(8, 6, 16)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawScene(canvas, width.toFloat(), height.toFloat())
    }

    fun renderBitmap(outWidth: Int = 1400, outHeight: Int = 900): Bitmap {
        val out = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        drawScene(Canvas(out), outWidth.toFloat(), outHeight.toFloat())
        return out
    }

    private fun drawScene(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(ink)
        val cy = h * .42f
        val r = min(w, h) * .18f
        val lx = w * .22f
        val rx = w * .78f
        val mx = w * .5f

        drawImageCircle(canvas, selfBitmap, lx, cy, r, Color.rgb(80, 190, 255))
        drawImageCircle(canvas, targetBitmap, rx, cy, r, violet)
        drawCompositeCircle(canvas, mx, cy, r * .9f)

        line.color = Color.argb(180, 103, 231, 255)
        line.strokeWidth = maxOf(3f, w / 360f)
        val p1 = Path().apply {
            moveTo(lx + r * .75f, cy)
            cubicTo(w * .35f, cy - r * .8f, w * .40f, cy + r * .75f, mx - r * .7f, cy)
        }
        val p2 = Path().apply {
            moveTo(mx + r * .7f, cy)
            cubicTo(w * .60f, cy - r * .75f, w * .66f, cy + r * .8f, rx - r * .75f, cy)
        }
        canvas.drawPath(p1, line)
        canvas.drawPath(p2, line)

        seal?.let { drawSigil(canvas, mx, cy, r * .75f, it) }

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.WHITE
        paint.textSize = maxOf(24f, w / 30f)
        paint.isFakeBoldText = true
        canvas.drawText("SELF", lx, h * .75f, paint)
        canvas.drawText("EMERGENT", mx, h * .75f, paint)
        canvas.drawText(targetName.take(18).uppercase(Locale.US), rx, h * .75f, paint)
        paint.isFakeBoldText = false
        paint.color = Color.LTGRAY
        paint.textSize = maxOf(18f, w / 42f)
        val traitLine = traits.take(5).joinToString(" • ")
        if (traitLine.isNotBlank()) canvas.drawText(traitLine, mx, h * .86f, paint)
    }

    private fun drawImageCircle(canvas: Canvas, bmp: Bitmap?, cx: Float, cy: Float, r: Float, border: Int) {
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(25, 20, 38)
        canvas.drawCircle(cx, cy, r, paint)
        if (bmp != null) {
            val save = canvas.save()
            canvas.clipPath(Path().apply { addCircle(cx, cy, r * .94f, Path.Direction.CW) })
            val scale = maxOf((r * 2) / bmp.width, (r * 2) / bmp.height)
            val bw = bmp.width * scale
            val bh = bmp.height * scale
            val dst = RectF(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2)
            canvas.drawBitmap(bmp, null, dst, paint)
            canvas.restoreToCount(save)
        }
        line.color = border
        line.strokeWidth = 5f
        canvas.drawCircle(cx, cy, r, line)
    }

    private fun drawCompositeCircle(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(32, 23, 48)
        canvas.drawCircle(cx, cy, r, paint)
        val save = canvas.save()
        canvas.clipPath(Path().apply { addCircle(cx, cy, r * .94f, Path.Direction.CW) })
        listOf(selfBitmap to 115, targetBitmap to 140).forEach { (bmp, alpha) ->
            if (bmp != null) {
                val scale = maxOf((r * 2) / bmp.width, (r * 2) / bmp.height)
                val bw = bmp.width * scale
                val bh = bmp.height * scale
                paint.alpha = alpha
                canvas.drawBitmap(bmp, null, RectF(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2), paint)
            }
        }
        paint.alpha = 255
        canvas.restoreToCount(save)
        line.color = Color.rgb(255, 211, 102)
        line.strokeWidth = 6f
        canvas.drawCircle(cx, cy, r, line)
    }

    private fun drawSigil(canvas: Canvas, cx: Float, cy: Float, radius: Float, bytes: ByteArray) {
        val n = 7 + ((bytes[0].toInt() and 0xff) % 6)
        val path = Path()
        repeat(n) { i ->
            val b = bytes[(i + 1) % bytes.size].toInt() and 0xff
            val angle = 2.0 * PI * (b / 255.0) + i * .73
            val rr = radius * (.35f + ((bytes[(i + 9) % bytes.size].toInt() and 0xff) / 255f) * .65f)
            val x = cx + cos(angle).toFloat() * rr
            val y = cy + sin(angle).toFloat() * rr
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        line.color = Color.WHITE
        line.strokeWidth = maxOf(3f, radius / 30f)
        canvas.drawPath(path, line)
        repeat(3) { ring ->
            line.color = if (ring % 2 == 0) cyan else violet
            line.strokeWidth = 2f
            canvas.drawCircle(cx, cy, radius * (.38f + ring * .22f), line)
        }
    }
}

class SigilGlyphView(context: Context) : View(context) {
    var seal: ByteArray = PairForge.digestText("KIA")
    var title: String = "SIGIL"
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.rgb(8, 6, 16))
        val cx = width / 2f
        val cy = height * .46f
        val r = min(width, height) * .30f
        val n = 8 + ((seal[0].toInt() and 0xff) % 8)
        val path = Path()
        repeat(n) { i ->
            val a = (2 * PI * ((seal[(i + 1) % seal.size].toInt() and 0xff) / 255.0)) + i * .61
            val rr = r * (.32f + ((seal[(i + 13) % seal.size].toInt() and 0xff) / 255f) * .68f)
            val x = cx + cos(a).toFloat() * rr
            val y = cy + sin(a).toFloat() * rr
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        stroke.color = Color.rgb(120, 232, 255)
        canvas.drawCircle(cx, cy, r, stroke)
        stroke.color = Color.rgb(205, 130, 255)
        canvas.drawPath(path, stroke)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 30f
        paint.isFakeBoldText = true
        canvas.drawText(title.take(24), cx, height * .90f, paint)
    }
}
