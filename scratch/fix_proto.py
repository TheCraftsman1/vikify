with open('app/build.gradle.kts', 'r', encoding='utf-8') as f:
    c = f.read()

target = '    implementation("com.google.firebase:firebase-firestore")'
replacement = '''    implementation("com.google.firebase:firebase-firestore") {
        exclude(group = "com.google.firebase", module = "protolite-well-known-types")
    }'''

c = c.replace(target, replacement, 1)

with open('app/build.gradle.kts', 'w', encoding='utf-8') as f:
    f.write(c)

print('Excluded protolite-well-known-types successfully')
