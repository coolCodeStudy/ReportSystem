(function () {
    const modalId = 'reportExportSettingsModal';
    const optionsContainerId = 'reportOutlineBookOptions';
    const exportButtonId = 'confirmReportExportBtn';
    const formatName = 'reportExportFormat';
    const styleId = 'reportExportSettingsStyle';

    function escapeHtml(value) {
        return String(value || '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function splitTextbookNames(rawText) {
        return String(rawText || '')
            .split(/[,，、/／\n]+/)
            .map(item => item.trim())
            .filter(Boolean);
    }

    function parseTeachingPlanData(rawValue) {
        if (!rawValue) return { coursePlans: [] };
        if (typeof rawValue === 'object') {
            if (!Array.isArray(rawValue.coursePlans)) rawValue.coursePlans = [];
            return rawValue;
        }
        try {
            const parsed = JSON.parse(rawValue);
            if (!Array.isArray(parsed.coursePlans)) parsed.coursePlans = [];
            return parsed;
        } catch (e) {
            return { coursePlans: [] };
        }
    }

    function buildOutlineBookOptions(data) {
        const plans = Array.isArray(data.coursePlans) ? data.coursePlans : [];
        const excludedBooks = new Set(Array.isArray(data.outlineExcludedBooks) ? data.outlineExcludedBooks : []);
        const hasSavedBookSelection = Array.isArray(data.outlineExcludedBooks);
        const bookMap = new Map();

        plans.forEach((row, idx) => {
            const phase = String(row.phase || `阶段 ${idx + 1}`).replace(/\n/g, ' / ').trim();
            splitTextbookNames(row.textbook).forEach(bookName => {
                if (!bookMap.has(bookName)) {
                    bookMap.set(bookName, {
                        name: bookName,
                        phases: [],
                        legacyEnabledCount: 0
                    });
                }

                const item = bookMap.get(bookName);
                if (phase && !item.phases.includes(phase)) item.phases.push(phase);
                if (row.outlineEnabled !== false) item.legacyEnabledCount += 1;
            });
        });

        return Array.from(bookMap.values()).map(item => ({
            ...item,
            checked: hasSavedBookSelection
                ? !excludedBooks.has(item.name)
                : item.legacyEnabledCount > 0
        }));
    }

    function ensureModal() {
        if (!document.getElementById(styleId)) {
            const style = document.createElement('style');
            style.id = styleId;
            style.textContent = `
                .report-outline-book-option {
                    border: 1px solid #E5E7EB;
                    border-radius: 0.5rem;
                    padding: 0.875rem 1rem;
                    background: #fff;
                }

                .report-outline-book-meta {
                    font-size: 0.85rem;
                    color: #6B7280;
                    line-height: 1.35;
                }
            `;
            document.head.appendChild(style);
        }

        if (document.getElementById(modalId)) return;

        const wrapper = document.createElement('div');
        wrapper.innerHTML = `
            <div class="modal fade" id="${modalId}" tabindex="-1" aria-labelledby="${modalId}Label" aria-hidden="true" style="z-index: 1060;">
                <div class="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable">
                    <div class="modal-content shadow">
                        <div class="modal-header border-bottom">
                            <h5 class="modal-title text-primary" id="${modalId}Label">导出设置</h5>
                            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                        </div>
                        <div class="modal-body bg-light">
                            <fieldset class="mb-4">
                                <legend class="fw-bold fs-6 mb-2">导出格式</legend>
                                <div class="btn-group w-100" role="group" aria-label="导出格式">
                                    <input type="radio" class="btn-check" name="reportExportFormat" id="reportExportFormatWord" value="word" checked>
                                    <label class="btn btn-outline-primary" for="reportExportFormatWord">
                                        <i class="bi bi-file-earmark-word me-1"></i>Word
                                    </label>
                                    <input type="radio" class="btn-check" name="reportExportFormat" id="reportExportFormatPdf" value="pdf">
                                    <label class="btn btn-outline-primary" for="reportExportFormatPdf">
                                        <i class="bi bi-file-earmark-pdf me-1"></i>PDF
                                    </label>
                                </div>
                            </fieldset>
                            <div class="fw-bold mb-1">教学计划大纲</div>
                            <p class="text-muted small mb-3">选择哪些教材打印大纲明细；课时规划表仍会完整导出。</p>
                            <div id="${optionsContainerId}" class="d-grid gap-2"></div>
                        </div>
                        <div class="modal-footer">
                            <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">取消</button>
                            <button type="button" class="btn btn-primary px-4 shadow-sm" id="${exportButtonId}">
                                <i class="bi bi-download me-1"></i>导出报告
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        `;
        document.body.appendChild(wrapper.firstElementChild);
    }

    function renderBookOptions(data) {
        const container = document.getElementById(optionsContainerId);
        const books = buildOutlineBookOptions(data);

        if (books.length === 0) {
            container.innerHTML = '<div class="text-muted small bg-white border rounded p-3">当前没有可配置的教材。确认导出后，报告会跳过教学计划大纲明细。</div>';
            return;
        }

        container.innerHTML = books.map((book, idx) => {
            const checked = book.checked ? 'checked' : '';
            const phaseText = book.phases.length > 0 ? book.phases.join('；') : '未填写阶段';
            return `
                <label class="report-outline-book-option d-flex align-items-start gap-3" for="report_outline_book_${idx}">
                    <input class="form-check-input mt-1 report-outline-book-checkbox" type="checkbox" id="report_outline_book_${idx}"
                        data-book="${escapeHtml(book.name)}" ${checked}>
                    <span class="flex-grow-1">
                        <span class="fw-semibold d-block">${escapeHtml(book.name)}</span>
                        <span class="report-outline-book-meta d-block mt-1">来自：${escapeHtml(phaseText)}</span>
                    </span>
                    <span class="badge text-bg-light border">打印教材大纲</span>
                </label>
            `;
        }).join('');
    }

    function applySelection(data) {
        if (!Array.isArray(data.coursePlans)) data.coursePlans = [];

        const selectedBooks = new Set();
        const excludedBooks = [];
        document.querySelectorAll('.report-outline-book-checkbox').forEach(cb => {
            const bookName = cb.dataset.book;
            if (!bookName) return;
            if (cb.checked) {
                selectedBooks.add(bookName);
            } else {
                excludedBooks.push(bookName);
            }
        });

        data.outlineExcludedBooks = excludedBooks;
        data.coursePlans.forEach(row => {
            const rowBooks = splitTextbookNames(row.textbook);
            row.outlineEnabled = rowBooks.length === 0 || rowBooks.some(bookName => selectedBooks.has(bookName));
        });

        return data;
    }

    function setLoading(button, loading, text) {
        if (!button) return null;
        if (loading) {
            const original = button.innerHTML;
            button.disabled = true;
            button.innerHTML = `<span class="spinner-border spinner-border-sm me-2"></span>${text || '正在生成...'}`;
            return original;
        }
        button.disabled = false;
        return null;
    }

    async function open(options) {
        ensureModal();

        const recordId = options.recordId;
        const data = parseTeachingPlanData(
            options.getTeachingPlanData ? options.getTeachingPlanData() : options.teachingPlanData
        );
        const exportButton = document.getElementById(exportButtonId);
        const wordFormat = document.getElementById('reportExportFormatWord');
        const modalEl = document.getElementById(modalId);
        const modal = bootstrap.Modal.getOrCreateInstance(modalEl);

        wordFormat.checked = true;
        renderBookOptions(data);
        async function exportReport() {
            const format = document.querySelector(`input[name="${formatName}"]:checked`)?.value || 'word';
            applySelection(data);

            const originalButtonHtml = setLoading(exportButton, true, '正在生成...');

            try {
                modal.hide();
                if (options.saveTeachingPlanData) {
                    await options.saveTeachingPlanData(data);
                }
                if (options.afterSave) {
                    options.afterSave(data);
                }
                window.location.href = format === 'pdf'
                    ? (options.pdfDownloadUrl || `/student/history/${recordId}/export/pdf`)
                    : (options.wordDownloadUrl || options.downloadUrl || `/student/history/${recordId}/export`);
            } catch (e) {
                if (window.Swal) {
                    Swal.fire('错误', '导出设置保存失败，请稍后重试。', 'error');
                } else {
                    alert('导出设置保存失败，请稍后重试。');
                }
            } finally {
                if (exportButton && originalButtonHtml !== null) {
                    exportButton.disabled = false;
                    exportButton.innerHTML = originalButtonHtml;
                }
            }
        }

        exportButton.onclick = exportReport;

        modal.show();
    }

    window.ReportExport = {
        open,
        parseTeachingPlanData,
        splitTextbookNames
    };
})();
