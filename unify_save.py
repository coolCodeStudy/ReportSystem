import re

with open('src/main/resources/templates/workspace.html', 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Remove the "保存测评基础配置" button from step 1
btn_block = '''                        <div class="col-12 mt-4 text-end">
                            <button type="button" class="btn btn-primary px-4 shadow-sm" id="btnSaveStep1" onclick="saveStep1AndContinue()">保存测评基础配置</button>
                        </div>'''
text = text.replace(btn_block, '')

# 2. Extract the saveStep1 logic into a function that returns a Promise
old_save_step1 = '''        function saveStep1AndContinue() {
            const btn = document.getElementById('btnSaveStep1');
            btn.disabled = true;
            btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>保存中...';

            // Find selected radio
            let selectedType = '';
            document.querySelectorAll('.step1-type-radio').forEach(r => {
                if (r.checked) selectedType = r.value;
            });

            const payload = {
                lingolandLevel: document.getElementById('step1_level').value,
                assessmentDate: document.getElementById('step1_date').value,
                assessmentType: selectedType,
                selectedExportColumns: document.getElementById('step1_selectedExportColumns').value
            };

            fetch(`/api/assessment/${recordId}/step1`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            })
            .then(res => res.json()).then(_j => _j.code === 200 ? _j.data : (_j.data !== undefined ? _j.data : _j))
            .then(data => {
                btn.disabled = false;
                btn.innerHTML = '保存并进入下一步 <i class="bi bi-arrow-right ms-1"></i>';
                if (data.success) {
                    // Force UI transition to step 2 after save
                    switchTab('step2');
                } else {
                    Swal.fire('提示', '保存失败。', 'error');
                }
            })
            .catch(err => {
                btn.disabled = false;
                btn.innerHTML = '保存并进入下一步 <i class="bi bi-arrow-right ms-1"></i>';
                Swal.fire('提示', '网络错误，保存失败。', 'error');
            });
        }'''

new_save_step1 = '''        function saveStep1DataSilent() {
            const recordId = document.getElementById('recordIdSpan').innerText;
            if (!recordId || recordId === '0') return Promise.resolve();
            let selectedType = '';
            document.querySelectorAll('.step1-type-radio').forEach(r => {
                if (r.checked) selectedType = r.value;
            });
            const payload = {
                lingolandLevel: document.getElementById('step1_level') ? document.getElementById('step1_level').value : '',
                assessmentDate: document.getElementById('step1_date') ? document.getElementById('step1_date').value : '',
                assessmentType: selectedType,
                selectedExportColumns: document.getElementById('step1_selectedExportColumns') ? document.getElementById('step1_selectedExportColumns').value : ''
            };
            return fetch(`/api/assessment/${recordId}/step1`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            }).then(res => res.json());
        }'''

text = text.replace(old_save_step1, new_save_step1)

# Wait! The original old_save_step1 had `fetch(`/api/assessment/${recordId}/step1`... 
# But in my string, did I match it exactly? Wait, `const recordId = ...` wasn't inside saveStep1AndContinue in original?
# Let's check if it replaces properly!
