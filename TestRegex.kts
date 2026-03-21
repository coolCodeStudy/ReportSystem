val combinedText = "测评说明：本次测评难度为雅思难度。从听、说、读、写四项语言技能以及学习与能力方面，全面反映学员在语言知识与中级交际方面的能力水平。重点考察学员对词汇、语法知识的了解以及语言技能的运用。考核项目分为笔试和口试两部分，包含听力、阅读与写作，口试由学员与测试官口头完成。"
val replaced = combinedText.replace("。", "。\n")
val parts = replaced.split(Regex("\\r?\\n")).map { it.trim() }.filter { it.isNotEmpty() }
println("Parts count: ${parts.size}")
parts.forEachIndexed { i, p -> println("Part $i: $p") }
