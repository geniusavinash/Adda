# Adda — Offline AI Study Companion

**One phone becomes the AI server for the whole group. Fully offline.**

Adda turns a single Android phone into an on-device AI tutor **and** a local web
server. Nearby students (other phones or a laptop) connect over **Wi-Fi Direct /
local Wi-Fi — no internet** — and ask doubts in **Hinglish**. The host phone runs
an on-device LLM and answers everyone. Nothing leaves the device.

> Built for the **iQOO Hackathon 2026 — Open Innovation** track.

---

## Why

Classrooms and study groups in low-connectivity settings can't rely on cloud AI —
it needs internet, costs data, and raises privacy concerns. Adda removes all three:

- **Zero internet** — runs fully in airplane mode once the model is on the device.
- **Shared from one device** — only the host needs the model; everyone else just connects.
- **Private** — questions and answers never leave the local network.

---

## What it does

- 🧠 **On-device LLM** (Gemma 3n E4B) answers questions — text, **images**, and **PDFs**.
- 🌐 **Host one phone**, others **join via QR / code** over Wi-Fi Direct or shared Wi-Fi.
- 💬 **Hinglish answers**, short and simple; formulas & code in fenced blocks.
- 🧵 **Multi-turn memory per student**, with isolated threads (one student's context never bleeds into another's).
- 🖼️ **Image & PDF input** — the model reads photos/notes/worksheets directly (multimodal vision); PDFs are rendered and read page-wise.
- 🎤 **Voice input** (speech-to-text) and 🔊 **read-aloud** (text-to-speech with stop).
- 📋 **Copy / Share** any answer.
- 📤 **Export the session** to **PDF / Word (.docx) / Excel (.xlsx)** — generated on-device, shared via the system sheet.
- 🎨 Clean **dark + amber Material 3** UI.

---

## How it works

```
        HOST PHONE (has the model)                     CLIENTS (no model)
 ┌─────────────────────────────────────┐        ┌───────────────────────────┐
 │  Gemma 3n .task  ──►  MediaPipe LLM  │        │  Phone / Laptop browser   │
 │                        Inference      │        │                           │
 │                          ▲            │  HTTP  │   "isme kya likha hai?"   │
 │   Embedded Ktor server  ─┘            │◄──────►│   + optional image/PDF    │
 │   (HTTP + WebSocket, :8080)           │  + WS  │                           │
 │   Wi-Fi Direct group owner            │        │   streamed answer ◄────   │
 │   (192.168.49.1)                      │        │                           │
 └─────────────────────────────────────┘        └───────────────────────────┘
```

- **HOST mode** — loads the Gemma `.task` from the app's files dir, starts an embedded
  Ktor server, and forms a Wi-Fi Direct group. All answers are produced on-device.
- **CLIENT mode** — connects to the host URL, sends questions (and base64 images) over
  HTTP, and streams answers back. A live feed updates over WebSocket.

---

## Tech stack

| Area | Technology |
|------|------------|
| Language / UI | Kotlin, Jetpack Compose, Material 3 (dark) |
| On-device AI | MediaPipe Tasks **GenAI** (LLM Inference) + Tasks **Vision** — Gemma 3n E4B int4 `.task` |
| Server | **Ktor** (CIO) embedded HTTP server + WebSockets, kotlinx.serialization |
| Networking | **Wi-Fi Direct** (WifiP2pManager), local sockets |
| Join / scan | CameraX + ML Kit barcode, ZXing (QR generate/scan) |
| Voice | Android `SpeechRecognizer` (STT), `TextToSpeech` (TTS) |
| Documents | Android `PdfDocument` (PDF); hand-built OOXML for `.docx` / `.xlsx` (no heavy library) |
| Build | Gradle (AGP 8.7.3), Kotlin 2.0.21, minSdk 26, compileSdk 35 |

---

## Setup

### Prerequisites
- Android Studio (or command-line Android SDK, API 35) and **JDK 21**
- An Android phone with **8 GB+ RAM** (it loads a ~4.4 GB model) and a recent GPU
- A Hugging Face account with access to **Gemma 3n** (to download the model)

### 1. Get the model (not in the repo — ~4.4 GB)
```bash
# set a Hugging Face token that has Gemma access
export HF_TOKEN=hf_xxx           # Windows PowerShell: $env:HF_TOKEN="hf_xxx"
pip install huggingface_hub
python dl_model.py               # downloads into ./model/gemma-3n-E4B-it-int4.task
```

### 2. Put the model on the device
The app loads `gemma.task` from its private files dir.
```bash
adb push model/gemma-3n-E4B-it-int4.task /data/local/tmp/gemma.task
adb shell run-as co.mobilise.adda cp /data/local/tmp/gemma.task files/gemma.task
adb shell rm /data/local/tmp/gemma.task
```

### 3. Build & install
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 4. Run
- **Host phone:** open the app → **Host an Adda** → allow nearby-devices permission →
  the server starts and shows a QR code + join URL.
- **Other phones:** **Join an Adda** → scan the QR (or enter the code) → ask away.
- **Laptop:** open the host URL in a browser on the same Wi-Fi.

> Put the host in **airplane mode** (Wi-Fi Direct still works) to prove it's fully offline.

---

## Project structure

```
app/src/main/java/co/mobilise/adda/
├── llm/        Gemma model wrapper (LlmService), multimodal + multi-turn, demo cache
├── server/     Ktor server, routes, DTOs, per-student session store, admin console
├── client/     HostClient — CLIENT-mode HTTP/stream calls
├── wifi/        Wi-Fi Direct group + join codes
├── ui/          Compose screens, dark+amber theme, chat bubbles, Q&A
├── export/      Session export to PDF / Word / Excel (offline)
├── tts/, voice/ Text-to-speech and speech-to-text
└── util/        Image/PDF/file decoding, connectivity helpers
dl_model.py      One-shot model downloader (Hugging Face)
```

---

## Offline guarantee

Every answer is computed **on the device** by the Gemma model. The app makes **no
cloud AI calls** — there is no API key, no remote inference, nothing. After the model
file is on the phone, the entire experience works with the radio off.
