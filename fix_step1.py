with open("src/main/resources/templates/workspace.html", "r", encoding="utf-8") as f:
    lines = f.readlines()

out = []
skip = False
for line in lines:
    if "function saveStep1AndContinue() {" in line:
        skip = True
        out.append("""        function saveStep1DataSilent() {
            const recordIdSpan = document.getElementById('recordIdSpan');
            if (!recordIdSpan) return Promise.resolve();
            const recordId = recordIdSpan.innerText;
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
            }).then(res => res.json()).catch(() => ({}));
        }
""")
        continue
        
    if skip:
        if "        // Initialize Step 1" in line:
            skip = False
            out.append(line)
        continue
    
    out.append(line)

with open("src/main/resources/templates/workspace.html", "w", encoding="utf-8") as f:
    f.writelines(out)

