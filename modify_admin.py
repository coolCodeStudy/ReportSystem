import re

with open("src/main/resources/templates/admin-templates.html", "r") as f:
    content = f.read()

# 1. Replace HTML Body (Lines 66-152)
html_start = content.find('<!-- Global Configs Row -->')
html_end = content.find('<!-- Add Type Modal -->')

new_html = """                <div class="row" style="height: calc(100vh - 120px);">
                    <!-- Left: Navigation & Student Types List -->
                    <div class="col-md-3 h-100">
                        <div class="card shadow-sm border-0 h-100 d-flex flex-column">
                            <div class="list-group list-group-flush flex-grow-1 overflow-auto" id="mainSidebarList">
                                <!-- Global Tabs -->
                                <div class="p-3 bg-light border-bottom text-muted small fw-bold">核心配置</div>
                                <div class="list-group-item list-group-item-action p-3 type-list-item active" id="menu-global" onclick="selectMainMenu('global')">
                                    <h6 class="mb-0 fw-bold"><i class="bi bi-grid-3x3-gap-fill text-primary me-2"></i>全局矩阵与基础标化</h6>
                                </div>
                                <div class="list-group-item list-group-item-action p-3 type-list-item" id="menu-desc" onclick="selectMainMenu('desc')">
                                    <h6 class="mb-0 fw-bold"><i class="bi bi-file-earmark-text-fill text-info me-2"></i>测评尾页说明文案</h6>
                                </div>

                                <!-- Dynamic Student Types -->
                                <div class="p-3 bg-light border-bottom border-top text-muted small fw-bold d-flex justify-content-between align-items-center">
                                    <span>学生体系专属配置</span>
                                    <button class="btn btn-sm btn-outline-primary py-0 px-2" onclick="showAddTypeModal()"><i class="bi bi-plus"></i></button>
                                </div>
                                <div id="studentTypeContainer">
                                    <div class="p-4 text-center text-muted" id="typesLoading">
                                        <div class="spinner-border spinner-border-sm me-2"></div>加载中...
                                    </div>
                                    <!-- Types injected here via JS -->
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- Right: Content Panels -->
                    <div class="col-md-9 h-100">
                        <div class="card shadow-sm border-0 h-100 d-flex flex-column">
                            <div class="card-header bg-white d-flex justify-content-between align-items-center py-3 border-bottom">
                                <h6 class="mb-0 fw-bold text-secondary" id="currentConfigTitle"><i class="bi bi-sliders me-2"></i> 全局矩阵与基础标化配置</h6>
                            </div>
                            <div class="card-body bg-light flex-grow-1 overflow-auto p-4" id="mainContentContainer">
                                
                                <div class="text-center py-5 text-muted d-none flex-column align-items-center justify-content-center h-100" id="fieldEmptyState">
                                    <div class="p-4 rounded-circle bg-white shadow-sm mb-3">
                                        <i class="bi bi-hand-index fs-1 text-secondary"></i>
                                    </div>
                                    <h5 class="fw-bold text-dark mt-2">未选择任何配置</h5>
                                    <p class="text-muted mt-2">请在左侧点击选择需要查看或更改的配置项目。</p>
                                </div>

                                <!-- Panel: Global -->
                                <div id="panel-global" class="config-panel">
                                    <div class="bg-white p-4 rounded-3 shadow-sm border mb-4">
                                        <div class="d-flex justify-content-between align-items-center mb-3 pb-3 border-bottom">
                                            <h6 class="fw-bold mb-0 text-dark"><i class="bi bi-file-earmark-spreadsheet-fill text-primary me-2"></i> 全局能力矩阵 (CSV) 配置</h6>
                                        </div>
                                        <div class="alert alert-info py-2 small">
                                            <strong>注意：</strong> 第一行必须是表头字段名（如 Lingoland,CEFR,雅思 等）。系统将读取这行来让各体系配置列关联。
                                        </div>
                                        <textarea id="globalMatrixTextarea" class="form-control matrix-textarea mb-3" rows="8"></textarea>
                                        <div class="text-end">
                                            <button class="btn btn-primary px-4 shadow-sm rounded-pill" onclick="saveGlobalMatrix()">保存 CSV</button>
                                        </div>
                                    </div>

                                    <div class="bg-white p-4 rounded-3 shadow-sm border mb-4">
                                        <div class="d-flex justify-content-between align-items-center mb-3 pb-3 border-bottom">
                                            <h6 class="fw-bold mb-0 text-dark"><i class="bi bi-check2-square text-success me-2"></i> 【基础与通用标化】导出列配置</h6>
                                        </div>
                                        <p class="text-muted small mb-4 mt-2">请勾选属于“基础与通用标化”的列。在前台导出任何表单时，此处勾选的列都将作为公用列被分类至“基础与通用标化”栏目下。</p>
                                        <div id="basicColumnsContainer" class="row g-3 mb-4">
                                            <!-- Checkboxes injected here -->
                                        </div>
                                        <div class="text-end border-top pt-3 mt-2">
                                            <button class="btn btn-success px-4 shadow-sm rounded-pill" onclick="saveBasicColumns()">保存标化列</button>
                                        </div>
                                    </div>
                                </div>

                                <!-- Panel: Desc -->
                                <div id="panel-desc" class="config-panel d-none">
                                    <div class="bg-white p-4 rounded-3 shadow-sm border h-100 d-flex flex-column">
                                        <div class="d-flex justify-content-between align-items-center mb-3 pb-3 border-bottom">
                                            <h6 class="fw-bold mb-0 text-dark"><i class="bi bi-file-earmark-text-fill text-info me-2"></i> 测评说明文案配置</h6>
                                        </div>
                                        <div class="alert alert-secondary py-2 small mb-3">
                                            <strong>提醒：</strong> 当填报端勾选了对应“参考测评体系”（如 KET / PET / 雅思）时，这边的文案会被自动作为尾页附录追加至生成的报告中。
                                        </div>
                                        <div class="text-end mb-2">
                                            <button class="btn btn-sm btn-primary" onclick="addAssessmentDescRow()">+ 新增测评说明</button>
                                        </div>
                                        <div id="assessmentDescList" class="list-group flex-grow-1 overflow-auto mb-3" style="max-height: 50vh;">
                                            <!-- rows injected via JS -->
                                        </div>
                                        <div class="text-end border-top pt-3 mt-auto">
                                            <button class="btn btn-success px-4 shadow-sm rounded-pill" onclick="saveAssessmentDescs()">保存所有文案</button>
                                        </div>
                                    </div>
                                </div>

                                <!-- Panel: Type Config -->
                                <div id="panel-type" class="config-panel d-none">
                                    <div id="matrixSection">
                                        <div class="bg-white p-4 rounded-3 shadow-sm border mb-4">
                                            <div class="d-flex justify-content-between align-items-center mb-3 pb-3 border-bottom">
                                                <h6 class="fw-bold mb-0 text-dark"><i class="bi bi-check2-square text-success me-2"></i> 该体系专属能力矩阵 (列关联)</h6>
                                            </div>
                                            <p class="text-muted small mb-4 mt-2">系统已读取全局 CSV 表头，请勾选该体系导出报告时需要展示的专属列。<br/>
                                                <span class="text-warning"><i class="bi bi-exclamation-triangle-fill"></i> 注意：【基础与通用标化】无需在此处重复勾选，它们会自动在前台出现。</span>
                                            </p>
                                            <div id="columnCheckboxesContainer" class="row g-3 mb-4">
                                                <!-- Checkboxes injected here -->
                                            </div>
                                            <div class="text-end border-top pt-3 mt-2">
                                                <button class="btn btn-primary px-4 shadow-sm rounded-pill" onclick="saveAssociatedColumns()">保存该体系列关联</button>
                                            </div>
                                        </div>
                                    </div>
                                </div>

                            </div>
                        </div>
                    </div>
                </div>
            </main>
        </div>
    </div>
"""
content = content[:html_start] + new_html + content[html_end:]

# 2. Delete modals
modal_start = content.find('<!-- Global Matrix Modal -->')
script_start = content.find('<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>')
content = content[:modal_start] + content[script_start:]

# 3. Replace JS Section
js_start = content.find('let currentSelectedType = null;')
js_end = content.find('// 1. Types Logic')

new_js = """        let currentSelectedType = null;
        let adminTypes = [];
        let globalCsvHeader = [];
        let globalBasicColumns = [];

        document.addEventListener('DOMContentLoaded', () => {
             loadTypes();
             loadGlobalCsvHeader();
             loadAssessmentDescs();
             // Initial state
             selectMainMenu('global');
        });

        function selectMainMenu(menuId) {
            // Update UI styling for active menu
            document.querySelectorAll('.type-list-item').forEach(el => el.classList.remove('active'));
            if(menuId === 'global') {
                document.getElementById('menu-global').classList.add('active');
                document.getElementById('currentConfigTitle').innerHTML = '<i class="bi bi-sliders me-2"></i> 全局矩阵与基础标化配置';
            } else if(menuId === 'desc') {
                document.getElementById('menu-desc').classList.add('active');
                document.getElementById('currentConfigTitle').innerHTML = '<i class="bi bi-sliders me-2"></i> 测评说明文案配置';
            }
            
            // Toggle panels
            document.querySelectorAll('.config-panel').forEach(p => p.classList.add('d-none'));
            document.getElementById('fieldEmptyState').classList.add('d-none');
            currentSelectedType = null;
            
            if(menuId === 'global') {
                document.getElementById('panel-global').classList.remove('d-none');
            } else if(menuId === 'desc') {
                document.getElementById('panel-desc').classList.remove('d-none');
            }
        }

        // 0. Global Config Logic
        function loadGlobalCsvHeader() {
            Promise.all([
                fetch('/admin/api/config/GLOBAL_CAPABILITY_MATRIX_CSV').then(res => res.json()),
                fetch('/admin/api/config/GLOBAL_BASIC_COLUMNS').then(res => res.json()).catch(() => ({value: ''}))
            ]).then(([csvData, basicData]) => {
                if (csvData.value) {
                    document.getElementById('globalMatrixTextarea').value = csvData.value;
                    const firstLine = csvData.value.split('\\n')[0];
                    if (firstLine) {
                        globalCsvHeader = firstLine.split(',').map(s => s.replace(/^"|"$/g, '').trim());
                    }
                }
                
                if (basicData.value) {
                    globalBasicColumns = basicData.value.split(',').map(s => s.trim());
                } else {
                    globalBasicColumns = [];
                }
                
                renderBasicColumnsCheckboxes();
                if (currentSelectedType) {
                    loadMatrix(currentSelectedType);
                }
            });
        }

        function renderBasicColumnsCheckboxes() {
            const container = document.getElementById('basicColumnsContainer');
            container.innerHTML = '';
            
            if (globalCsvHeader.length === 0) {
                container.innerHTML = '<div class="col-12 text-danger small">无法加载全局 CSV 表头，请先配置上方大框内的 CSV 数据。</div>';
                return;
            }

            globalCsvHeader.forEach((col, index) => {
                const isChecked = globalBasicColumns.includes(col) ? 'checked' : '';
                container.innerHTML += `
                    <div class="col-md-4 col-sm-6">
                        <div class="form-check border p-2 rounded bg-white">
                            <input class="form-check-input basic-column-checkbox" type="checkbox" value="${col}" id="bcol_${index}" ${isChecked}>
                            <label class="form-check-label w-100" style="cursor: pointer;" for="bcol_${index}">
                                ${col}
                            </label>
                        </div>
                    </div>
                `;
            });
        }

        function saveGlobalMatrix() {
            const newCsv = document.getElementById('globalMatrixTextarea').value;
            fetch('/admin/api/config/GLOBAL_CAPABILITY_MATRIX_CSV', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({ value: newCsv })
            }).then(() => {
                loadGlobalCsvHeader();
                alert('全局能力矩阵已保存！列表已根据新表头刷新。');
            });
        }

        function saveBasicColumns() {
            const checkboxes = document.querySelectorAll('.basic-column-checkbox');
            const selected = [];
            checkboxes.forEach(cb => {
                if (cb.checked) {
                    selected.push(cb.value);
                }
            });

            const newValue = selected.join(',');
            
            fetch('/admin/api/config/GLOBAL_BASIC_COLUMNS', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({ value: newValue })
            }).then(() => {
                globalBasicColumns = selected;
                alert('基础与通用标化列配置已保存！');
            });
        }

        // 0.5 Assessment Descs Logic
        let assessmentDescs = [];

        function loadAssessmentDescs() {
            fetch('/admin/api/config/GLOBAL_ASSESSMENT_DESCRIPTIONS')
            .then(res => res.json())
            .then(data => {
                try {
                    assessmentDescs = data.value ? JSON.parse(data.value) : [];
                } catch(e) { assessmentDescs = []; }
                renderAssessmentDescs();
            }).catch(() => {
                assessmentDescs = [];
                renderAssessmentDescs();
            });
        }

        function renderAssessmentDescs() {
            const list = document.getElementById('assessmentDescList');
            list.innerHTML = '';
            if (assessmentDescs.length === 0) {
                list.innerHTML = '<div class="text-center text-muted py-4">暂无测评说明，点击上方新增。</div>';
                return;
            }
            assessmentDescs.forEach((item, index) => {
                list.innerHTML += `
                    <div class="list-group-item bg-light mb-2 border rounded p-3">
                        <div class="row align-items-start">
                            <div class="col-md-3">
                                <label class="small text-muted mb-1 fw-bold">测评名称 / 关键词</label>
                                <input type="text" class="form-control form-control-sm desc-name-input" value="${item.name}" placeholder="如：KET">
                            </div>
                            <div class="col-md-8">
                                <label class="small text-muted mb-1 fw-bold">说明详情 (大段文案)</label>
                                <textarea class="form-control form-control-sm desc-text-input" rows="3" placeholder="如：本次测评难度为KET...">${item.description}</textarea>
                            </div>
                            <div class="col-md-1 text-end mt-4">
                                <button class="btn btn-sm btn-outline-danger" onclick="deleteAssessmentDescRow(${index})"><i class="bi bi-trash"></i></button>
                            </div>
                        </div>
                    </div>
                `;
            });
        }

        function addAssessmentDescRow() {
            assessmentDescs.push({ name: '', description: '' });
            renderAssessmentDescs();
            setTimeout(() => {
                const list = document.getElementById('assessmentDescList');
                list.scrollTop = list.scrollHeight;
            }, 50);
        }

        function deleteAssessmentDescRow(index) {
            assessmentDescs.splice(index, 1);
            renderAssessmentDescs();
        }

        function saveAssessmentDescs() {
            const names = document.querySelectorAll('.desc-name-input');
            const texts = document.querySelectorAll('.desc-text-input');
            const newArray = [];
            for(let i=0; i<names.length; i++){
                const n = names[i].value.trim();
                const t = texts[i].value.trim();
                if (n || t) {
                    newArray.push({ name: n, description: t });
                }
            }
            fetch('/admin/api/config/GLOBAL_ASSESSMENT_DESCRIPTIONS', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({ value: JSON.stringify(newArray) })
            }).then(() => {
                alert('测评文案已成功保存至后台数据库！');
            });
        }

"""
content = content[:js_start] + new_js + content[js_end:]

# 4. Modify 'selectType' and loadMatrix to adjust for the new panels 
js_select_type_start = content.find('function selectType(typeCode, typeName) {')
js_select_type_end = content.find('// 3. Matrix Logic', js_select_type_start)

new_js_select_type = """function selectType(typeCode, typeName) {
            currentSelectedType = typeCode;
            document.getElementById('currentConfigTitle').innerHTML = `<i class="bi bi-sliders me-2"></i> 正在配置: ${typeName} 体系专属列`;
            
            // Re-render styles and load content
            renderTypes(adminTypes);
            document.querySelectorAll('.type-list-item').forEach(el => el.classList.remove('active'));
            // Since active is set in renderTypes() we just need to un-active the global menues
            document.getElementById('menu-global').classList.remove('active');
            document.getElementById('menu-desc').classList.remove('active');

            // Toggle panels
            document.querySelectorAll('.config-panel').forEach(p => p.classList.add('d-none'));
            document.getElementById('fieldEmptyState').classList.add('d-none');
            document.getElementById('panel-type').classList.remove('d-none');
            
            loadMatrix(typeCode);
        }

"""
content = content[:js_select_type_start] + new_js_select_type + content[js_select_type_end:]

with open("src/main/resources/templates/admin-templates.html", "w") as f:
    f.write(content)
