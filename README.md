# Retired-Sentinel (Native Android)

Give your old Android phone a new career in private security.

Retired-Sentinel is a fully native Android application that turns any spare smartphone into a smart security camera. It detects intruders, recognizes known faces, and sends you video evidence via Telegram when something's amiss.

Unlike most modern smart cameras, this app is **100% free, open-source, and cloud-free**. All Machine Learning inference happens locally on the device's hardware.

## 🎬 Demo

| Intruder Detected | Safe Identity Verified |
|:---:|:---:|
| ![Intruder detected](intruder.gif) | ![Safe ID verified](secure_id.gif) |

*Left: The system detects an unrecognized person and logs an `!!! Intruder Alert !!!` which triggers an MP4 video upload to Telegram. Right: The system identifies an authorized user and suppresses the alarm.*

## ✨ Key Features

- **100% On-Device Processing**: No cloud subscriptions, no data harvesting. All video and facial data stays on your phone.
- **Advanced ML Pipeline**: Uses CameraX, YOLO26 for fast person detection, Google ML Kit for face cropping, and FaceNet for high-accuracy identity matching.
- **Smart Security Logic**: Configurable grace periods, incident timeouts, and safe-identity thresholds virtually eliminate false alarms.
- **Automated Telegram Alerts**: Instantly encodes a rolling buffer of frames into an MP4 and sends the video evidence directly to your Telegram chat.
- **Thermal & Battery Protection**: Fully configurable camera resolution and FPS limiters to keep older devices cool during 24/7 operation.
- **Easy Enrollment**: Add safe identities directly in the app by uploading photos straight from your gallery.

## 📦 Installation

This project is now a standalone Android app (no Termux or Python required!).

1. Go to the [Releases](../../releases) tab on the right side of this repository.
2. Download the latest `app-release.apk` directly to your Android device.
3. Open the file to install it. *(You may need to allow "Install from Unknown Sources" in your Android settings).*
4. Grant the app **Camera** and **Storage** permissions when prompted (Storage is required to save the MP4 video evidence locally).

## 🚀 Setup Guide

### 1. Configure Telegram Alerts (Optional but recommended)
To receive video alerts, you need to connect the app to a Telegram Bot:
1. Open the Telegram app and search for `@BotFather`.
2. Send `/newbot` and follow the prompts to get your **Bot API Token**.
3. Start a chat with your new bot.
4. Search for `@userinfobot` to get your personal **Chat ID**.
5. Open the Retired-Sentinel app, go to Settings, and paste your Token and Chat ID.

### 2. Enroll Safe Identities
To prevent the alarm from triggering on yourself or your family:
1. Navigate to the **Enroll** tab in the app.
2. Tap the `+` button, type a name (e.g., "Alice"), and select a clear portrait photo of them.
3. The app will generate a secure numerical vector (embedding) of the face. You can add multiple photos of the same person (different lighting/angles) to improve accuracy!

### 3. Adjust Security Rules
In the **Settings** tab, configure the app for your specific environment:
- **Grace Period:** How long an unknown person can be in the room before the alarm fires.
- **Incident Timeout:** How long the room must be completely empty before the system resets.
- **Person Detection Confidence Threshold:** Minimum confidence for YOLO to consider a person detected.
- **Identity Match Threshold:** How closely a detected face must match an enrolled identity to be considered "safe".
### 4. Arm the Camera
Prop the phone up, plug it into power, and let it run. The screen will show the live feed, current FPS, thermal headroom, and bounding boxes.

## ⚙️ Architecture & Pipeline

**The ML Pipeline:**
`CameraX Stream` → `YOLOv8 (Person Detection)` → `Google ML Kit (Face Crop)` → `FaceNet (Embedding Match)` → `Security State Machine`

**The Rolling Buffer:**
The app maintains a continuous memory buffer of frames based on your Grace Period. When an alert triggers, it takes those exact frames, hands them to `JCodec` to encode a local `.mp4` file, and uses `OkHttp` to post the video to Telegram. This ensures your alert video actually shows the intruder walking into the frame, rather than starting *after* the alarm triggered.

## 🔒 Security & Privacy

This app is built with extreme privacy in mind.

- **No Inbound Traffic:** The app does not open any local web servers or ports. It cannot be "hacked into" from the outside.
- **Only Necessary Outbound Traffic:** The only time the app communicates with the internet is to execute a secure HTTPS POST to Telegram's official API when an alert triggers.
- **Irreversible Biometrics:** Face embeddings are stored in a local, plain-text JSON file securely in the app's isolated sandbox. Embeddings are mathematically irreversible—meaning no one can reconstruct an image of your face from the data. The original photos you used for enrollment are never saved or uploaded.

## 🛠️ Troubleshooting & Tips

- **Overheating / App Crashing:** Running AI continuously is heavy on mobile processors. Go to Settings and lower the **Target FPS** (e.g., to 2 or 3 FPS).
- **Faces aren't being recognized:** If the camera is placed far away from the subject, the face crops may be too pixelated. Go to settings and increase the **Camera Resolution** to 720p or 1080p to give the FaceNet model more data to work with.
- **False Alarms:** Increase the "Person detection confidence threshold" if the app is mistaking shadows/furniture for people, or increase the "Identity match threshold" if it is mistaking an intruder for a safe identity.

## 📜 License
This project is completely free and open-source under the MIT License.

## 🙏 Acknowledgments
- [Ultralytics](https://ultralytics.com/) for YOLOv8
- [Facenet-Pytorch](https://github.com/timesler/facenet-pytorch) (Original models adapted for TFLite)
- [Google ML Kit](https://developers.google.com/ml-kit)
- [JCodec](http://jcodec.org/) for pure-Java video encoding
