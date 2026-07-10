package com.example.reportsystem.service.docx

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import kotlin.math.roundToLong

object DocxFeePageRenderer {
    private const val MAX_FEE_IMAGE_HEIGHT_EMU = 7_200_000L

    fun render(document: XWPFDocument) {
        val headingIndex = document.bodyElements.indexOfFirst { element ->
            element is XWPFParagraph && element.text == "费用"
        }
        if (headingIndex < 0) return

        val heading = document.bodyElements[headingIndex] as XWPFParagraph
        val headingProperties = heading.ctp.pPr ?: heading.ctp.addNewPPr()
        if (!headingProperties.isSetKeepNext) headingProperties.addNewKeepNext()
        if (!headingProperties.isSetKeepLines) headingProperties.addNewKeepLines()

        val imageParagraph = document.bodyElements
            .drop(headingIndex + 1)
            .filterIsInstance<XWPFParagraph>()
            .firstOrNull { paragraph -> paragraph.runs.any { it.ctr.drawingList.isNotEmpty() } }
            ?: return

        val inline = imageParagraph.runs
            .asSequence()
            .flatMap { it.ctr.drawingList.asSequence() }
            .flatMap { it.inlineList.asSequence() }
            .firstOrNull()
            ?: return

        val currentHeight = inline.extent.cy
        if (currentHeight <= MAX_FEE_IMAGE_HEIGHT_EMU) return

        val scale = MAX_FEE_IMAGE_HEIGHT_EMU.toDouble() / currentHeight.toDouble()
        val scaledWidth = (inline.extent.cx.toDouble() * scale).roundToLong()
        inline.extent.cx = scaledWidth
        inline.extent.cy = MAX_FEE_IMAGE_HEIGHT_EMU

        imageParagraph.runs
            .flatMap { it.embeddedPictures }
            .firstOrNull()
            ?.ctPicture
            ?.spPr
            ?.xfrm
            ?.ext
            ?.apply {
                cx = scaledWidth
                cy = MAX_FEE_IMAGE_HEIGHT_EMU
            }

        val imageProperties = imageParagraph.ctp.pPr ?: imageParagraph.ctp.addNewPPr()
        if (!imageProperties.isSetKeepLines) imageProperties.addNewKeepLines()
    }
}

