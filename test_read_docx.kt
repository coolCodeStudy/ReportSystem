import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.FileInputStream

fun main() {
    val doc = XWPFDocument(FileInputStream("src/main/resources/static/Lingoland学习方案.docx"))
    doc.paragraphs.forEach { p ->
        val text = p.text.trim()
        if (text.isNotEmpty()) println(text)
    }
}
