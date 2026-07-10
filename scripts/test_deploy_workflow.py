import unittest
from pathlib import Path


class DeployWorkflowTest(unittest.TestCase):
    def test_rpm_pdf_runtime_uses_recoverable_font_installation(self):
        workflow = Path(".github/workflows/deploy.yml").read_text()

        self.assertIn("install_rpm_pdf_runtime()", workflow)
        self.assertIn("google-noto-cjk-fonts", workflow)
        self.assertIn("CJK Noto font package not available", workflow)
        self.assertIn("Color emoji font package not available", workflow)
        self.assertNotIn(
            "libreoffice-headless libreoffice-writer \\\n                google-noto-sans-cjk-fonts",
            workflow,
        )


if __name__ == "__main__":
    unittest.main()
