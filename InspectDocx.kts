import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.FileInputStream

val doc = XWPFDocument(FileInputStream("src/main/resources/static/Lingoland学习方案.docx"))
val para = doc.paragraphs.find { it.text.contains("测评说明") }
if (para != null) {
    println("Original text: ${para.text}")
    para.runs.forEachIndexed { i, r ->
        println("Run $i: '${r.text()}'")
    }
}
doc.close()
