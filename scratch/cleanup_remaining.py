import os

ROOT = os.path.abspath(".")

files_to_update = [
    "app/src/main/AndroidManifest.xml",
    "tv/src/main/AndroidManifest.xml",
    "wearos/src/main/AndroidManifest.xml",
    "app/build.gradle.kts",
    "tv/build.gradle.kts",
    "wearos/build.gradle.kts",
    "app/proguard-rules.pro",
    "app/src/main/res/values/wear.xml",
    "wearos/src/main/res/values/wear.xml",
    "app/src/main/res/xml/backup_rules.xml",
    "app/src/main/res/xml/data_extraction_rules.xml",
    "app/src/main/java/androidx/media3/decoder/ffmpeg/FfmpegAudioDecoder.java",
    "app/src/main/java/androidx/media3/decoder/ffmpeg/FfmpegAudioRenderer.java",
    "app/src/main/java/androidx/media3/decoder/ffmpeg/FfmpegDecoderException.java",
    "app/src/main/java/androidx/media3/decoder/ffmpeg/FfmpegLibrary.java",
    "wearos/src/main/res/drawable/ic_notification.xml",
    "wearos/src/main/res/values/themes.xml",
    "README.md",
    "CONTRIBUTING.md",
    "CODE_OF_CONDUCT.md",
    "ADDITIONAL.md",
    "tv/README.md",
    "wearos/README.md"
]

for rel in files_to_update:
    path = os.path.join(ROOT, rel)
    if not os.path.exists(path):
        continue
    with open(path, "r", encoding="utf-8", errors="ignore") as f:
        c = f.read()
    c = c.replace("bitchord", "vikify")
    c = c.replace("BitChord", "Vikify")
    c = c.replace("BITCHORD", "VIKIFY")
    with open(path, "w", encoding="utf-8") as f:
        f.write(c)
    print(f"Updated {rel}")
