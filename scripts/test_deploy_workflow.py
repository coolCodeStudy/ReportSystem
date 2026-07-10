import unittest
from pathlib import Path


class DeployWorkflowTest(unittest.TestCase):
    def test_rpm_pdf_runtime_uses_recoverable_font_installation(self):
        workflow = Path(".github/workflows/deploy.yml").read_text()

        self.assertIn("install_rpm_pdf_runtime()", workflow)
        self.assertIn("google-noto-cjk-fonts", workflow)
        self.assertIn("google-noto-serif-cjk-fonts", workflow)
        self.assertIn("CJK Noto font package not available", workflow)
        self.assertIn("Color emoji font package not available", workflow)
        self.assertNotIn(
            "libreoffice-headless libreoffice-writer \\\n                google-noto-sans-cjk-fonts",
            workflow,
        )

    def test_pdf_runtime_maps_word_serif_fonts_for_linux_conversion(self):
        workflow = Path(".github/workflows/deploy.yml").read_text()

        self.assertIn("30-report-system-fonts.conf", workflow)
        self.assertIn("<family>华文宋体</family>", workflow)
        self.assertIn("<family>STSong</family>", workflow)
        self.assertIn("<family>Noto Serif CJK SC</family>", workflow)
        self.assertIn("fc-cache -f", workflow)


if __name__ == "__main__":
    unittest.main()
