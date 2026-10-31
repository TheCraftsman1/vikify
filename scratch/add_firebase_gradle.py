import os

# 1. Update root build.gradle.kts
root_gradle = "build.gradle.kts"
with open(root_gradle, "r", encoding="utf-8") as f:
    content = f.read()

plugin_target = 'id("org.jetbrains.compose") version "1.10.3" apply false'
new_plugin = 'id("org.jetbrains.compose") version "1.10.3" apply false\n    id("com.google.gms.google-services") version "4.4.2" apply false'

if "com.google.gms.google-services" not in content:
    content = content.replace(plugin_target, new_plugin)
    with open(root_gradle, "w", encoding="utf-8") as f:
        f.write(content)
    print("Updated root build.gradle.kts with google-services plugin.")

# 2. Update app/build.gradle.kts
app_gradle = "app/build.gradle.kts"
with open(app_gradle, "r", encoding="utf-8") as f:
    app_content = f.read()

# Add plugin to app/build.gradle.kts
app_plugin_target = 'id("org.jetbrains.kotlin.plugin.serialization")'
app_plugin_new = 'id("org.jetbrains.kotlin.plugin.serialization")\n    id("com.google.gms.google-services")'

if 'id("com.google.gms.google-services")' not in app_content:
    app_content = app_content.replace(app_plugin_target, app_plugin_new)

# Add Firebase dependencies
deps_target = 'dependencies {'
firebase_deps = """dependencies {
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
"""

if "firebase-bom" not in app_content:
    app_content = app_content.replace(deps_target, firebase_deps, 1)

with open(app_gradle, "w", encoding="utf-8") as f:
    app_content = f.write(app_content)
print("Updated app/build.gradle.kts with Firebase plugins and dependencies.")
