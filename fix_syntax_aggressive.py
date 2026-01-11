import re
import sys

file_path = sys.argv[1]
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix space after dot in general code
# e.g. "Modifier. animate" -> "Modifier.animate"
# e.g. "Values. 3f" -> "Values.3f"
def fix_dot_space(match):
    return match.group(1) + '.' + match.group(2)

content = re.sub(r'(\w+)\.\s+(\w+)', fix_dot_space, content)
content = re.sub(r'(\d)\.\s+(\d)', fix_dot_space, content)
content = re.sub(r'(\w+)\.\s+(\w+)', fix_dot_space, content) # run again for multi

# Fix specific androidx packages again just in case
content = content.replace(". geometry", ".geometry")

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
