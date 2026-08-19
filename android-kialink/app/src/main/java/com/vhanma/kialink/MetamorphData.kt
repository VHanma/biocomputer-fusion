package com.vhanma.kialink

object MetamorphData {
    val skills = listOf(
        SkillNode(
            id = "golden_spot",
            name = "Golden Spot",
            description = "Root-state crystal for centered intention, reduced distortion, and coherent action.",
            domains = setOf("MIND", "BODY", "SPIRIT", "MAGICK"),
            sources = setOf("NOVITSKIY"),
            tags = setOf("ROOT", "CENTER")
        ),
        SkillNode(
            id = "pattern_recognition",
            name = "Pattern Recognition",
            description = "Detect recurring structure, timing, intent, cues, and relationships across domains.",
            domains = setOf("MIND", "SEN", "SAB", "COMBAT", "SOCIAL", "MAGICK", "DREAM"),
            sources = setOf("JUNG", "COMBAT STUDY", "CHAOS MAGICK"),
            fusionPartners = setOf("calm_pressure", "distance_awareness", "manipulation_recognition"),
            tags = setOf("OVERLAP", "PERCEPTION")
        ),
        SkillNode(
            id = "calm_pressure",
            name = "Relaxation Under Pressure",
            description = "Preserve breath, structure, perception, and decision quality while stress rises.",
            domains = setOf("MIND", "BODY", "MED", "SEN", "SOCIAL", "COMBAT", "DREAM"),
            sources = setOf("PUNCH DOCTOR", "ALEXANDER", "BOXING"),
            fusionPartners = setOf("pattern_recognition", "breath_control", "combat_timing"),
            tags = setOf("OVERLAP", "STATE")
        ),
        SkillNode(
            id = "breath_control",
            name = "Breath Control",
            description = "Use breath to stabilize posture, rhythm, attention, arousal, and recovery.",
            domains = setOf("BODY", "MIND", "SPIRIT", "MED", "SYN", "COMBAT", "MEDITATION"),
            sources = setOf("PUNCH DOCTOR", "MEDITATION", "YOGA"),
            fusionPartners = setOf("calm_pressure", "postural_stack"),
            tags = setOf("OVERLAP", "RECOVERY")
        ),
        SkillNode(
            id = "postural_stack",
            name = "Postural Stack",
            description = "Organize head, ribcage, pelvis, feet, and axial support for efficient movement.",
            domains = setOf("BODY", "SEN", "SYN", "COMBAT", "MOVEMENT"),
            sources = setOf("ANATOMY TRAINS", "ALEXANDER"),
            fusionPartners = setOf("spiral_transmission", "scapular_control", "ground_force"),
            tags = setOf("STRUCTURE")
        ),
        SkillNode(
            id = "spiral_transmission",
            name = "Spiral Transmission",
            description = "Coordinate rotational force through connected trunk and limb pathways.",
            domains = setOf("BODY", "COM", "RAV", "BOXING", "MUAY THAI", "WRESTLING"),
            sources = setOf("ANATOMY TRAINS", "PUNCH DOCTOR", "BAKI", "KENGAN"),
            fusionPartners = setOf("ground_force", "scapular_control", "hip_rotation"),
            tags = setOf("POWER", "OVERLAP")
        ),
        SkillNode(
            id = "ground_force",
            name = "Ground Force",
            description = "Transfer force through stance, feet, legs, pelvis, trunk, and upper body without leaks.",
            domains = setOf("BODY", "COM", "BOXING", "MUAY THAI", "WRESTLING"),
            sources = setOf("PUNCH DOCTOR", "ANATOMY TRAINS", "KENGAN"),
            fusionPartners = setOf("spiral_transmission", "hip_rotation"),
            tags = setOf("POWER")
        ),
        SkillNode(
            id = "hip_rotation",
            name = "Hip Rotation",
            description = "Use hip turn as a shared engine for strikes, kicks, entries, throws, and directional changes.",
            domains = setOf("BODY", "COM", "RAV", "BOXING", "MUAY THAI", "WRESTLING", "MOVEMENT"),
            sources = setOf("PUNCH DOCTOR", "ANATOMY TRAINS", "BAKI", "KENGAN"),
            fusionPartners = setOf("spiral_transmission", "ground_force"),
            tags = setOf("OVERLAP", "POWER")
        ),
        SkillNode(
            id = "scapular_control",
            name = "Scapular Control",
            description = "Manage shoulder blade position and motion for striking, guarding, grappling, posture, and shoulder resilience.",
            domains = setOf("BODY", "SEN", "COM", "BOXING", "GRAPPLING", "POSTURE"),
            sources = setOf("PUNCH DOCTOR", "ANATOMY TRAINS", "ALEXANDER"),
            fusionPartners = setOf("postural_stack", "spiral_transmission"),
            tags = setOf("OVERLAP", "STRUCTURE")
        ),
        SkillNode(
            id = "distance_awareness",
            name = "Distance Awareness",
            description = "Read and manage spatial relationship, reach, entry range, escape range, and position.",
            domains = setOf("MIND", "SEN", "RAV", "COMBAT", "SOCIAL"),
            sources = setOf("BOXING", "MMA", "KENGAN"),
            fusionPartners = setOf("pattern_recognition", "combat_timing"),
            tags = setOf("PERCEPTION", "OVERLAP")
        ),
        SkillNode(
            id = "combat_timing",
            name = "Combat Timing",
            description = "Recognize when to enter, interrupt, counter, disengage, or change rhythm.",
            domains = setOf("MIND", "COM", "RAV", "SEN", "COMBAT"),
            sources = setOf("BOXING", "MMA", "BAKI", "KENGAN"),
            fusionPartners = setOf("pattern_recognition", "distance_awareness", "calm_pressure"),
            tags = setOf("TIMING")
        ),
        SkillNode(
            id = "manipulation_recognition",
            name = "Manipulation Recognition",
            description = "Notice coercion, false urgency, guilt hooks, contradiction, baiting, pressure, and hidden incentives.",
            domains = setOf("MIND", "SAB", "SEN", "SYN", "SOCIAL"),
            sources = setOf("JUNG", "SOCIAL PSYCHOLOGY"),
            fusionPartners = setOf("pattern_recognition", "boundary_setting", "calm_pressure"),
            tags = setOf("SOCIAL", "OVERLAP")
        ),
        SkillNode(
            id = "boundary_setting",
            name = "Boundary Setting",
            description = "Maintain clear limits while preserving strategic awareness and choice.",
            domains = setOf("MIND", "SEN", "SYN", "SOCIAL", "RELATIONSHIPS"),
            sources = setOf("JUNG", "PSYCHOLOGY"),
            fusionPartners = setOf("manipulation_recognition", "calm_pressure"),
            tags = setOf("SOCIAL")
        ),
        SkillNode(
            id = "lucidity",
            name = "Lucidity Recognition",
            description = "Recognize dream-state cues and stabilize deliberate awareness inside dreams.",
            domains = setOf("MIND", "DREAM", "SPIRIT", "SEN"),
            sources = setOf("LUCID DREAM RESEARCH", "DREAMWORK"),
            fusionPartners = setOf("dream_recall", "visualization"),
            tags = setOf("DREAM")
        ),
        SkillNode(
            id = "dream_recall",
            name = "Dream Recall",
            description = "Retain and record dream content, symbols, training events, and recurring environments.",
            domains = setOf("MIND", "DREAM", "MAGICK"),
            sources = setOf("DREAMWORK", "JUNG"),
            fusionPartners = setOf("lucidity", "symbol_observation"),
            tags = setOf("DREAM", "JOURNAL")
        ),
        SkillNode(
            id = "visualization",
            name = "Visualization",
            description = "Construct vivid internal imagery for rehearsal, ritual, dream incubation, and identity embodiment.",
            domains = setOf("MIND", "SPIRIT", "SYN", "DREAM", "MAGICK", "COMBAT"),
            sources = setOf("CHAOS MAGICK", "DEITY YOGA", "SPORT IMAGERY"),
            fusionPartners = setOf("lucidity", "godform_assumption"),
            tags = setOf("OVERLAP", "IMAGERY")
        ),
        SkillNode(
            id = "godform_assumption",
            name = "Godform / Archetype Assumption",
            description = "Deliberately enter a chosen archetypal identity-state through posture, imagery, symbolism, and intention.",
            domains = setOf("SPIRIT", "MAGICK", "SYN", "MIND", "IDENTITY"),
            sources = setOf("CEREMONIAL MAGICK", "THELEMA", "DEITY YOGA", "JUNG"),
            fusionPartners = setOf("visualization", "shadow_integration"),
            tags = setOf("IDENTITY", "INVOCATION")
        ),
        SkillNode(
            id = "shadow_integration",
            name = "Shadow Integration",
            description = "Identify disowned traits, projections, opposites, and transform them into conscious choices.",
            domains = setOf("MIND", "SPIRIT", "JUNG", "MAGICK", "RELATIONSHIPS"),
            sources = setOf("JUNG", "ALCHEMY"),
            fusionPartners = setOf("godform_assumption", "projection_retrieval"),
            tags = setOf("JUNG", "IDENTITY")
        ),
        SkillNode(
            id = "projection_retrieval",
            name = "Projection Retrieval",
            description = "Examine intense reactions to others and recover the underlying qualities as material for growth.",
            domains = setOf("MIND", "JUNG", "RELATIONSHIPS", "MAGICK"),
            sources = setOf("JUNG"),
            fusionPartners = setOf("shadow_integration", "pattern_recognition"),
            tags = setOf("JUNG")
        ),
        SkillNode(
            id = "symbol_observation",
            name = "Symbol Observation",
            description = "Record symbols and events before interpretation, then compare patterns over time.",
            domains = setOf("MIND", "MAGICK", "DREAM", "JUNG"),
            sources = setOf("JUNG", "CHAOS MAGICK"),
            fusionPartners = setOf("dream_recall", "pattern_recognition"),
            tags = setOf("JOURNAL")
        )
    )

    val servitors = listOf(
        ServitorConstitution(
            role = ServitorRole.COM,
            purpose = "Decisive execution and completion when an opening, decision, or task is ready.",
            innerDirective = "OBSERVE → DECIDE → COMMIT → COMPLETE → RELEASE",
            defaultEnergySource = "Ritual attention, breath, completed actions, symbolic charging, and magician-approved offerings.",
            authorizedActions = listOf("execute", "press", "commit", "finish", "exploit opening", "breakthrough", "surface relevant Complete Shards"),
            prohibitedActions = listOf("self-rewrite purpose", "draw from health or loved ones", "continue after completion", "ignore master recall")
        ),
        ServitorConstitution(
            role = ServitorRole.RAV,
            purpose = "Generate options, chains, adaptation, and openings rapidly.",
            innerDirective = "EXPLORE → CONNECT → BUILD MOMENTUM → CREATE OPENING",
            defaultEnergySource = "Curiosity, movement, visualization, completed experiments, and magician-approved ritual charge.",
            authorizedActions = listOf("build", "chain", "accelerate", "explore", "combine skills", "create opening"),
            prohibitedActions = listOf("create chaos without objective", "self-replicate", "continue after completion", "ignore master recall")
        ),
        ServitorConstitution(
            role = ServitorRole.SEN,
            purpose = "Observation, defense, guarding, counter-readiness, boundaries, and stability.",
            innerDirective = "NOTICE → STABILIZE → PROTECT → READ → COUNTER WHEN APPROPRIATE",
            defaultEnergySource = "Attention, stillness, breath, protective ritual charge, and completed defensive actions.",
            authorizedActions = listOf("shield", "guard", "intercept", "observe", "hold", "counter", "fortify", "watch"),
            prohibitedActions = listOf("pursue threats unnecessarily", "abandon protected objective", "self-rewrite purpose", "ignore master recall")
        ),
        ServitorConstitution(
            role = ServitorRole.SAB,
            purpose = "Detect and dismantle obstacles, manipulation patterns, hostile influence, and broken strategies.",
            innerDirective = "DETECT → EXPOSE → CONFUSE/DISRUPT OBSTACLE → DISMANTLE → RELEASE",
            defaultEnergySource = "Analysis, written problem definition, symbolic charge, completed pattern-breaks, and magician-approved offerings.",
            authorizedActions = listOf("scan", "expose", "discourage", "confuse threat expectations", "disrupt", "weaken obstacle", "sever", "dispel", "break pattern"),
            prohibitedActions = listOf("indiscriminate targeting", "feed on fear", "self-rewrite purpose", "continue after goal completion")
        ),
        ServitorConstitution(
            role = ServitorRole.SYN,
            purpose = "Amplify useful states, skills, confidence, social intelligence, resilience, and allied workings.",
            innerDirective = "ALIGN → AMPLIFY → SUPPORT → ADAPT → MAINTAIN",
            defaultEnergySource = "Positive completion, music, breath, movement, visualization, gratitude, and magician-approved ritual charge.",
            authorizedActions = listOf("amplify", "focus", "empower", "align", "reinforce", "surface persuasion/negotiation skills", "strengthen relevant shards"),
            prohibitedActions = listOf("override master values", "feed on dependency", "self-rewrite purpose", "ignore master recall")
        ),
        ServitorConstitution(
            role = ServitorRole.MED,
            purpose = "Recovery, restoration, grounding, reintegration, cleansing, and emergency support.",
            innerDirective = "STABILIZE → RESTORE → RECOVER → INTEGRATE",
            defaultEnergySource = "Rest, breath, hydration/food rituals, healing symbolism, completed recovery actions, and magician-approved offerings.",
            authorizedActions = listOf("restore", "ground", "cleanse", "recover", "stabilize", "repair", "reintegrate", "phoenix protocol"),
            prohibitedActions = listOf("draw from another person's wellbeing", "self-rewrite purpose", "continue after completion", "ignore master recall")
        )
    )

    val paradigms = listOf(
        Paradigm("battle", "APEX RESPONSE", listOf(ServitorRole.SEN, ServitorRole.RAV, ServitorRole.COM), listOf(ServitorRole.SAB, ServitorRole.SYN, ServitorRole.MED), "Adaptive emergency conflict response.", "See clearly, protect life, discourage/redirect if viable, adapt fast, act decisively when necessary, stop when the immediate threat ends."),
        Paradigm("clear_mind", "CLEAR MIND", listOf(ServitorRole.SEN, ServitorRole.SAB, ServitorRole.SYN), listOf(ServitorRole.MED), "Manipulation and coercion resistance.", "Pause. Separate fact from pressure. Detect contradictions and incentives. Increase awareness, confidence, and strategic choice."),
        Paradigm("motivate", "IGNITION", listOf(ServitorRole.SYN, ServitorRole.COM, ServitorRole.MED), emptyList(), "Break low-motivation loops and begin useful action.", "Reduce overwhelm, reconnect meaning, choose the smallest meaningful action, start now, then build momentum."),
        Paradigm("social", "CHARM", listOf(ServitorRole.SYN, ServitorRole.RAV, ServitorRole.SEN), listOf(ServitorRole.MED), "Social confidence and adaptive conversation.", "Clear needless fear. Stay curious. Read feedback. Generate conversational options. Express yourself without forcing performance."),
        Paradigm("protect", "FORTRESS", listOf(ServitorRole.SEN, ServitorRole.SEN, ServitorRole.SYN), listOf(ServitorRole.SAB, ServitorRole.MED), "Heavy protection and ward reinforcement.", "Hold the boundary, maintain awareness, strengthen protection, disrupt unwanted influence, restore stability."),
        Paradigm("recover", "SANCTUARY", listOf(ServitorRole.MED, ServitorRole.SEN, ServitorRole.SYN), emptyList(), "Recovery from overload, fear, exhaustion, or ritual strain.", "Lower overload, stabilize, restore resources, reinforce useful identity, integrate lessons."),
        Paradigm("analysis", "ANALYST", listOf(ServitorRole.SAB, ServitorRole.RAV, ServitorRole.SEN), emptyList(), "Solve confusing or complex problems.", "Break the problem apart, expose assumptions, generate alternatives, observe consequences, choose from clarity."),
        Paradigm("dream_dojo", "DREAM DOJO", listOf(ServitorRole.SEN, ServitorRole.RAV, ServitorRole.COM), listOf(ServitorRole.SYN), "Lucid-dream or astral training formation.", "Recognize the state, stabilize lucidity, enter the dojo, summon the chosen teachers/servitors, train deliberately, return with recall."),
        Paradigm("phoenix", "PHOENIX DOWN", listOf(ServitorRole.MED, ServitorRole.SEN, ServitorRole.SYN), listOf(ServitorRole.SAB, ServitorRole.RAV, ServitorRole.COM), "Magician-defined resurrection contingency within the user's spiritual system.", "Protect the creator's identity/return anchors, restore and reunite according to the magician's covenant, complete the return operation, then reabsorb after success.")
    )

    val autoRules = listOf(
        AutoSummonRule("fight", "Physical fight / immediate serious threat", "battle", 5),
        AutoSummonRule("manipulation", "Manipulation, coercion, false urgency, or social pressure", "clear_mind", 3),
        AutoSummonRule("motivation", "Low motivation / procrastination loop", "motivate", 2),
        AutoSummonRule("social_nerves", "Nervous social interaction / talking to someone I like", "social", 1),
        AutoSummonRule("overload", "Overwhelm, fear spiral, exhaustion, or post-conflict recovery", "recover", 2),
        AutoSummonRule("hostile_influence", "Perceived hostile spiritual influence / unwanted presence", "protect", 4),
        AutoSummonRule("lucid", "Lucid-dream cue recognized", "dream_dojo", 2),
        AutoSummonRule("phoenix", "Magician-defined Phoenix condition", "phoenix", 5, false)
    )

    val commands = mapOf(
        "CORE" to listOf("SUMMON", "AWAKEN", "ACTIVATE", "SLEEP", "DISMISS", "RECALL", "REABSORB", "DISSOLVE", "SUSPEND", "RESUME", "RESET"),
        "ASSIGNMENT" to listOf("ASSIGN", "TARGET", "PROTECT", "OBSERVE", "FOLLOW", "GUARD", "ASSIST", "INVESTIGATE", "REPORT"),
        "COM" to listOf("EXECUTE", "PRESS", "COMMIT", "FINISH", "EXPLOIT", "BREAKTHROUGH"),
        "RAV" to listOf("BUILD", "CHAIN", "ACCELERATE", "EXPLORE", "OVERLOAD", "CREATE OPENING"),
        "SEN" to listOf("SHIELD", "GUARD", "INTERCEPT", "HOLD", "COUNTER", "FORTIFY", "WATCH"),
        "SAB" to listOf("SCAN", "EXPOSE", "DISCOURAGE", "CONFUSE", "DISRUPT", "WEAKEN", "SEVER", "DISPEL", "BREAK PATTERN", "REMOVE", "SILENCE"),
        "SYN" to listOf("AMPLIFY", "FORTIFY", "ACCELERATE", "FOCUS", "EMPOWER", "ALIGN", "REINFORCE", "INFLUENCE", "NEGOTIATE"),
        "MED" to listOf("RESTORE", "GROUND", "CLEANSE", "RECOVER", "STABILIZE", "REPAIR", "REINTEGRATE", "PHOENIX DOWN"),
        "SQUAD" to listOf("PARADIGM SHIFT", "FORMATION", "ALL SUMMON", "ALL RECALL", "FOCUS TARGET", "AUTOPILOT", "EMERGENCY RECALL", "DISSOLVE ALL"),
        "DEVELOPMENT" to listOf("LEARN", "ADAPT", "INTEGRATE", "ARCHIVE", "REPORT LESSON", "TRANSFER EXPERIENCE")
    )
}
