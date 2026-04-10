import re
with open("src/main/resources/templates/workspace.html", "r", encoding="utf-8") as f:
    text = f.read()

btn_str = '''<button type="button" class="btn btn-primary px-4 shadow-sm" id="btnSaveStep1" onclick="saveStep1AndContinue()">保存测评基础配置</button>'''
text = text.replace(btn_str, "")

with open("src/main/resources/templates/workspace.html", "w", encoding="utf-8") as f:
    f.write(text)
