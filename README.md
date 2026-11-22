# VORTEX / VORTEX E

Native Android (Kotlin + Jetpack Compose), landscape only. One codebase, two apps:
- `operator` flavor = VORTEX (at the mixer)
- `controller` flavor = VORTEX E (in the crowd)

Build: open this folder in Android Studio, pick a build variant (operatorDebug / controllerDebug), run.

## Stages
1. Project structure, start screen, project list (rename, delete, Initial setup button)  (done)
2. Page 1 (messages, FX, vocal faders, spectrum with demo data, graphic EQ) and page 2 (vocal EQ knobs, Master A, FX1/FX2, Master B, band volumes), expandable panels  <- this zip (not compiled yet)
3. Two-phone Wi-Fi link (WebSocket server on operator, pairing code, offline queue)
4. Audio input from the 3.5 mm jack, FFT, spectrum, waterfall, sending FFT
5. EQ and fader requests with Accept / Applied flow, messages
6. Extras

## Not built yet
Initial setup screen (placeholder), Wi-Fi link between the phones, real audio and FFT (the spectrum is DEMO data), request marking and Request EQ flow.
Until stage 3 each phone only sees its own data.
Database uses destructive migration during development.
