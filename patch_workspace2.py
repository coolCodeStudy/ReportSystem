import re

with open('src/main/resources/templates/workspace.html', 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Remove the rule that hides bottom-action-bar on step 3
replace_tab = '''            const actionBar = document.querySelector('.bottom-action-bar');
            if (actionBar) {
                if (step === 'step3') {
                    actionBar.style.setProperty('display', 'none', 'important');
                } else {
                    actionBar.style.setProperty('display', 'flex', 'important');
                }
            }'''
new_tab = '''            const actionBar = document.querySelector('.bottom-action-bar');
            if (actionBar) {
                actionBar.style.setProperty('display', 'flex', 'important');
            }'''
text = text.replace(replace_tab, new_tab)

# 2. Add globalWorkspaceStep3Manager variable
text = text.replace('let globalWorkspaceManager = null;', 'let globalWorkspaceManager = null;\n        let globalWorkspaceStep3Manager = null;')

# 3. Add to init
text = text.replace('async init() {', 'async init() {\n                    globalWorkspaceStep3Manager = this;')

# 4. Modify window.saveWorkspaceData
replace_save_window = '''        // Helper override for save button in action bar
        window.saveWorkspaceData = function() {
            if (globalWorkspaceManager) return globalWorkspaceManager.saveWorkspaceData();
            return Promise.resolve();
        };'''
new_save_window = '''        // Helper override for save button in action bar
        window.saveWorkspaceData = function() {
            const btn = document.querySelector('.bottom-action-bar .btn-primary');
            const origHtml = btn ? btn.innerHTML : '';
            if (btn) {
                btn.disabled = true;
                btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>保存中...';
            }
            
            let p1 = globalWorkspaceManager ? globalWorkspaceManager.saveWorkspaceData() : Promise.resolve();
            let p2 = globalWorkspaceStep3Manager ? globalWorkspaceStep3Manager.saveStep3(true) : Promise.resolve();
            return Promise.all([p1, p2]).finally(() => {
                if (btn) {
                    btn.disabled = false;
                    btn.innerHTML = origHtml;
                }
            });
        };'''
text = text.replace(replace_save_window, new_save_window)

# 5. Modify saveStep3 signature to accept silent
text = text.replace('async saveStep3() {', 'async saveStep3(silent = false) {')
text = text.replace('this.alertMsg = \'\';', 'if(!silent) this.alertMsg = \'\';')
text = text.replace('this.alertMsg = \'教学安排内容已保存成功！\';', 'if(!silent) { this.alertMsg = \'教学安排内容已保存成功！\'; }\n                            // also trigger global indicator since we unified it\n                            const ind = document.getElementById("saveSuccessIndicator");\n                            if(ind) { ind.classList.remove("d-none"); setTimeout(() => ind.classList.add("d-none"), 3000); }')

# 6. Remove the standalone "保存此页" button
replace_btn = '''                <button class="btn btn-primary" @click="saveStep3" :disabled="saving">
                    <i class="bi bi-save me-1"></i> <span x-text="saving ? '保存中...' : '保存此页'"></span>
                </button>'''
text = text.replace(replace_btn, '')

with open('src/main/resources/templates/workspace.html', 'w', encoding='utf-8') as f:
    f.write(text)

print("Patch successful!")
