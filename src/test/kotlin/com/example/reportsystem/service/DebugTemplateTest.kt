package com.example.reportsystem.service

import org.junit.jupiter.api.Test
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.springframework.core.io.ClassPathResource

class DebugTemplateTest {
    @Test
    fun testDump() {
        val resource = ClassPathResource("static/Lingoland学习方案.docx")
        val doc = XWPFDocument(resource.inputStream)
        println("START DUMP")
        doc.paragraphs.forEachIndexed { i, p ->
            println("PARA \$i: \${p.text}")
        }
        println("END DUMP")
        
        println("TABLES DUMP")
        doc.tables.forEachIndexed { tIdx, table ->
            println("Table \$tIdx")
            table.rows.forEachIndexed { rIdx, row ->
                row.tableCells.forEachIndexed { cIdx, cell ->
                    cell.paragraphs.forEachIndexed { pIdx, p ->
                        println("  T\$tIdx R\$rIdx C\$cIdx P\$pIdx: \${p.text}")
                    }
                }
            }
        }
    }
}
