# 🚀 ThumbGen AI

> **High-CTR YouTube Thumbnail Generator for Android**  
> Native Kotlin & Jetpack Compose app powered by Open-Source AI (Cloudflare Workers AI, NVIDIA NIM, Pollinations FLUX) and Google ML Kit Subject Segmentation.

[![Release](https://img.shields.io/github/v/release/rawatriya7514-lab/ThumbGenAI?style=for-the-badge&color=F04444)](https://github.com/rawatriya7514-lab/ThumbGenAI/releases/latest)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-minSdk%2026%20|%20targetSdk%2035-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)

---

## 📱 Features

- **🎯 AI Prompt & Hook Strategy:**
  - Automated strategy generator connecting to OpenRouter (`meta-llama/llama-3.3-70b-instruct:free`) or Groq (`llama-3.3-70b-versatile`).
  - Strict JSON enforcement: generates 8k photorealistic prompts (with strictly **zero** text in the image) + 2–4 word high-curiosity text hooks.
  - Built-in heuristic engine for full offline operation.

- **⚡ Multi-Provider FLUX Image Generation with Automatic Fallback:**
  1. **Primary:** Cloudflare Workers AI (`@cf/black-forest-labs/flux-1-schnell`)
  2. **Fallback 1:** NVIDIA NIM (`FLUX.1-dev`, 25 steps, custom resolutions)
  3. **Fallback 2:** Pollinations.ai FLUX (open-access free tier)
  4. **Offline Mode:** Procedural studio background engine.

- **✂️ Offline AI Subject Cutout (Google ML Kit):**
  - Pick any creator or product reference image from your gallery.
  - On-device background removal via Google ML Kit Subject Segmentation (`16.0.0-beta1`).
  - Automatically composites the cutout on the right third with a subtle rim-glow highlight.

- **🎨 Live Dual-Layer Text Hook Editor:**
  - Dynamic dual-pass canvas rendering: thick black outer stroke + vibrant yellow (`#FFE600`) / white fill + soft drop shadow.
  - Real-time live text hook editing and color toggle without regenerating the background.

- **💾 Direct Android Gallery Export:**
  - Direct lossless PNG export to `Pictures/ThumbGen` via the Android `MediaStore` API.

---

## 📥 Download APK

Get the latest release directly from GitHub Releases:

👉 [**Download ThumbGenAI-v1.0.0.apk**](https://github.com/rawatriya7514-lab/ThumbGenAI/releases/latest)

---

## 🛠️ Tech Stack & Architecture

- **Language:** Kotlin 2.0.0
- **UI Framework:** Jetpack Compose (Material 3)
- **Networking:** OkHttp 4.12.0
- **Asynchronous Execution:** Kotlin Coroutines (`1.8.1`)
- **Storage & State:** Jetpack DataStore Preferences (`1.1.1`)
- **Computer Vision:** Google ML Kit Subject Segmentation (`16.0.0-beta1`)
- **Image Pipeline:** Coil (`2.6.0`) & Android Hardware Canvas

---

## 🏗️ Build from Source

```bash
git clone https://github.com/rawatriya7514-lab/ThumbGenAI.git
cd ThumbGenAI
./gradlew assembleDebug
```
The output APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

---

## 📄 License
Apache License 2.0. Created by [artraw](https://github.com/rawatriya7514-lab).
