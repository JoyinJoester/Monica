import os

file_path = r"c:\Users\joyins\Desktop\Monica-main\Monica for Android\app\src\main\java\takagi\ru\monica\utils\WebDavHelper.kt"

with open(file_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

# Keep 1-676 (indices 0-675)
part1 = lines[:676]

# Skip 677-1033 (indices 676-1033)
# Keep 1034-End (indices 1033 onwards)
part2 = lines[1033:]

new_content = part1 + part2

with open(file_path, 'w', encoding='utf-8') as f:
    f.writelines(new_content)

print(f"Fixed {file_path}. Original lines: {len(lines)}, New lines: {len(new_content)}")
