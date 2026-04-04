package com.example.reportsystem.controller

import com.example.reportsystem.common.api.ResponseResult
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/admin/api/analysis-templates")
class AdminExcelController {

    data class DimensionParsed(
        var dimension: String = "",
        var positive: String = "",
        var negative: String = ""
    )

    @PostMapping("/import-excel")
    fun importExcel(@RequestParam("file") file: MultipartFile): ResponseResult<List<DimensionParsed>> {
        if (file.isEmpty) {
            return ResponseResult.error(400, "上传文件为空")
        }

        val dimensions = mutableListOf<DimensionParsed>()
        
        file.inputStream.use { inputStream ->
            val workbook = XSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)
            
            var currentDimensionObj: DimensionParsed? = null
            
            // Loop starting from row 1 to skip header (index 0)
            for (i in 1..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                
                val dimStr = row.getCell(0)?.stringCellValue?.trim() ?: ""
                val statusStr = row.getCell(1)?.stringCellValue?.trim() ?: ""
                val textStr = row.getCell(2)?.stringCellValue?.trim() ?: ""
                
                if (dimStr.isNotEmpty()) {
                    currentDimensionObj = DimensionParsed(dimension = dimStr)
                    dimensions.add(currentDimensionObj)
                }
                
                if (currentDimensionObj != null) {
                    if (statusStr.contains("✅") || statusStr.contains("达标")) {
                        currentDimensionObj.positive = textStr
                    } else if (statusStr.contains("❗") || statusStr.contains("未达标") || statusStr.contains("不达标")) {
                        currentDimensionObj.negative = textStr
                    }
                }
            }
        }
        
        return ResponseResult.success(dimensions)
    }
}
