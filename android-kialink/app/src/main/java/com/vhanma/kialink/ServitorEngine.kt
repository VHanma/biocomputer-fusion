package com.vhanma.kialink

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ServitorEngine(private val context: Context, private val store: MetamorphStore) {
    data class ActivationResult(
        val paradigm: Paradigm,
        val activeRoles: String,
        val backgroundRoles: String,
        val message: String
    )

    fun activateParadigm(id: String, source: String = "manual"): ActivationResult? {
        val paradigm = MetamorphData.paradigms.firstOrNull { it.id == id } ?: return null
        store.setActiveParadigm(id)
        log("SUMMON ${paradigm.name} via $source")
        return ActivationResult(
            paradigm,
            paradigm.active.joinToString(" / ") { it.code },
            paradigm.background.joinToString(" / ") { it.code }.ifBlank { "None" },
            paradigm.subconsciousScript
        )
    }

    fun activateRule(ruleId: String): ActivationResult? {
        val rule = MetamorphData.autoRules.firstOrNull { it.id == ruleId } ?: return null
        if (!store.autopilotEnabled() || !store.ruleEnabled(rule)) return null
        return activateParadigm(rule.paradigmId, "autosummon:${rule.trigger}")
    }

    fun currentParadigm(): Paradigm? = store.activeParadigm()?.let { id ->
        MetamorphData.paradigms.firstOrNull { it.id == id }
    }

    fun completeCurrentGoal(note: String = "Goal complete"): List<String> {
        val p = currentParadigm() ?: return emptyList()
        val roleSet = (p.active + p.background).toSet()
        val touched = linkedSetOf<String>()
        val skillTargets = MetamorphData.skills.filter { skill ->
            roleSet.any { role -> skill.domains.contains(role.code) }
        }
        skillTargets.forEach { skill ->
            store.addXp(skill.id, 8)
            touched += skill.name
        }
        log("COMPLETE ${p.name}: $note | AUTO-REABSORB ${roleSet.joinToString { it.code }}")
        store.setActiveParadigm("")
        return touched.toList()
    }

    fun allRecall() {
        val current = currentParadigm()?.name ?: "none"
        log("ALL RECALL from $current")
        store.setActiveParadigm("")
    }

    fun emergencyRecall() {
        log("EMERGENCY RECALL → SUSPEND → RECALL → REABSORB")
        store.setActiveParadigm("")
    }

    fun constitution(role: ServitorRole): ServitorConstitution =
        MetamorphData.servitors.first { it.role == role }

    private fun log(message: String) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        store.appendLog("$stamp | $message")
    }
}
