import sys
import tempfile
import unittest
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))

import qa_common


class QaCommonVisualQaTest(unittest.TestCase):
    def test_png_visual_metrics_distinguish_blank_and_content_pages(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            blank = tmp_path / "blank.png"
            content = tmp_path / "content.png"

            Image.new("RGB", (200, 200), "white").save(blank)
            image = Image.new("RGB", (200, 200), "white")
            draw = ImageDraw.Draw(image)
            draw.rectangle((40, 40, 160, 160), fill=(20, 70, 140))
            image.save(content)

            blank_metrics = qa_common.analyze_png_visual_metrics(blank)
            content_metrics = qa_common.analyze_png_visual_metrics(content)

            self.assertTrue(blank_metrics["is_blank"])
            self.assertLess(blank_metrics["non_white_ratio"], 0.01)
            self.assertFalse(content_metrics["is_blank"])
            self.assertGreater(content_metrics["non_white_ratio"], 0.2)

    def test_visual_qa_flags_blank_pages_and_marks_fee_split_for_review(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            blank = tmp_path / "page-1.png"
            fee_page = tmp_path / "page-2.png"
            image_page = tmp_path / "page-3.png"

            Image.new("RGB", (200, 200), "white").save(blank)
            Image.new("RGB", (200, 200), "white").save(fee_page)
            image = Image.new("RGB", (200, 200), "white")
            draw = ImageDraw.Draw(image)
            draw.rectangle((20, 20, 180, 180), fill=(20, 70, 140))
            image.save(image_page)

            issues, details = qa_common.run_visual_qa_checks(
                [blank, fee_page, image_page],
                {
                    1: "封面",
                    2: "四、费用",
                    3: "课程价目表",
                },
            )

            self.assertTrue(any("appears blank" in issue for issue in issues))
            self.assertFalse(any("费用" in issue and "课程价目表" in issue for issue in issues))
            self.assertTrue(any("费用" in item and "课程价目表" in item for item in details["review_items"]))
            self.assertEqual(details["keyword_pages"]["费用"], [2])
            self.assertEqual(details["keyword_pages"]["课程价目表"], [3])

    def test_visual_qa_marks_empty_fee_page_followed_by_dense_image_page_for_review(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            fee_page = tmp_path / "page-1.png"
            image_page = tmp_path / "page-2.png"
            tail_page = tmp_path / "page-3.png"

            fee_image = Image.new("RGB", (200, 200), "white")
            draw = ImageDraw.Draw(fee_image)
            draw.rectangle((20, 20, 80, 35), fill=(20, 20, 20))
            fee_image.save(fee_page)
            image = Image.new("RGB", (200, 200), (20, 70, 140))
            image.save(image_page)
            tail_image = Image.new("RGB", (200, 200), "white")
            ImageDraw.Draw(tail_image).rectangle((20, 20, 80, 35), fill=(20, 20, 20))
            tail_image.save(tail_page)

            issues, details = qa_common.run_visual_qa_checks(
                [fee_page, image_page, tail_page],
                {
                    1: "四、费用",
                    2: "",
                    3: "附录",
                },
            )

            self.assertEqual(issues, [])
            self.assertTrue(any("dense image page" in item for item in details["review_items"]))

    def test_visual_qa_does_not_flag_fee_page_when_price_image_is_already_present(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            fee_page = tmp_path / "page-1.png"
            next_dense_page = tmp_path / "page-2.png"
            tail_page = tmp_path / "page-3.png"

            fee_image = Image.new("RGB", (200, 200), "white")
            draw = ImageDraw.Draw(fee_image)
            draw.rectangle((35, 35, 165, 180), fill=(20, 70, 140))
            fee_image.save(fee_page)
            Image.new("RGB", (200, 200), (20, 70, 140)).save(next_dense_page)
            tail_image = Image.new("RGB", (200, 200), "white")
            ImageDraw.Draw(tail_image).rectangle((20, 20, 80, 35), fill=(20, 20, 20))
            tail_image.save(tail_page)

            issues, details = qa_common.run_visual_qa_checks(
                [fee_page, next_dense_page, tail_page],
                {
                    1: "四、费用",
                    2: "ABOUT US",
                    3: "附录",
                },
            )

            self.assertEqual(issues, [])
            self.assertEqual(details["review_items"], [])

    def test_visual_qa_flags_status_characters_that_are_invisible_in_rendered_page(self):
        with tempfile.TemporaryDirectory() as tmp:
            page = Path(tmp) / "page-1.png"
            image = Image.new("RGB", (200, 200), "white")
            ImageDraw.Draw(image).rectangle((60, 50, 170, 160), fill=(40, 40, 40))
            image.save(page)

            issues, details = qa_common.run_visual_qa_checks(
                [page],
                {1: "词汇 ........ ✅  专注力 ........ ❗"},
            )

            self.assertTrue(any("status icons may be invisible" in issue for issue in issues))
            self.assertEqual(details["status_icon_metrics"][0]["green_pixels"], 0)
            self.assertEqual(details["status_icon_metrics"][0]["red_pixels"], 0)

    def test_visual_qa_accepts_visible_green_and_red_status_icons(self):
        with tempfile.TemporaryDirectory() as tmp:
            page = Path(tmp) / "page-1.png"
            image = Image.new("RGB", (200, 200), "white")
            draw = ImageDraw.Draw(image)
            draw.rectangle((60, 50, 170, 160), fill=(40, 40, 40))
            draw.rectangle((120, 70, 130, 80), fill=(85, 175, 70))
            draw.rectangle((145, 95, 150, 110), fill=(235, 60, 60))
            image.save(page)

            issues, details = qa_common.run_visual_qa_checks(
                [page],
                {1: "词汇 ........ ✅  专注力 ........ ❗"},
            )

            self.assertFalse(any("status icons may be invisible" in issue for issue in issues))
            self.assertGreater(details["status_icon_metrics"][0]["green_pixels"], 20)
            self.assertGreater(details["status_icon_metrics"][0]["red_pixels"], 20)


if __name__ == "__main__":
    unittest.main()
