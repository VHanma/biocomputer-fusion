package com.vhanma.kialink

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID


data class SpiritProfile(
    val id: String,
    val name: String,
    val type: String,
    val roleCode: String,
    val imageUri: String = "",
    val purpose: String,
    val innerDirective: String,
    val energySource: String,
    val authorizedActions: String,
    val prohibitedActions: String,
    val autoSummon: String,
    val completionRule: String,
    val autoReabsorb: Boolean,
    val commands: String,
    val anchorSeal: String = "",
    val notes: String = ""
) {
    fun sigilSeed(): String = listOf(
        id, name, type, roleCode, purpose, innerDirective, energySource,
        authorizedActions, prohibitedActions, autoSummon, completionRule,
        commands, anchorSeal
    ).joinToString("|")
}

class SpiritProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("metamorph_spirits", Context.MODE_PRIVATE)

    init { ensureDefaults() }

    fun loadAll(): List<SpiritProfile> {
        val arr = runCatching { JSONArray(prefs.getString("profiles", "[]") ?: "[]") }.getOrDefault(JSONArray())
        val out = mutableListOf<SpiritProfile>()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { decode(it) }?.let(out::add)
        return out
    }

    fun find(id: String): SpiritProfile? = loadAll().firstOrNull { it.id == id }

    fun save(profile: SpiritProfile) {
        val all = loadAll().toMutableList()
        val index = all.indexOfFirst { it.id == profile.id }
        if (index >= 0) all[index] = profile else all += profile
        write(all)
    }

    fun updateImage(id: String, uri: String) {
        val profile = find(id) ?: return
        save(profile.copy(imageUri = uri))
    }

    fun delete(id: String) {
        if (id.startsWith("core_")) return
        write(loadAll().filterNot { it.id == id })
    }

    fun newProfile(type: String = "SERVITOR"): SpiritProfile = SpiritProfile(
        id = "custom_${UUID.randomUUID()}",
        name = if (type == "TULPA") "New Tulpa" else "New Servitor",
        type = type,
        roleCode = "CUSTOM",
        purpose = "State one clear purpose.",
        innerDirective = "NOTICE → ALIGN → EXECUTE ASSIGNED PURPOSE → REPORT → RELEASE",
        energySource = "Ritual attention, breath, completed actions, visualization, and magician-approved offerings.",
        authorizedActions = "Only actions directly required by the stated purpose and explicitly approved commands.",
        prohibitedActions = defaultCovenant(),
        autoSummon = "Only when the magician-defined trigger occurs.",
        completionRule = "Goal complete or magician ends operation.",
        autoReabsorb = type != "TULPA",
        commands = "SUMMON, ACTIVATE, REPORT, SUSPEND, RECALL, REABSORB, DISSOLVE",
        notes = if (type == "TULPA") "Identity, relationship, boundaries, wonderland, dialogue, development." else "Task-bounded specialist."
    )

    private fun ensureDefaults() {
        if ((prefs.getString("profiles", "") ?: "").isNotBlank()) return
        val defaults = MetamorphData.servitors.map { s ->
            SpiritProfile(
                id = "core_${s.role.code}",
                name = "${s.role.code} // ${s.role.title}",
                type = "SERVITOR",
                roleCode = s.role.code,
                purpose = s.purpose,
                innerDirective = s.innerDirective,
                energySource = s.defaultEnergySource,
                authorizedActions = s.authorizedActions.joinToString(", "),
                prohibitedActions = s.prohibitedActions.joinToString(", ") + "\n" + defaultCovenant(),
                autoSummon = defaultAutoSummon(s.role),
                completionRule = s.completionRule,
                autoReabsorb = s.autoReabsorb,
                commands = roleCommands(s.role)
            )
        }
        write(defaults)
    }

    private fun defaultAutoSummon(role: ServitorRole): String = when (role) {
        ServitorRole.COM -> "When decisive execution is required after a clear opening or decision."
        ServitorRole.RAV -> "When options, combinations, momentum, or openings must be generated rapidly."
        ServitorRole.SEN -> "When danger, uncertainty, protection, observation, guarding, or counter-readiness is required."
        ServitorRole.SAB -> "When manipulation, hostile influence, obstruction, broken strategy, or threat momentum is detected."
        ServitorRole.SYN -> "When confidence, clarity, social intelligence, persuasion, focus, or allied enhancement is useful."
        ServitorRole.MED -> "When recovery, restoration, grounding, cleansing, stabilization, or emergency support is required."
    }

    private fun roleCommands(role: ServitorRole): String = when (role) {
        ServitorRole.COM -> "SUMMON, EXECUTE, PRESS, COMMIT, FINISH, EXPLOIT, BREAKTHROUGH, RECALL, REABSORB"
        ServitorRole.RAV -> "SUMMON, BUILD, CHAIN, ACCELERATE, EXPLORE, OVERLOAD, CREATE OPENING, RECALL, REABSORB"
        ServitorRole.SEN -> "SUMMON, SHIELD, GUARD, INTERCEPT, HOLD, COUNTER, FORTIFY, WATCH, RECALL, REABSORB"
        ServitorRole.SAB -> "SUMMON, SCAN, EXPOSE, DISCOURAGE, CONFUSE, DISRUPT, WEAKEN, SEVER, DISPEL, BREAK PATTERN, RECALL, REABSORB"
        ServitorRole.SYN -> "SUMMON, AMPLIFY, FOCUS, EMPOWER, ALIGN, REINFORCE, SUPPORT, RECALL, REABSORB"
        ServitorRole.MED -> "SUMMON, RESTORE, GROUND, CLEANSE, RECOVER, STABILIZE, REPAIR, REINTEGRATE, PHOENIX, RECALL, REABSORB"
    }

    private fun defaultCovenant(): String =
        "Creator retains command authority. Purpose cannot rewrite itself. Stay inside assigned domain. " +
            "Do not impersonate another being to obtain authority. Do not create fear to obtain attention. " +
            "Do not self-replicate or create sub-entities without explicit permission. " +
            "Do not draw from the creator's health, fear, pain, sleep, loved ones, animals, or unapproved people. " +
            "SUSPEND, RECALL, REABSORB, and DISSOLVE override all routines. Completion and expiry rules remain binding."

    private fun write(profiles: List<SpiritProfile>) {
        val arr = JSONArray()
        profiles.forEach { arr.put(encode(it)) }
        prefs.edit().putString("profiles", arr.toString()).apply()
    }

    private fun encode(p: SpiritProfile) = JSONObject().apply {
        put("id", p.id); put("name", p.name); put("type", p.type); put("roleCode", p.roleCode)
        put("imageUri", p.imageUri); put("purpose", p.purpose); put("innerDirective", p.innerDirective)
        put("energySource", p.energySource); put("authorizedActions", p.authorizedActions)
        put("prohibitedActions", p.prohibitedActions); put("autoSummon", p.autoSummon)
        put("completionRule", p.completionRule); put("autoReabsorb", p.autoReabsorb)
        put("commands", p.commands); put("anchorSeal", p.anchorSeal); put("notes", p.notes)
    }

    private fun decode(o: JSONObject): SpiritProfile? {
        val id = o.optString("id")
        if (id.isBlank()) return null
        return SpiritProfile(
            id = id,
            name = o.optString("name", "Unnamed"),
            type = o.optString("type", "SERVITOR"),
            roleCode = o.optString("roleCode", "CUSTOM"),
            imageUri = o.optString("imageUri"),
            purpose = o.optString("purpose"),
            innerDirective = o.optString("innerDirective"),
            energySource = o.optString("energySource"),
            authorizedActions = o.optString("authorizedActions"),
            prohibitedActions = o.optString("prohibitedActions"),
            autoSummon = o.optString("autoSummon"),
            completionRule = o.optString("completionRule"),
            autoReabsorb = o.optBoolean("autoReabsorb", true),
            commands = o.optString("commands"),
            anchorSeal = o.optString("anchorSeal"),
            notes = o.optString("notes")
        )
    }
}
