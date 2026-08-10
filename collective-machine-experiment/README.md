# Collective Machine Experiment V2

Android experiment for building an evolving collective model from repeated human signals.

## Input channels

- Voice: 16 kHz mono PCM, 20 ms Hamming frames, 5 ms hop, 512-point FFT, 24 Bark bands
- Tap rhythm
- Reaction timing
- Typed response patterns
- Accelerometer motion
- Drawing paths
- Recursive feedback responses

All channels are compressed to 50-value local feature vectors.

## Collective architecture

1. Give participants the same session code and round number.
2. Gather one or more modalities from each person.
3. Earlier person + modality observations become personal baselines.
4. The current round is expressed as change relative to those baselines.
5. Participant residual vectors combine into a collective latent core.
6. Measure coherence, dispersion, novelty, stability, and participant influence.
7. Compare synchronized rounds with shuffled controls.
8. Compare pooled collective prediction with participant-only prediction.
9. Convert the current core into a generated visual glyph and short tone pattern.
10. Record participant responses, advance the round, and repeat the loop.

## Capsules

V2 capsules preserve participant, modality, session, round, tag, timestamp, and feature vectors. V1 capsules can also be imported.

Raw microphone audio is discarded after feature extraction. The app has no Internet permission.
