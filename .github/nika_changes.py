# Nika: writes the list of changes for the screen "What is new".
# A commit message: the first line is the Ukrainian text, then optional lines "ru: ...", "en: ...", "app: smartkey".
import re
import subprocess
import sys

mode = sys.argv[1] if len(sys.argv) > 1 else "nika"
raw = subprocess.run(["git", "log", "-n", "150", "--pretty=format:%x1e%B"], capture_output=True, text=True).stdout
out = []
for rec in raw.split("\x1e"):
    lines = [l.strip() for l in rec.strip().splitlines() if l.strip()]
    if not lines:
        continue
    subject = lines[0]
    ru = ""
    en = ""
    app = ""
    for l in lines[1:]:
        low = l.lower()
        if low.startswith("ru:"):
            ru = l[3:].strip()
        elif low.startswith("en:"):
            en = l[3:].strip()
        elif low.startswith("app:"):
            app = l[4:].strip().lower()
    if mode == "smartkey" and app != "smartkey":
        continue
    if mode != "smartkey" and app == "smartkey":
        continue
    uk = ""
    if re.search("[\u0456\u0457\u0454\u0491\u0406\u0407\u0404\u0490]", subject):
        uk = subject
    elif re.search("[\u0400-\u04FF]", subject):
        if not ru:
            ru = subject
    if not (uk or ru or en):
        continue
    entry = "\t".join([uk, ru, en])
    if entry not in out:
        out.append(entry)
    if len(out) >= 25:
        break
with open("talkback/src/main/assets/nika_changes.txt", "w", encoding="utf-8") as f:
    f.write("\n".join(out) + "\n")
