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

    private val nodes = MetamorphData.skills
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
        if (nodes.isEmpty()) return
        positions.clear()
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.38f

        nodes.firstOrNull { it.id == "golden_spot" }?.let { positions[it.id] = cx to cy }
        val outer = nodes.filterNot { it.id == "golden_spot" }
        outer.forEachIndexed { index, node ->
            val angle = (2.0 * PI * index / outer.size) - PI / 2.0
            val wobble = if (index % 2 == 0) 1.0f else 0.82f
            positions[node.id] = (cx + cos(angle).toFloat() * radius * wobble) to
                (cy + sin(angle).toFloat() * radius * wobble)
        }

        drawOverlapLinks(canvas)
        drawFusionLinks(canvas)
        drawNodes(canvas)
    }

    private fun drawOverlapLinks(canvas: Canvas) {
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val a = nodes[i]
                val b = nodes[j]
                if (a.id == "golden_spot" || b.id == "golden_spot") continue
                val shared = a.domains.intersect(b.domains)
                if (shared.size < 2) continue
                val p1 = positions[a.id] ?: continue
                val p2 = positions[b.id] ?: continue
                val selected = selectedId == a.id || selectedId == b.id
                linePaint.strokeWidth = if (selected) 2.4f else 1.2f
                linePaint.color = if (selected) Color.argb(125, 103, 231, 255) else Color.argb(35, 103, 231, 255)
                canvas.drawLine(p1.first, p1.second, p2.first, p2.second, linePaint)
            }
        }
    }

    private fun drawFusionLinks(canvas: Canvas) {
        val byId = nodes.associateBy { it.id }
        nodes.forEach nodeLoop@ { node ->
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

    private fun drawNodes(canvas: Canvas) {
        nodes.forEach nodeLoop@ { node ->
            val p = positions[node.id] ?: return@nodeLoop
            val stage = store.stageFor(node.id)
            nodePaint.color = stageColor(stage)
            val size = if (node.id == "golden_spot") 34f else 24f + stage.ordinal * 2.5f
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
            canvas.drawText(short, p.first, p.second + size + 24f, if (node.id == "golden_spot") textPaint else tinyPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
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
            nodes.firstOrNull { it.id == hit.key }?.let { onNodeSelected?.invoke(it) }
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
}
