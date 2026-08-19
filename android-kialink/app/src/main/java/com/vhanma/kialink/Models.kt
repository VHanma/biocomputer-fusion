package com.vhanma.kialink

enum class SkillStage(val label: String, val minXp: Int) {
    FRAGMENT("Fragment", 0),
    SHARD("Skill Shard", 100),
    COMPLETE("Complete Shard", 300),
    FUSED("Fused Crystal", 700),
    SYSTEM("System Crystal", 1400),
    SEED("Seed Crystal", 2500);

    companion object {
        fun fromXp(xp: Int): SkillStage = entries.last { xp >= it.minXp }
    }
}

data class SkillNode(
    val id: String,
    val name: String,
    val description: String,
    val domains: Set<String>,
    val sources: Set<String> = emptySet(),
    val prerequisites: Set<String> = emptySet(),
    val fusionPartners: Set<String> = emptySet(),
    val tags: Set<String> = emptySet()
)

enum class ServitorRole(val code: String, val title: String) {
    COM("COM", "Commando"),
    RAV("RAV", "Ravager"),
    SEN("SEN", "Sentinel"),
    SAB("SAB", "Saboteur"),
    SYN("SYN", "Synergist"),
    MED("MED", "Medic")
}

data class ServitorConstitution(
    val role: ServitorRole,
    val purpose: String,
    val innerDirective: String,
    val defaultEnergySource: String,
    val authorizedActions: List<String>,
    val prohibitedActions: List<String>,
    val recallCommand: String = "RECALL",
    val reabsorbCommand: String = "REABSORB",
    val completionRule: String = "Goal complete or magician ends operation",
    val autoReabsorb: Boolean = true
)

data class Paradigm(
    val id: String,
    val name: String,
    val active: List<ServitorRole>,
    val background: List<ServitorRole> = emptyList(),
    val purpose: String,
    val subconsciousScript: String
)

data class AutoSummonRule(
    val id: String,
    val trigger: String,
    val paradigmId: String,
    val intensity: Int,
    val enabledByDefault: Boolean = true
)

enum class ConsciousState(val label: String) {
    AWAKE("Awake"),
    MEDITATION("Meditation"),
    TRANCE("Trance"),
    SLEEP("Sleep"),
    LUCID_DREAM("Lucid Dream"),
    ASTRAL_PROJECTION("Astral Projection"),
    INVOCATION("Invocation / Possession"),
    UNKNOWN("Unknown"),
    PHOENIX_CONDITION("Phoenix Condition")
}

data class DreamGateConfig(
    val cueDelayMinutes: Int = 360,
    val cueCount: Int = 3,
    val spacingMinutes: Int = 30,
    val volumePercent: Int = 18,
    val vibrationEnabled: Boolean = true,
    val lightEnabled: Boolean = false,
    val mission: String = "Become lucid, remember the mission, enter the Dream Dojo."
)
