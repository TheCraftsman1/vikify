import os
import shutil

ROOT = os.path.abspath(".")
EXCLUDES = {".git", "build", ".gradle", "node_modules", ".cxx", "dist"}

# 1. First, rename filenames
rename_files = [
    (
        os.path.join(ROOT, "app/src/main/java/com/music/bitchord/BitChordApplication.kt"),
        os.path.join(ROOT, "app/src/main/java/com/music/bitchord/VikifyApplication.kt")
    ),
    (
        os.path.join(ROOT, "sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/icons/BitChordIcons.kt"),
        os.path.join(ROOT, "sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/icons/VikifyIcons.kt")
    ),
]

for src, dst in rename_files:
    if os.path.exists(src):
        os.rename(src, dst)
        print(f"Renamed {src} -> {dst}")

# 2. Text replacements in all text files
replacements = [
    ("com.music.bitchord", "com.music.vikify"),
    ("com.dev.bitchord", "com.music.vikify"),
    ("BitChordApplication", "VikifyApplication"),
    ("BitChordIcons", "VikifyIcons"),
    ("BitChordTheme", "VikifyTheme"),
    ("BitChordTypography", "VikifyTypography"),
    ("Theme.BitChordWear", "Theme.VikifyWear"),
    ("Theme.BitChord", "Theme.Vikify"),
    ("BitChordWearTheme", "VikifyWearTheme"),
    ("bitchord_dsd", "vikify_dsd"),
    ("bitchord_dst", "vikify_dst"),
    ("Java_com_music_bitchord", "Java_com_music_vikify"),
    ("bitchordVersionCode", "vikifyVersionCode"),
    ("bitchordVersionName", "vikifyVersionName"),
    ("bitchordBetaSuffix", "vikifyBetaSuffix"),
    ("bitchord.bundleWear", "vikify.bundleWear"),
    ("BitChordWear.apk", "VikifyWear.apk"),
    ("BitChordWear.sha256", "VikifyWear.sha256"),
    ("bitchord-wear", "vikify-wear"),
    ("bitchord://", "vikify://"),
    ("saved_to_bitchord_folder", "saved_to_vikify_folder"),
    ("counted_on_device_with_bitchord", "counted_on_device_with_vikify"),
    ("bitchord_settings", "vikify_settings"),
    ("bitchord-listen-together", "vikify-listen-together"),
    ("bitchord_playback", "vikify_playback"),
    ("bitchord_download", "vikify_download"),
    ("bitchord_watch", "vikify_watch"),
    ("bitchord.kushagrasingh.in", "vikify.app"),
    ("/bitchord/", "/vikify/"),
    ("\"BitChord\"", "\"Vikify\""),
    ("'BitChord'", "'Vikify'"),
]

valid_exts = {
    ".kt", ".java", ".xml", ".kts", ".gradle", ".properties", ".pro",
    ".cpp", ".c", ".h", ".json", ".md", ".txt", ".yml", ".yaml", ".sh"
}

modified_count = 0
for dirpath, dirnames, filenames in os.walk(ROOT):
    dirnames[:] = [d for d in dirnames if d not in EXCLUDES]
    for fname in filenames:
        ext = os.path.splitext(fname)[1].lower()
        if ext in valid_exts or fname in ["CMakeLists.txt", "Dockerfile"]:
            fpath = os.path.join(dirpath, fname)
            try:
                with open(fpath, "r", encoding="utf-8", errors="ignore") as f:
                    content = f.read()
                new_content = content
                for old, new in replacements:
                    new_content = new_content.replace(old, new)
                if new_content != content:
                    with open(fpath, "w", encoding="utf-8") as f:
                        f.write(new_content)
                    modified_count += 1
            except Exception as e:
                print(f"Error on {fpath}: {e}")

print(f"Text replacement complete in {modified_count} files.")

# 3. Move bitchord directories to vikify
target_dirs = [
    "app/src/androidTest/java/com/music/bitchord",
    "app/src/debug/java/com/music/bitchord",
    "app/src/main/java/com/music/bitchord",
    "app/src/test/java/com/music/bitchord",
    "shared/src/commonMain/kotlin/com/music/bitchord",
    "shared/src/commonTest/kotlin/com/music/bitchord",
    "shared/src/jvmSharedMain/kotlin/com/music/bitchord",
    "shared/src/jvmTest/kotlin/com/music/bitchord",
    "sharedUi/src/androidMain/kotlin/com/music/bitchord",
    "sharedUi/src/jvmMain/kotlin/com/music/bitchord",
    "sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord",
    "sharedUi/src/jvmTest/kotlin/com/music/bitchord",
    "tv/src/main/java/com/music/bitchord",
    "tv/src/test/java/com/music/bitchord",
    "wearos/protocol/com/music/bitchord",
    "wearos/src/main/java/com/music/bitchord",
]

for td in target_dirs:
    full_path = os.path.join(ROOT, td)
    if os.path.exists(full_path):
        parent = os.path.dirname(full_path)
        dest_path = os.path.join(parent, "vikify")
        if os.path.exists(dest_path):
            # merge if exists
            for item in os.listdir(full_path):
                s = os.path.join(full_path, item)
                d = os.path.join(dest_path, item)
                if os.path.isdir(s):
                    shutil.copytree(s, d, dirs_exist_ok=True)
                else:
                    shutil.copy2(s, d)
            shutil.rmtree(full_path)
        else:
            os.rename(full_path, dest_path)
        print(f"Moved directory {full_path} -> {dest_path}")
