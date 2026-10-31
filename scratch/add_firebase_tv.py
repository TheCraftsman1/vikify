with open('tv/build.gradle.kts', 'r', encoding='utf-8') as f:
    c = f.read()

target = 'dependencies {\n    implementation(project(":shared"))'
if target not in c:
    target = 'dependencies {\r\n    implementation(project(":shared"))'

replacement = '''dependencies {
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore") {
        exclude(group = "com.google.firebase", module = "protolite-well-known-types")
    }

    implementation(project(":shared"))'''

c = c.replace(target, replacement, 1)

with open('tv/build.gradle.kts', 'w', encoding='utf-8') as f:
    f.write(c)

print('Updated tv/build.gradle.kts with Firebase dependencies')
