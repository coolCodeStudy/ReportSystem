import ast
import inspect
import unittest

import e2e.c_end.test_student_flow as student_flow


class StudentFlowContractTest(unittest.TestCase):
    def test_export_report_uses_stable_button_ids(self):
        source = inspect.getsource(student_flow.export_report)

        self.assertIn("#exportReportBtn", source)
        self.assertIn("#confirmReportExportBtn", source)

        tree = ast.parse(source)
        string_literals = [
            node.value
            for node in ast.walk(tree)
            if isinstance(node, ast.Constant) and isinstance(node.value, str)
        ]

        ambiguous_locators = [
            value
            for value in string_literals
            if "button:has-text('导出报告')" in value or 'button:has-text("导出报告")' in value
        ]
        self.assertEqual([], ambiguous_locators)


if __name__ == "__main__":
    unittest.main()
