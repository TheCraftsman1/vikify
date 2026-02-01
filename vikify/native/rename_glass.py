import sys

file_path = sys.argv[1]
class_name = sys.argv[2] # e.g. "DownloadGlass"

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Replace usage
content = content.replace("Glass.surface", f"{class_name}.surface")
content = content.replace("Glass.border", f"{class_name}.border")
# Replace definition
content = content.replace("object Glass", f"object {class_name}")

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
