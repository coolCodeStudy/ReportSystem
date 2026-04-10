with open("src/main/resources/templates/workspace.html", "r", encoding="utf-8") as f:
    text = f.read()

target = '''        window.saveWorkspaceData = function() {
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

replacement = '''        window.saveWorkspaceData = function() {
            const btn = document.querySelector('.bottom-action-bar .btn-primary');
            const origHtml = btn ? btn.innerHTML : '';
            if (btn) {
                btn.disabled = true;
                btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>保存中...';
            }
            
            let promises = [];
            if (globalWorkspaceManager) promises.push(globalWorkspaceManager.saveWorkspaceData());
            if (globalWorkspaceStep3Manager) promises.push(globalWorkspaceStep3Manager.saveStep3(true));
            if (typeof saveStep1DataSilent === 'function') promises.push(saveStep1DataSilent());
            
            return Promise.all(promises).finally(() => {
                if (btn) {
                    btn.disabled = false;
                    btn.innerHTML = origHtml;
                }
            });
        };'''

text = text.replace(target, replacement)
with open("src/main/resources/templates/workspace.html", "w", encoding="utf-8") as f:
    f.write(text)

