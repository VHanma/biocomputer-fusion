# Collective Machine Experiment

Android APK experiment inspired by the signal-to-model workflow discussed in the KIDS System sequence.

## What it does

- Collects labeled 3-second voice samples from multiple participants.
- Includes a 64-name prompt bank and random 5-name rounds.
- Processes audio locally at 16 kHz.
- Uses 20 ms frames, 5 ms hops, a Hamming window, FFT power, and 24 Bark-scale bands.
- Discards raw audio after extracting a 50-value feature vector.
- Builds transparent nearest-prototype acoustic models.
- Runs 1/3 held-out testing and 2/3 training.
- Tests an original model on later unseen data without retraining.
- Runs a 4-stage incremental-retraining experiment.
- Shows a live `INNER VIEW` network where human signals converge on the current prototype core.
- Exports/imports feature-only JSON capsules so datasets from several phones can be merged.
- Uses no network permission and makes no scientific or paranormal claims.
