import re
import sys

file_path = sys.argv[1]
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix package and imports with spaces
# e.g. "package com.vikify. app" -> "package com.vikify.app"
# "import androidx. compose" -> "import androidx.compose"

def fix_dot_space(match):
    return match.group(1) + '.' + match.group(2)

# Fix 'package com.vikify. app'
content = re.sub(r'(package\s+[\w\.]+)\.\s+(\w+)', fix_dot_space, content)
content = re.sub(r'(import\s+[\w\.]+)\.\s+(\w+)', fix_dot_space, content)
# Run twice to catch multiple spaces (e.g. a. b. c)
content = re.sub(r'(package\s+[\w\.]+)\.\s+(\w+)', fix_dot_space, content)
content = re.sub(r'(import\s+[\w\.]+)\.\s+(\w+)', fix_dot_space, content)

# Fix specific androidx imports that might be like "androidx. compose"
content = content.replace("androidx. compose", "androidx.compose")
content = content.replace("androidx. lifecycle", "androidx.lifecycle")
content = content.replace("androidx. navigation", "androidx.navigation")

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
