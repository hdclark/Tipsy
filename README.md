# Tipsy

A sensor-driven Android racing toy where 10 deterministic hard-sphere balls race around a feature-rich loop.

## What it does

- Single custom canvas-style game surface.
- Stable 2D hard-sphere kinematics powered by JBox2D.
- 10 deterministic colored balls with slightly different radii.
- Tilt/rotation control from accelerometer gravity plus touch prods.
- Loop track includes rocks, chute pinch points, bumpy zig-zag bumps, and a horseshoe trap.
- Race ends when the first 3 balls complete 3 laps, shows a 5-second finish banner, then auto-resets.
- Collision and lead-change callouts (`oof!`, `ow!`, `1st!`).
- Leaderboard history is persisted across runs.

## Build

```bash
./gradlew :app:assembleDebug
```

GitHub Actions also builds and publishes a downloadable debug APK artifact (`tipsy-debug-apk`).
