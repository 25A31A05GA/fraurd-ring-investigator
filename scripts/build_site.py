#!/usr/bin/env python3
"""Render the Markdown docs to static HTML pages inside docs/ (run before committing).
Needs: pip install markdown.   Output is plain HTML, so Vercel / GitHub Pages need no build step."""
import re, pathlib, markdown
D = pathlib.Path(__file__).resolve().parent.parent / "docs"
PAGES = {"REPORT.md": "report.html", "ARCHITECTURE.md": "architecture.html", "API.md": "api.html",
         "RECRUITER_KIT.md": "recruiter-kit.html"}
for p in sorted((D / "case-studies").glob("*.md")):
    PAGES["case-studies/" + p.name] = "case-studies/" + ("index.html" if p.name == "README.md" else p.stem + ".html")
CSS = """:root{--bg:#04070d;--card:#0a1322;--bd:#17304a;--pri:#35e0ff;--tx:#dbe9ff;--mut:#7d93b3;color-scheme:dark}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--tx);font:16px/1.65 system-ui,Segoe UI,sans-serif}
header{border-bottom:1px solid var(--bd);padding:12px 20px;display:flex;gap:18px;flex-wrap:wrap;background:#070d18}
header a{color:var(--mut);text-decoration:none;font-size:14px}header a:hover,header a.b{color:var(--pri)}
main{max-width:860px;margin:0 auto;padding:28px 20px 80px}h1,h2,h3{line-height:1.25}h1{color:var(--pri)}h2{border-bottom:1px solid var(--bd);padding-bottom:6px;margin-top:2em}
a{color:var(--pri)}code{background:#0b1a2e;padding:1px 6px;border-radius:4px;font-size:.9em}pre{background:#08111f;border:1px solid var(--bd);padding:14px;overflow:auto;border-radius:6px}pre code{background:none;padding:0}
table{border-collapse:collapse;display:block;overflow-x:auto}th,td{border:1px solid var(--bd);padding:7px 10px;text-align:left;vertical-align:top}th{background:#0b1a2e}
blockquote{border-left:3px solid var(--pri);margin:1em 0;padding:2px 16px;background:#08111f;color:var(--mut)}img{max-width:100%}"""
def fix_links(html):
    def sub(m):
        u = m.group(1)
        if re.match(r"(https?:|#|mailto:)", u): return m.group(0)
        u = re.sub(r"^(\.\./)?(\./)?docs/", "", u)
        u = {"REPORT.md": "report.html", "ARCHITECTURE.md": "architecture.html", "API.md": "api.html",
             "RECRUITER_KIT.md": "recruiter-kit.html"}.get(u.split("#")[0], u) + (("#" + u.split("#")[1]) if "#" in u else "")
        u = re.sub(r"\.md(#|$)", r".html\1", u)
        u = re.sub(r"(^|/)(REPORT|ARCHITECTURE|API|RECRUITER_KIT)\.html", lambda m: m.group(1) + {"REPORT":"report","ARCHITECTURE":"architecture","API":"api","RECRUITER_KIT":"recruiter-kit"}[m.group(2)] + ".html", u)
        u = re.sub(r"(^|/)README\.html", r"\1index.html", u)
        return 'href="%s"' % u
    return re.sub(r'href="([^"]+)"', sub, html)
for src, out in PAGES.items():
    md = (D / src).read_text(encoding="utf-8")
    body = markdown.markdown(md, extensions=["tables", "fenced_code"])
    depth = out.count("/")
    root = "../" * depth
    body = fix_links(body)
    if depth:  # case-study pages: links between siblings stay relative, links to top docs go up one level
        body = re.sub(r'href="(report|architecture|api|recruiter-kit)\.html', r'href="../\1.html', body)
    title = re.search(r"^#\s+(.+)$", md, re.M)
    html = f"""<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>{re.sub('<[^>]+>', '', title.group(1)) if title else 'Docs'}</title><style>{CSS}</style></head><body>
<header><a class="b" href="{root}./">⌂ Home</a><a href="{root}dashboard.html">Live dashboard</a><a href="{root}report.html">Report</a><a href="{root}case-studies/">Case studies</a><a href="{root}architecture.html">Architecture</a><a href="{root}api.html">API</a><a href="{root}Fraud_Ring_Investigator.pptx">Slides</a></header>
<main>{body}</main></body></html>"""
    (D / out).parent.mkdir(parents=True, exist_ok=True)
    (D / out).write_text(html, encoding="utf-8")
    print("wrote docs/" + out)
