import re
with open("src/main/resources/templates/workspace.html", "r", encoding="utf-8") as f:
    text = f.read()

target = '''                        <div class="analysis-card">
                            <div class="analysis-card-header">
                                <h6 class="mb-0 fw-bold text-dark"><i class="bi bi-calculator me-2"></i>分数与等级自动换算</h6>
                            </div>
                            <div class="p-4" x-show="assessments[subj.id]">'''

new_target = '''                        <div class="analysis-card" x-show="assessments[subj.id]?.scoreRule && assessments[subj.id]?.scoreRule?.mode !== 'NONE'">
                            <div class="analysis-card-header">
                                <h6 class="mb-0 fw-bold text-dark"><i class="bi bi-calculator me-2"></i>分数与等级自动换算</h6>
                            </div>
                            <div class="p-4" x-show="assessments[subj.id]">'''

# In case the exact formatting doesn't match perfectly, let's use a regex or just replace if found
if target in text:
    text = text.replace(target, new_target)
    with open("src/main/resources/templates/workspace.html", "w", encoding="utf-8") as f:
        f.write(text)
    print("SUCCESS")
else:
    print("NOT FOUND")
