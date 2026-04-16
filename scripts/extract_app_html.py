import re
import pathlib

root = pathlib.Path(__file__).resolve().parents[1]
main_java = root / "src" / "main" / "java" / "Main.java"
s = main_java.read_text(encoding="utf-8")
m = re.search(r'String html = """(.*?)"""', s, re.DOTALL)
if not m:
    raise SystemExit("text block not found")
html = m.group(1)
lines = html.splitlines(True)
# common leading space strip (only for non-empty lines)
non_empty = [ln for ln in lines if ln.strip()]
if non_empty:
    mins = min(len(ln) - len(ln.lstrip(" ")) for ln in non_empty)
    lines = [ln[mins:] if ln.strip() else ln for ln in lines]
out = root / "gateway-service" / "src" / "main" / "resources" / "static" / "app.html"
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text("".join(lines), encoding="utf-8")
print("wrote", out)
