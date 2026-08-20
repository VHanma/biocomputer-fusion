package com.vhanma.kialink

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

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
        val combined = (entry + "\n" + old).lineSequence().take(120).joinToString("\n")
        prefs.edit().putString("event_log", combined).apply()
    }

    fun eventLog(): String = prefs.getString("event_log", "") ?: ""

    fun customSkills(): List<SkillNode> {
        val raw = prefs.getString("custom_skills", "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        val out = mutableListOf<SkillNode>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out += SkillNode(
                id = o.optString("id"),
                name = o.optString("name"),
                description = o.optString("description"),
                domains = jsonSet(o.optJSONArray("domains")),
                sources = jsonSet(o.optJSONArray("sources")),
                prerequisites = jsonSet(o.optJSONArray("prerequisites")),
                fusionPartners = jsonSet(o.optJSONArray("fusionPartners")),
                tags = jsonSet(o.optJSONArray("tags"))
            )
        }
        return out.filter { it.id.isNotBlank() && it.name.isNotBlank() }
    }

    fun addCustomSkill(
        name: String,
        description: String,
        domains: Set<String>,
        sources: Set<String>,
        fusionPartners: Set<String> = emptySet(),
        tags: Set<String> = emptySet(),
        initialXp: Int = 25
    ): SkillNode {
        val normalized = name.trim().lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_')
        val shortHash = MessageDigest.getInstance("SHA-256")
            .digest((name + sources.joinToString()).toByteArray())
            .take(4).joinToString("") { "%02x".format(it) }
        val id = "custom_${normalized.take(32)}_$shortHash"
        val node = SkillNode(
            id = id,
            name = name.trim(),
            description = description.trim(),
            domains = domains,
            sources = sources,
            fusionPartners = fusionPartners,
            tags = tags
        )
        val all = customSkills().toMutableList()
        val existing = all.indexOfFirst { it.id == id || it.name.equals(node.name, true) }
        if (existing >= 0) all[existing] = node else all += node
        saveCustomSkills(all)
        if (xpFor(id) == 0 && initialXp > 0) addXp(id, initialXp)
        return node
    }

    private fun saveCustomSkills(skills: List<SkillNode>) {
        val arr = JSONArray()
        skills.takeLast(80).forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("description", s.description)
                put("domains", JSONArray(s.domains.toList()))
                put("sources", JSONArray(s.sources.toList()))
                put("prerequisites", JSONArray(s.prerequisites.toList()))
                put("fusionPartners", JSONArray(s.fusionPartners.toList()))
                put("tags", JSONArray(s.tags.toList()))
            })
        }
        prefs.edit().putString("custom_skills", arr.toString()).apply()
    }

    fun setPairAnchor(sealHex: String, targetName: String, traits: String, selfUri: String?, targetUri: String?) {
        prefs.edit()
            .putString("pair_seal", sealHex)
            .putString("pair_target", targetName)
            .putString("pair_traits", traits)
            .putString("pair_self_uri", selfUri ?: "")
            .putString("pair_target_uri", targetUri ?: "")
            .apply()
    }

    fun pairSeal(): String = prefs.getString("pair_seal", "") ?: ""
    fun pairTarget(): String = prefs.getString("pair_target", "") ?: ""
    fun pairTraits(): String = prefs.getString("pair_traits", "") ?: ""
    fun pairSelfUri(): String = prefs.getString("pair_self_uri", "") ?: ""
    fun pairTargetUri(): String = prefs.getString("pair_target_uri", "") ?: ""

    fun putRitualValue(key: String, value: String) {
        prefs.edit().putString("ritual_$key", value).apply()
    }

    fun ritualValue(key: String, fallback: String = ""): String =
        prefs.getString("ritual_$key", fallback) ?: fallback

    private fun jsonSet(arr: JSONArray?): Set<String> {
        if (arr == null) return emptySet()
        val out = linkedSetOf<String>()
        for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let(out::add)
        return out
    }
}
