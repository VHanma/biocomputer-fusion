package com.vhanma.kialink

import android.content.Context

class MetamorphStore(context: Context) {
    private val prefs = context.getSharedPreferences("metamorph_core", Context.MODE_PRIVATE)

    fun xpFor(skillId: String): Int = prefs.getInt("xp_$skillId", 0)

    fun addXp(skillId: String, amount: Int): Int {
        val next = (xpFor(skillId) + amount).coerceAtLeast(0)
        prefs.edit().putInt("xp_$skillId", next).apply()
        return next
    }

    fun stageFor(skillId: String): SkillStage = SkillStage.fromXp(xpFor(skillId))

    fun setAutopilot(enabled: Boolean) {
        prefs.edit().putBoolean("autopilot", enabled).apply()
    }

    fun autopilotEnabled(): Boolean = prefs.getBoolean("autopilot", true)

    fun setRuleEnabled(ruleId: String, enabled: Boolean) {
        prefs.edit().putBoolean("rule_$ruleId", enabled).apply()
    }

    fun ruleEnabled(rule: AutoSummonRule): Boolean =
        prefs.getBoolean("rule_${rule.id}", rule.enabledByDefault)

    fun setActiveParadigm(id: String) {
        prefs.edit().putString("active_paradigm", id).apply()
    }

    fun activeParadigm(): String? = prefs.getString("active_paradigm", null)

    fun setConsciousState(state: ConsciousState) {
        prefs.edit().putString("conscious_state", state.name).apply()
    }

    fun consciousState(): ConsciousState {
        val raw = prefs.getString("conscious_state", ConsciousState.AWAKE.name) ?: ConsciousState.AWAKE.name
        return runCatching { ConsciousState.valueOf(raw) }.getOrDefault(ConsciousState.AWAKE)
    }

    fun setDreamMission(mission: String) {
        prefs.edit().putString("dream_mission", mission).apply()
    }

    fun dreamMission(): String = prefs.getString(
        "dream_mission",
        "Become lucid, remember the mission, enter the Dream Dojo."
    ) ?: "Become lucid, remember the mission, enter the Dream Dojo."

    fun setAstralSession(active: Boolean) {
        prefs.edit().putBoolean("astral_active", active).apply()
        if (active) setConsciousState(ConsciousState.ASTRAL_PROJECTION)
        else if (consciousState() == ConsciousState.ASTRAL_PROJECTION) setConsciousState(ConsciousState.AWAKE)
    }

    fun astralSessionActive(): Boolean = prefs.getBoolean("astral_active", false)

    fun appendLog(entry: String) {
        val old = prefs.getString("event_log", "") ?: ""
        val combined = (entry + "\n" + old).lineSequence().take(80).joinToString("\n")
        prefs.edit().putString("event_log", combined).apply()
    }

    fun eventLog(): String = prefs.getString("event_log", "") ?: ""
}
