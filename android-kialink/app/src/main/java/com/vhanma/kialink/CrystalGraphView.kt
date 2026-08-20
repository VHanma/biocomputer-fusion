package com.vhanma.kialink

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class CrystalGraphView(
    context: Context,
    private val store: MetamorphStore
) : View(context) {

    private val nodes: List<SkillNode>
        get() = MetamorphData.skills + store.customSkills()

    private val positions = mutableMapOf<String, Pair<Float, Float>>()
    private var selectedId: String? = null
    var onNodeSelected: ((SkillNode) -> Unit)? = null

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 24f
    }
    private val tinyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        textAlign = Paint.Align.CENTER
        textSize = 17f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(9, 7, 17))
        val current = nodes
        if (current.isEmpty()) return
        positions.clear()
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.38f

        current.firstOrNull { it.id == "golden_spot" }?.let { positions[it.id] = cx to cy }
        val outer = current.filterNot { it.id == "golden_spot" }
        outer.forEachIndexed { index, node ->
            val angle = (2.0 * PI * index / outer.size.coerceAtLeast(1)) - PI / 2.0
            val wobble = when (index % 3) { 0 -> 1.0f; 1 -> .84f; else -> .70f }
            positions[node.id] = (cx + cos(angle).toFloat() * radius * wobble) to
                (cy + sin(angle).toFloat() * radius * wobble)
        }

        drawRootLinks(canvas, current)
        drawOverlapLinks(canvas, current)
        drawFusionLinks(canvas, current)
        drawNodes(canvas, current)
    }

    private fun drawRootLinks(canvas: Canvas, current: List<SkillNode>) {
        val root = positions["golden_spot"] ?: return
        current.filter { it.id != "golden_spot" && (it.tags.contains("EMERGENT") || it.tags.contains("IDENTITY")) }.forEach { node ->
            val p = positions[node.id] ?: return@forEach
            linePaint.color = Color.argb(75, 255, 214, 104)
            linePaint.strokeWidth = 1.8f
            canvas.drawLine(root.first, root.second, p.first, p.second, linePaint)
        }
    }

    private fun drawOverlapLinks(canvas: Canvas, current: List<SkillNode>) {
        for (i in current.indices) {
            for (j in i + 1 until current.size) {
                val a = current[i]
                val b = current[j]
                if (a.id == "golden_spot" || b.id == "golden_spot") continue
                val shared = a.domains.intersect(b.domains)
                if (shared.size < 2) continue
                val p1 = positions[a.id] ?: continue
                val p2 = positions[b.id] ?: continue
                val selected = selectedId == a.id || selectedId == b.id
                linePaint.strokeWidth = if (selected) 2.4f else 1.1f
                linePaint.color = if (selected) Color.argb(125, 103, 231, 255) else Color.argb(30, 103, 231, 255)
                canvas.drawLine(p1.first, p1.second, p2.first, p2.second, linePaint)
            }
        }
    }

    private fun drawFusionLinks(canvas: Canvas, current: List<SkillNode>) {
        val byId = current.associateBy { it.id }
        current.forEach nodeLoop@ { node ->
            val start = positions[node.id] ?: return@nodeLoop
            node.fusionPartners.forEach partnerLoop@ { partnerId ->
                val partner = byId[partnerId] ?: return@partnerLoop
                if (node.id >= partner.id) return@partnerLoop
                val end = positions[partner.id] ?: return@partnerLoop
                linePaint.strokeWidth = if (selectedId == node.id || selectedId == partner.id) 3.8f else 2.4f
                linePaint.color = if (selectedId == node.id || selectedId == partner.id) {
                    Color.argb(220, 198, 135, 255)
                } else Color.argb(125, 174, 112, 255)
                canvas.drawLine(start.first, start.second, end.first, end.second, linePaint)
            }
        }
    }

    private fun drawNodes(canvas: Canvas, current: List<SkillNode>) {
        current.forEach nodeLoop@ { node ->
            val p = positions[node.id] ?: return@nodeLoop
            val stage = store.stageFor(node.id)
            nodePaint.color = if (node.tags.contains("EMERGENT")) emergentColor(stage) else stageColor(stage)
            val size = if (node.id == "golden_spot") 34f else 21f + stage.ordinal * 2.5f
            val path = diamond(p.first, p.second, size)
            if (selectedId == node.id) {
                val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(100, 220, 160, 255)
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(p.first, p.second, size * 1.55f, glow)
            }
            canvas.drawPath(path, nodePaint)
            val short = node.name.split(" ").take(2).joinToString(" ")
            canvas.drawText(short, p.first, p.second + size + 22f, if (node.id == "golden_spot") textPaint else tinyPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val current = nodes
        val hit = positions.entries.minByOrNull { (_, p) ->
            val dx = event.x - p.first
            val dy = event.y - p.second
            dx * dx + dy * dy
        } ?: return true
        val p = hit.value
        val dx = event.x - p.first
        val dy = event.y - p.second
        if (dx * dx + dy * dy < 3600f) {
            selectedId = hit.key
            current.firstOrNull { it.id == hit.key }?.let { onNodeSelected?.invoke(it) }
            invalidate()
        }
        return true
    }

    private fun diamond(cx: Float, cy: Float, r: Float): Path = Path().apply {
        moveTo(cx, cy - r)
        lineTo(cx + r * 0.78f, cy)
        lineTo(cx, cy + r)
        lineTo(cx - r * 0.78f, cy)
        close()
    }

    private fun stageColor(stage: SkillStage): Int = when (stage) {
        SkillStage.FRAGMENT -> Color.rgb(85, 87, 104)
        SkillStage.SHARD -> Color.rgb(85, 180, 230)
        SkillStage.COMPLETE -> Color.rgb(110, 240, 255)
        SkillStage.FUSED -> Color.rgb(164, 118, 255)
        SkillStage.SYSTEM -> Color.rgb(255, 174, 80)
        SkillStage.SEED -> Color.rgb(255, 224, 105)
    }

    private fun emergentColor(stage: SkillStage): Int = when (stage) {
        SkillStage.FRAGMENT -> Color.rgb(125, 84, 145)
        SkillStage.SHARD -> Color.rgb(175, 105, 235)
        SkillStage.COMPLETE -> Color.rgb(225, 135, 255)
        SkillStage.FUSED -> Color.rgb(255, 148, 228)
        SkillStage.SYSTEM -> Color.rgb(255, 183, 116)
        SkillStage.SEED -> Color.rgb(255, 229, 118)
    }
}
