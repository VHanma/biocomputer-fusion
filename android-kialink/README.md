# KIA Link

An offline Android chaos-magick sigil and archetypal embodiment app built around a two-photo ritual link.

## Core design

- **Two-photo pair binding:** the selected self image and target image are each SHA-256 hashed. Their hashes, target type, intent, and reduced sigil letters are hashed again into one deterministic **Pair Seal**. The same inputs reproduce the same sigil seed.
- **Spare / chaos sigil layer:** the intent is reduced by eliminating repeated letters. Optional vowel deletion is provided as a later/common compression variant rather than presented as a universal rule.
- **Magical-link layer:** the photographs function as symbolic representations under the familiar sympathetic-magic / magical-link model. The cryptographic pair binding is the app's literal, testable digital link.
- **Gnosis modes:** inhibitory fixation, excitatory embodiment, hybrid, and pre-sleep visualization.
- **Invocation / metamorphosis:** the user chooses traits rather than simply asking to become an entire target identity. The session rehearses posture, gaze, tempo, decisions, emotion, and first-person imagery.
- **Release / banishing:** charging ends with deliberate attentional disengagement, reflecting the chaos-magick emphasis on dropping conscious fixation on the result.
- **Integration lock:** a concrete if-then action is written after the ritual and saved with the seal in a local journal.

## Research basis

The workflow is primarily informed by Austin Osman Spare's sigil practice and the chaos-magick line developed by Peter J. Carroll (*Liber Null & Psychonaut*, *Liber Kaos*), Phil Hine (*Condensed Chaos*), and Grant Morrison's *Pop Magic*. The photo-link concept is also comparable to the older principles of similarity/contact discussed by J. G. Frazer in *The Golden Bough*. The behavioral integration layer borrows from implementation-intention research associated with Peter Gollwitzer and from mental-imagery research in performance psychology.

`Liber Null` explicitly describes sigil work as construction, loss from ordinary conscious attention, and charging; it also presents word, pictorial, and mantrical construction methods. The app translates that structure into an Android workflow rather than inventing an unrelated "manifestation" mechanic.

## Quantum wording

KIA Link can use **quantum entanglement as a ritual metaphor and visualization**. It does not claim that a phone, two JPEGs, or a sigil creates literal physical quantum entanglement between people. The measurable linkage implemented here is deterministic cryptographic pair-binding.

## Privacy

No internet permission. Photos remain on-device and are read through Android's document picker. The app stores only session text and seal codes in its private SharedPreferences journal.

## Build

The repository workflow builds an installable debug-signed APK for Android 8+ and targets Android API 36.
