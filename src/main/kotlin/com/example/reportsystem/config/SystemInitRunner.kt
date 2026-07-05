package com.example.reportsystem.config

import com.example.reportsystem.entity.StudentTypeDictionary
import com.example.reportsystem.entity.SystemConfig
import com.example.reportsystem.repository.StudentTypeDictionaryRepository
import com.example.reportsystem.repository.SystemConfigRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

private data class SeedRowData(
    val lingoland: String, val cefr: String, val lansi: String, val cihuiLiang: String,
    val cambridge: String, val toeflJr: String, val toefl: String, val ielts: String,
    val int1: String, val int2: String,
    val dse1a: String, val dse1bc: String, val dse2: String,
    val wbs: String, val beisaisi: String, val mapScore: String, val huli: String, val his: String, val hangwai: String,
    val zj: String, val sh: String
)

private val SEED_ROWS = listOf(
    SeedRowData("K", "Pre-A1", "160L", "400", "Starters", "", "", "", "K", "", "G1", "", "", "G2", "", "", "G1", "", "G2", "", ""),
    SeedRowData("G1", "A1", "165L", "800", "Movers", "", "", "", "G1", "G3", "G2", "G5", "G7", "G3", "K", "190", "G2", "G3", "G3", "", ""),
    SeedRowData("G2", "A2-", "425L", "1100", "Flyers", "", "", "", "G2", "G4", "G3", "G6", "G8", "G4", "G1", "204", "G3", "G4", "G4", "", ""),
    SeedRowData("G3", "A2+", "600L", "1500", "KET", "625", "", "3", "G3", "G5", "G4", "G7", "G9", "G5", "G2", "214", "G4", "G5", "G5", "", ""),
    SeedRowData("G4", "B1-", "725L", "2500", "PET", "725", "31", "4", "G4", "G6", "G5", "G8", "G10", "G6", "G3", "220", "G5", "G6", "G6", "中考近满分", ""),
    SeedRowData("G5", "B1+", "825L", "3500", "PET", "785", "45", "5", "G5", "G7", "G6", "G9", "G11", "G7", "G4", "226", "G6", "G7", "G7", "", "中考近满分"),
    SeedRowData("G6", "B2-", "925L", "4500", "PET", "860", "66", "5.5", "G6", "G8", "G7", "G10", "G12", "G8", "G5", "230", "G7", "G8", "G8", "", ""),
    SeedRowData("G7", "B2+", "1000L", "6000", "FCE", "865", "93", "6.5", "G7", "G9", "G8", "G11", "", "G9", "G6", "236", "G8", "G9", "G9", "高考140", ""),
    SeedRowData("G8", "C1-", "1050L", "7500", "FCE", "900", "101", "7", "G8", "G10", "G9", "G12", "", "G10", "G7", "238", "G9", "G10", "G10", "", "高考140"),
    SeedRowData("G9", "C1+", "1125L", "10000", "CAE", "", "109", "7.5", "G9", "", "G10", "", "", "G11", "G8", "", "G10", "G11", "G11", "", ""),
    SeedRowData("G10", "", "1175L", "15000", "CAE", "", "120", "8", "G10", "", "G11", "", "", "", "G9", "", "G11", "", "", "", ""),
    SeedRowData("G11", "", "1225L", "17500", "", "", "", "", "G11", "", "", "", "", "", "", "", "", "", "", "", ""),
    SeedRowData("", "", "1250L", "20000", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "")
)

private data class SeedColConfig(val header: String, val getValue: (SeedRowData) -> String)

private val FIXED_COLS = listOf(
    SeedColConfig("Lingoland") { it.lingoland }, SeedColConfig("CEFR") { it.cefr }, SeedColConfig("蓝思值") { it.lansi },
    SeedColConfig("词汇量") { it.cihuiLiang }, SeedColConfig("剑桥系考试") { it.cambridge }, SeedColConfig("TOEFL Junior") { it.toeflJr },
    SeedColConfig("托福") { it.toefl }, SeedColConfig("雅思") { it.ielts }
)

private val DEFAULT_COURSE_PLAN_JSON = """
    [
      {
        "phase": "阶段1:\n基础课程",
        "duration": "生活英语：2h\n学术英语：2h\n\n生活英语：NEF\n学术英语：Unlock + Sprint",
        "goal": "1. 用轻松的方式引入，让孩子适应国际体系的授课风格，逐渐开口运用\n2. 拓展词汇量\n3. 基础语法学习，时态、语态意识培养\n4. 欧标达到，对标",
        "hours": ""
      },
      {
        "phase": "阶段2:\n中高级课程/\n应试培训+面试辅导",
        "duration": "2h",
        "goal": "1. 建立学术英语阅读写作基础能力\n2. 培养批判性思维和文本分析能力\n3. 掌握学术词汇和复杂句式结构\n4. 对标",
        "hours": ""
      }
    ]
""".trimIndent()

private const val DEFAULT_COURSE_PLAN_NOTE =
    "课时浮动会根据学生基础、目标学校要求、备考时间和课堂吸收情况动态调整。"

@Component
class SystemInitRunner(
    private val studentTypeDictionaryRepository: StudentTypeDictionaryRepository,
    private val systemConfigRepository: SystemConfigRepository
) : CommandLineRunner {

    private fun generateGlobalCsv(): String {
        val dynamicCols = listOf(
            SeedColConfig("第一梯队国际") { it.int1 }, SeedColConfig("第二梯队国际") { it.int2 },
            SeedColConfig("香港DSE Band 1A") { it.dse1a }, SeedColConfig("香港DSE Band 1B-1C") { it.dse1bc }, SeedColConfig("香港DSE Band 2") { it.dse2 },
            SeedColConfig("WBS前20%") { it.wbs }, SeedColConfig("贝赛思") { it.beisaisi }, SeedColConfig("Map (83%-86%)") { it.mapScore }, SeedColConfig("惠立") { it.huli }, SeedColConfig("HIS") { it.his }, SeedColConfig("杭外剑高") { it.hangwai },
            SeedColConfig("浙江高考") { it.zj }, SeedColConfig("上海高考") { it.sh }
        )
        val allCols = FIXED_COLS + dynamicCols
        val sb = StringBuilder()
        
        // Headers
        sb.append(allCols.joinToString(",") { "\"${it.header.replace("\"", "\"\"")}\"" }).append("\n")
        
        // Rows
        SEED_ROWS.forEach { row ->
            sb.append(allCols.joinToString(",") { "\"${it.getValue(row).replace("\"", "\"\"")}\"" }).append("\n")
        }
        return sb.toString().trim()
    }

    private fun getAssociatedColumns(typeCode: String): String {
        val dynamicCols = when (typeCode) {
            "INTL", "TRANSITION_INTL" -> listOf("第一梯队国际", "第二梯队国际")
            "TRANSITION_HKDSE" -> listOf("香港DSE Band 1A", "香港DSE Band 1B-1C", "香港DSE Band 2")
            "TRANSITION_HANGZHOU_INTL" -> listOf("WBS前20%", "贝赛思", "Map (83%-86%)", "惠立", "HIS", "杭外剑高")
            "DOMESTIC" -> listOf("浙江高考", "上海高考")
            else -> emptyList()
        }
        val allCols = FIXED_COLS.map { it.header } + dynamicCols
        return allCols.joinToString(",")
    }

    private fun generateCsvForType(typeCode: String): String {
        val dynamicCols = when (typeCode) {
            "INTL", "TRANSITION_INTL" -> listOf(SeedColConfig("第一梯队国际") { it.int1 }, SeedColConfig("第二梯队国际") { it.int2 })
            "TRANSITION_HKDSE" -> listOf(SeedColConfig("香港DSE Band 1A") { it.dse1a }, SeedColConfig("香港DSE Band 1B-1C") { it.dse1bc }, SeedColConfig("香港DSE Band 2") { it.dse2 })
            "TRANSITION_HANGZHOU_INTL" -> listOf(SeedColConfig("WBS前20%") { it.wbs }, SeedColConfig("贝赛思") { it.beisaisi }, SeedColConfig("Map (83%-86%)") { it.mapScore }, SeedColConfig("惠立") { it.huli }, SeedColConfig("HIS") { it.his }, SeedColConfig("杭外剑高") { it.hangwai })
            "DOMESTIC" -> listOf(SeedColConfig("浙江高考") { it.zj }, SeedColConfig("上海高考") { it.sh })
            else -> emptyList()
        }
        val allCols = FIXED_COLS + dynamicCols
        val sb = StringBuilder()
        
        // Headers
        sb.append(allCols.joinToString(",") { "\"${it.header.replace("\"", "\"\"")}\"" }).append("\n")
        
        // Rows
        SEED_ROWS.forEach { row ->
            sb.append(allCols.joinToString(",") { "\"${it.getValue(row).replace("\"", "\"\"")}\"" }).append("\n")
        }
        return sb.toString().trim()
    }

    private fun seedConfigIfBlank(configKey: String, defaultValue: String, label: String) {
        val existing = systemConfigRepository.findByConfigKey(configKey)
        if (existing == null) {
            systemConfigRepository.save(SystemConfig(configKey = configKey, configValue = defaultValue))
            println("=== Initialized $label in system config ===")
        } else if (existing.configValue.isNullOrBlank()) {
            existing.configValue = defaultValue
            systemConfigRepository.save(existing)
            println("=== Filled blank $label in system config ===")
        }
    }

    override fun run(vararg args: String?) {
        // Initialize global matrix CSV
        if (systemConfigRepository.findByConfigKey("GLOBAL_CAPABILITY_MATRIX_CSV") == null) {
            systemConfigRepository.save(SystemConfig(configKey = "GLOBAL_CAPABILITY_MATRIX_CSV", configValue = generateGlobalCsv()))
            println("=== Initialized global capability matrix CSV in system config ===")
        }

        // Initialize global assessment descriptions
        if (systemConfigRepository.findByConfigKey("GLOBAL_ASSESSMENT_DESCRIPTIONS") == null) {
            val defaultDescs = """
                [
                  {"id":"starters","name":"Starters","description":"本次测评难度为Starters难度。"},
                  {"id":"movers","name":"Movers","description":"本次测评难度为Movers难度。"},
                  {"id":"flyers","name":"Flyers","description":"本次测评难度为Flyers难度。"},
                  {"id":"ket","name":"KET","description":"本次测评难度为KET难度。从听、说、读、写四项语言技能以及学习素养与能力方面，全面反映学员在语言知识与基础交际方面的能力水平。重点考察学员对该阶段词汇、语法知识的了解以及语言技能的运用。考核项目分为笔试和口试两部分，笔试包含听力、阅读与写作，口试由学员与测试官口头完成。"},
                  {"id":"pet","name":"PET","description":"本次测评难度为PET难度。从听、说、读、写四项语言技能以及学习素养与能力方面，全面反映学员在语言知识与初级交际方面的能力水平。重点考察学员对该阶段词汇、语法知识的了解以及语言技能的运用。考核项目分为笔试和口试两部分，笔试包含听力、阅读与写作，口试由学员与测试官口头完成。"},
                  {"id":"ielts","name":"IELTS","description":"本次测评难度为雅思难度。从听、说、读、写四项语言技能以及学习素养与能力方面，全面反映学员在语言知识与中级交际方面的能力水平。重点考察学员对该阶段词汇、语法知识的了解以及语言技能的运用。考核项目分为笔试和口试两部分，笔试包含听力、阅读与写作，口试由学员与测试官口头完成。"},
                  {"id":"toefl_junior","name":"TOEFL Junior","description":"本次测评难度为TOEFL Junior难度。"},
                  {"id":"map","name":"MAP","description":"本次测评难度为MAP难度。"}
                ]
            """.trimIndent()
            systemConfigRepository.save(SystemConfig(configKey = "GLOBAL_ASSESSMENT_DESCRIPTIONS", configValue = defaultDescs))
            println("=== Initialized global assessment descriptions in system config ===")
        }

        // Initialize global basic columns
        if (systemConfigRepository.findByConfigKey("GLOBAL_BASIC_COLUMNS") == null) {
            val defaultBasicCols = "Lingoland,CEFR,蓝思值,词汇量,剑桥系考试,TOEFL Junior,托福,雅思"
            systemConfigRepository.save(SystemConfig(configKey = "GLOBAL_BASIC_COLUMNS", configValue = defaultBasicCols))
            println("=== Initialized global basic columns in system config ===")
        }

        // Initialize Assessment Analysis Templates
        if (systemConfigRepository.findByConfigKey("GLOBAL_ANALYSIS_CONFIG_READING") == null) {
            val readingAnalysisJson = """
                [
                  { "dimension": "时间分配", "positive": "能在规定时间内完成。", "negative": "" },
                  { "dimension": "信息理解", "positive": "", "negative": "对于文章主要信息很难抓住，在细节理解上受生词影响，也无法依赖语境推测句意。" },
                  { "dimension": "词汇量", "positive": "", "negative": "基础生活词汇及部分学术词汇掌握不足，阅读中出现多处不认识的关键词，影响理解深度；词性和词组固定搭配的知识点较薄弱。" },
                  { "dimension": "考试技巧", "positive": "", "negative": "未明显体现出。不会主动圈划文章中的关键字或者是做笔记。" },
                  { "dimension": "阅读速度", "positive": "能够在规定时间内完成整篇文章的阅读。", "negative": "" },
                  { "dimension": "阅读策略（扫读，预测，跳读，推理等）", "positive": "", "negative": "缺乏阅读策略意识，推理能力较弱，需进一步训练信息筛选与逻辑推断能力。" },
                  { "dimension": "背景知识", "positive": "", "negative": "对常见文学/修辞手法、文化及学科性主题了解有限，需积累更多英语语境下的知识。" },
                  { "dimension": "专注力", "positive": "较为专注。", "negative": "" }
                ]
            """.trimIndent()
            systemConfigRepository.save(SystemConfig(configKey = "GLOBAL_ANALYSIS_CONFIG_READING", configValue = readingAnalysisJson))
            
            val readingCauseJson = """
                [
                  "阅读量不足，缺乏语感",
                  "长难句结构知识薄弱",
                  "不熟悉常见的同义词替换考点",
                  "做题时未能有效定位核心信息段落",
                  "词汇储备未能覆盖试卷高频大纲词"
                ]
            """.trimIndent()
            systemConfigRepository.save(SystemConfig(configKey = "GLOBAL_CAUSE_ANALYSIS_READING", configValue = readingCauseJson))
            println("=== Initialized Assessment Analysis Templates (Reading) ===")
        }

        if (systemConfigRepository.findByConfigKey("GLOBAL_ANALYSIS_CONFIG_SPEAKING") == null) {
            val speakingAnalysisJson = """
                [
                  { "dimension": "发音", "positive": "没有问题，单词发音准确。", "negative": "" },
                  { "dimension": "问题理解", "positive": "能够理解老师提出的不同时态的问题并作出回答。部分比较长的问题（如）第一遍会听不明白，老师用比较简单的语言拆解过后可以理解", "negative": "" },
                  { "dimension": "句型和语法的多样性", "positive": "不错，能够用举例子、对比等方法来支撑自己的观点，并运用I think, first, because等连接词正确衔接语句。", "negative": "" },
                  { "dimension": "流利", "positive": "非常流利，并且能够做到一边构思、一边表达，没有长时间的“额”这种停顿的情况。", "negative": "" },
                  { "dimension": "语速", "positive": "适中，清晰，且能够支持自己边想边说，不会卡壳。", "negative": "" },
                  { "dimension": "口音", "positive": "没有明显的母语口音，不会影响听者理解。", "negative": "" },
                  { "dimension": "语法正确性", "positive": "", "negative": "时态、语态问题比较严重。首先，没有明确的时态标记，不管是现在、过去还是将来，都是I do的形式回答。其次，有系统性语法问题，比如表达现在进行时的句子，会出现She is make这样的错误表达。缺少三单标记。" },
                  { "dimension": "词汇量", "positive": "日常用于词汇量是比较不错的，能够支持自己讲述和日常生活密切相关的话题。学术类词汇由于测试中没有涉及因此没有重点考察，但总体词汇量是比同龄体制内小朋友要多的，部分用词已经达到了初中的水平。", "negative": "" },
                  { "dimension": "语音、语句现象", "positive": "整体表达清晰，没有明显的口音带来的歧义或理解问题。", "negative": "" },
                  { "dimension": "互动和回应", "positive": "基本能够和老师做到一问一答有机衔接，老师抛出的所有问题，即使是yes/no可以回答的问题，也会用because...来给出具体的想法或原因。", "negative": "" },
                  { "dimension": "思维能力", "positive": "不错，而且思维深度远超同龄人。这一点在写作中表现得更为明显。口语交谈中，能看到小朋友对家人展现出体贴共情能力，对于一些生活话题（比如自己对休闲娱乐的看法、对生日的看法等）都有自己的见解和理由去支持。", "negative": "" },
                  { "dimension": "压力", "positive": "不太紧张。小朋友属于“淡人”，不管是思考中还是面对听不明白的问题时，都很少表现出情感上的波动，包括焦虑、压力、情绪等。从发言内容来看，小朋友还是更习惯于待在自己的“舒适圈”的。比如谈及不同的休闲娱乐方式，小朋友会更倾向于对自己熟悉的话题（cooking, reading）做出更多的阐述，而避开自己不那么熟悉话题。", "negative": "" },
                  { "dimension": "突发情况应对", "positive": "会片刻思考，询问“pardon?”。", "negative": "" }
                ]
            """.trimIndent()
            systemConfigRepository.save(SystemConfig(configKey = "GLOBAL_ANALYSIS_CONFIG_SPEAKING", configValue = speakingAnalysisJson))
            
            val speakingCauseJson = """
                [
                  "语法训练不规范导致语法问题较多，特别是时态语态等句型结构上基础语法的问题。"
                ]
            """.trimIndent()
            systemConfigRepository.save(SystemConfig(configKey = "GLOBAL_CAUSE_ANALYSIS_SPEAKING", configValue = speakingCauseJson))
        }

        if (systemConfigRepository.findByConfigKey("GLOBAL_ANALYSIS_CONFIG_WRITING") == null) {
            val writingAnalysisJson = """
                [
                  { "dimension": "内容", "positive": "能回应题目要求，有自己的想法，表达想法强烈。", "negative": "" },
                  { "dimension": "结构（Intro-Body-Conclusion）", "positive": "结构意识好，会将分论点逐个说明。", "negative": "" },
                  { "dimension": "写作惯例（Cohesion, Unity, Completeness）", "positive": "", "negative": "有逻辑衔接的意识，会用一些连词，如but, also等，但使用的准确性还需加强。如although和but不能连用。" },
                  { "dimension": "词汇", "positive": "", "negative": "词汇使用的准确性有待提高，包括词性，如。以及词义辨析，如。" },
                  { "dimension": "语法正确性", "positive": "", "negative": "存在多处基础语法错误（时态、主谓一致、复数、非谓语、比较级），例如。三单方面会注意，但是有些地方仍有疏漏。英文标点方面需要注意，断句意识不强。" },
                  { "dimension": "语法多样性", "positive": "", "negative": "尝试使用复合句，但还未完全掌握，如未用动名词作主语，动名词作主语谓语动词应使用单数形式。" },
                  { "dimension": "拼写", "positive": "单词拼写掌握的较好，无拼写错误。", "negative": "" },
                  { "dimension": "标点符号", "positive": "", "negative": "不会正确地使用逗号和句号，与语法基础不扎实有关。" },
                  { "dimension": "表达（清晰，简洁）", "positive": "", "negative": "思路清晰但表述冗余，部分句子结构模糊，影响可读性（如）。" }
                ]
            """.trimIndent()
            systemConfigRepository.save(SystemConfig(configKey = "GLOBAL_ANALYSIS_CONFIG_WRITING", configValue = writingAnalysisJson))

            val writingCauseJson = """
                [
                  "写作偏口语化，缺乏书面语结构和学术表达意识，无法正确使用逻辑连接词",
                  "语法和词汇整合能力不足，影响句式完整性与表达准确性",
                  "可能缺乏范文输入与模仿练习，导致表达生硬且语法规则未能内化为语感",
                  "缺乏组织语言的策略和自我检查的习惯，导致表达冗余且错误遗留"
                ]
            """.trimIndent()
            systemConfigRepository.save(SystemConfig(configKey = "GLOBAL_CAUSE_ANALYSIS_WRITING", configValue = writingCauseJson))
        }

        if (systemConfigRepository.findByConfigKey("GLOBAL_ANALYSIS_CONFIG_LISTENING") == null) {
            val listeningAnalysisJson = """
                [
                  { "dimension": "时间分配", "positive": "能够在规定时间内完成。", "negative": "" },
                  { "dimension": "信息理解", "positive": "", "negative": "理解有较大偏差，尤其是遇到生词和长句时，有较多生词不理解。" },
                  { "dimension": "语速适应", "positive": "", "negative": "在关键信息抓取上需要锻炼，可能信息量大的时候有点点困难，或容易受干扰项影响。" },
                  { "dimension": "单词辨音", "positive": "", "negative": "能够分辨已知单词的发音，对不常见单词以及长难句难以辨认。" },
                  { "dimension": "专注力", "positive": "较为专注。", "negative": "" },
                  { "dimension": "语法知识（如从句的理解）", "positive": "", "negative": "能够理解简单语法结构，但面对复杂句式时需要更多时间来反应。" },
                  { "dimension": "语音变体（是否能适应口音）", "positive": "", "negative": "" },
                  { "dimension": "语音现象", "positive": "", "negative": "" },
                  { "dimension": "考试策略", "positive": "", "negative": "没有考试策略的意识，没有提前读题，圈划关键词的习惯。" },
                  { "dimension": "焦虑与压力", "positive": "基本没有焦虑情绪。", "negative": "" },
                  { "dimension": "词汇", "positive": "", "negative": "听音频填空完成得不错；但在理解性的题目中可能因为较多生词而影响题目分析。" }
                ]
            """.trimIndent()
            systemConfigRepository.save(SystemConfig(configKey = "GLOBAL_ANALYSIS_CONFIG_LISTENING", configValue = listeningAnalysisJson))

            val listeningCauseJson = """
                [
                  "词汇量匮乏。一些关键词汇的不理解会造成信息听取出现大面积空白",
                  "语法结构掌握不扎实，造成长难句的理解偏差",
                  "没有考试策略的意识，没有提前读题，圈划关键词的习惯。",
                  "抓取关键信息的能力薄弱，易受干扰项影响"
                ]
            """.trimIndent()
            systemConfigRepository.save(SystemConfig(configKey = "GLOBAL_CAUSE_ANALYSIS_LISTENING", configValue = listeningCauseJson))
        }

        if (systemConfigRepository.findByConfigKey("GLOBAL_ANALYSIS_CONFIG_LANGUAGE_USE") == null) {
            val languageUseAnalysisJson = """
                [
                  { "dimension": "基础语法概念", "positive": "", "negative": "对主谓一致、时态和句型结构可能有基本认识，但在实际使用中仍混乱。时态辨析不强，特别是一般过去时和过去进行时。" },
                  { "dimension": "句型结构与语法形式", "positive": "", "negative": "主要使用简单句，复合句结构掌握不足，连接词使用有限。" },
                  { "dimension": "词汇量", "positive": "", "negative": "应该掌握的高频学术及生活词汇（如）明显欠缺，影响理解与产出。" },
                  { "dimension": "语音与拼读", "positive": "语音与拼读没有问题。", "negative": "" },
                  { "dimension": "词汇识别与语法迁移", "positive": "", "negative": "生词无法通过语法线索或词根推测含义，反映出未建立对词汇组成的概念和意识的运用。" },
                  { "dimension": "语法意识与自我校正", "positive": "", "negative": "缺乏语法自检能力，对错误缺乏敏感度。" },
                  { "dimension": "词形变化", "positive": "", "negative": "动词原形、三单、过去式及过去分词（如的变形）、非谓语等知识点未完全掌握，名词复数、比较级最高级的使用也有小问题。" }
                ]
            """.trimIndent()
            systemConfigRepository.save(SystemConfig(configKey = "GLOBAL_ANALYSIS_CONFIG_LANGUAGE_USE", configValue = languageUseAnalysisJson))

            val languageUseCauseJson = """
                [
                  "语法学习以记忆为主，缺乏语境化练习，导致“知规则但不会用”",
                  "动词变化、句型连接、名词复数等语法点掌握零散，系统性不足",
                  "词汇量有限，影响语法结构理解与句式表达",
                  "缺乏语言运用规则意识，对词根、派生和词性变化不敏感"
                ]
            """.trimIndent()
            systemConfigRepository.save(SystemConfig(configKey = "GLOBAL_CAUSE_ANALYSIS_LANGUAGE_USE", configValue = languageUseCauseJson))
        }

        if (systemConfigRepository.findByConfigKey("GLOBAL_ANALYSIS_CONFIG_LEARNING_LITERACY") == null) {
            val learningLiteracyAnalysisJson = """
                [
                  { "dimension": "学习态度", "positive": "专注度总体还可以。", "negative": "" },
                  { "dimension": "依从性", "positive": "很好地落实老师的指令；能够基本完成测评任务。", "negative": "" },
                  { "dimension": "思维", "positive": "", "negative": "思维活跃，善于思考，有辩证思考问题的能力。但是现有词汇量无法支持完成一些比较复杂的表达。" },
                  { "dimension": "复习习惯", "positive": "学习状态好，推测复习习惯较好，能完成老师布置的复习任务。", "negative": "" },
                  { "dimension": "学习策略", "positive": "学习习惯比较优秀。", "negative": "" },
                  { "dimension": "畏难情绪", "positive": "能够按要求完成所有任务，即使觉得有点困难也能坚持完成。", "negative": "" },
                  { "dimension": "笔记习惯", "positive": "", "negative": "有待加强(e.g.可以做笔记，写阅读的时候可以多划关键字)。" }
                ]
            """.trimIndent()
            systemConfigRepository.save(SystemConfig(configKey = "GLOBAL_ANALYSIS_CONFIG_LEARNING_LITERACY", configValue = learningLiteracyAnalysisJson))

            val learningLiteracyCauseJson = "[]" // As none were provided explicitly 
            systemConfigRepository.save(SystemConfig(configKey = "GLOBAL_CAUSE_ANALYSIS_LEARNING_LITERACY", configValue = learningLiteracyCauseJson))
            println("=== Initialized ALL Assessment Analysis Templates ===")
        }


        // Initialize global teacher introductions
        if (systemConfigRepository.findByConfigKey("GLOBAL_TEACHER_INTRODUCTIONS") == null) {
            val defaultTeacherIntros = """
                [
                  {
                    "level": "A级别老师/储备老师",
                    "desc": "1.专业背景：海外QS TOP10院校相关英语，二语习得，教育学或TESOL专业硕士。具备师范类专业背景，或211/985学校本科学历。具备教师资质（教师资格证，CELTA等）\n2.教龄：1-2年"
                  },
                  {
                    "level": "AA级别老师",
                    "desc": "1.专业背景：海外QS TOP10院校相关英语，二语习得，教育学或TESOL专业硕士。具备师范类专业背景，或211/985学校本科学历。具备教师资质（教师资格证，CELTA等）\n2. 教龄：3年及以上国际课程授课经验，擅长基础英语教学"
                  },
                  {
                    "level": "AAA级别老师",
                    "desc": "1.专业背景：海外QS TOP10院校相关英语，二语习得，教育学或TESOL专业硕士。具备师范类专业背景，或211/985学校本科学历。具备教师资质（教师资格证，CELTA等）\n2. 教龄：8年及以上国际课程授课经验，具备丰富的国际学校入学备考经验，陪伴超过50名学生顺利拿到国际高中入学考试offer"
                  }
                ]
            """.trimIndent()
            systemConfigRepository.save(SystemConfig(configKey = "GLOBAL_TEACHER_INTRODUCTIONS", configValue = defaultTeacherIntros))
            println("=== Initialized global teacher introductions ===")
        }

        // Initialize default teaching checklist template
        if (systemConfigRepository.findByConfigKey("GLOBAL_TEACHING_CHECKLIST_TEMPLATE") == null) {
            val defaultChecklist = """
助教课打卡清单
A. Quizlet 单词打卡
B. Reading Explorer 2 阅读打卡
C. 听力听写与跟读打卡
D. 口语打卡
E. 精听打卡
每课学习词汇：50-70
            """.trimIndent()
            systemConfigRepository.save(SystemConfig(configKey = "GLOBAL_TEACHING_CHECKLIST_TEMPLATE", configValue = defaultChecklist))
            println("=== Initialized global teaching checklist template ===")
        }

        // Initialize default course frequency template
        if (systemConfigRepository.findByConfigKey("GLOBAL_COURSE_FREQUENCY_TEMPLATE") == null) {
            val defaultFrequency = "课程频次\n根据备考节奏确定"
            systemConfigRepository.save(SystemConfig(configKey = "GLOBAL_COURSE_FREQUENCY_TEMPLATE", configValue = defaultFrequency))
            println("=== Initialized global course frequency template ===")
        }

        seedConfigIfBlank(
            "GLOBAL_COURSE_PLAN_DEFAULT",
            DEFAULT_COURSE_PLAN_JSON,
            "global course plan default template"
        )
        seedConfigIfBlank(
            "GLOBAL_COURSE_PLAN_NOTE_DEFAULT",
            DEFAULT_COURSE_PLAN_NOTE,
            "global course plan note default"
        )

        // Initialize default plan risk template
        if (systemConfigRepository.findByConfigKey("GLOBAL_PLAN_RISK_TEMPLATE") == null) {
            val defaultPlanRisk = """
1. 课堂参与度低，缺乏互动
原因：由于学生不积极参与课堂互动，通常只用单个词汇和短语回答问题，他们可能没有意识到英语学习不仅仅是做作业，更多的是通过交流和实践来掌握语言。如果课堂上学生不参与讨论或互动，也没有积极主动地提问或回答问题，老师很难通过互动了解学生的真正水平和理解情况。

影响：学生没有通过实际应用英语来巩固所学的知识，因此，尽管可能学了一些词汇和语法，实际上他们并没有真正内化这些内容，导致学习效果不佳。

解决方案：
·  采用互动式教学法：通过小组讨论、角色扮演、模拟场景等方式，增加学生的课堂互动和参与感。这样可以鼓励学生主动开口说英语，逐步提高他们的语言输出能力。
·  鼓励学生提问：让学生感到课堂是一个开放和友好的空间，鼓励他们提问，并给予充分的时间和耐心回答。

2. 对学习目标和内容的认知不清
原因：如果课程目标不够明确或没有与学生的需求和兴趣紧密结合，学生可能无法理解学习内容对自己未来的价值，导致他们缺乏学习的动力和目标感。尤其是学术英语的引入，如果没有适当的过渡，学生可能觉得这些内容过于遥远或者不相关。

影响：学生无法看到自己的学习成果，导致他们对学习产生挫败感，甚至可能认为学习英语没有实际意义，从而产生抗拒心理。

解决方案：
·  与学生的实际生活结合：通过引导学生理解英语学习与他们未来生活的关系，例如留学、旅行、职业发展等，让学生看到英语在实际生活中的应用，增加学习的现实意义。
·  引导学生设定个人学习目标：让学生根据自己的兴趣和未来的计划设定个人的英语学习目标，并与他们讨论如何通过英语达成这些目标。这样，学生会更有动力去学习英语。

3. 时间管理和学习安排不当
原因：很多学生可能缺乏有效的时间管理和学习规划能力，尤其是在初中阶段，可能还未完全适应自主学习的方式。如果学生没有每天定期学习的习惯，或者没有按时完成作业，会造成学习进度缓慢，错过了必要的复习和巩固时间。

影响：学习进度滞后，学生没有足够的时间进行系统复习和巩固，导致知识点没有牢固掌握，学习成果自然无法呈现出来。

解决方案：
·  制定学习计划：帮助学生制定具体的学习计划，并在计划中细化每周和每日的任务。例如，每天安排15-20分钟的英语学习时间，帮助学生养成规律的学习习惯。
·  定期检查学习进度：每周和学生回顾一次他们的学习进度，了解他们是否按计划执行。如果有延误，及时调整计划并帮助学生解决遇到的问题。

4. 课外作业和任务拖延
原因：学生缺乏学习主动性和自律性，常常拖延作业和学习任务，只有在老师催促时才会去做。尤其是对于一些低自我驱动的学生，他们可能并没有建立起自觉完成作业的良好习惯，导致任务不能按时完成，影响课程的进展。

影响：作业和任务的拖延导致学生无法及时巩固课堂上学到的知识，缺乏持续的复习和练习，最终会影响他们的英语能力提高，无法按计划取得学习成果。

解决方案：
·  分阶段的作业设计：避免布置过多的任务或过长的作业，而是将作业拆分成小的部分，并设定明确的截止时间。每次任务完成后，及时给予反馈，帮助学生保持动力。
·  强化作业的即时反馈：及时批改作业并与学生进行一对一反馈，帮助学生了解自己的不足，并提供具体的改进建议。及时的反馈能让学生看到自己的进步，同时避免出现拖延现象。

5. 对错误和反馈的抵触情绪
原因：一些学生对自己犯的错误感到羞愧或者不喜欢被指出错误，尤其是当错误频繁发生时，他们可能产生消极情绪，不愿意接受反馈或纠正。这种情绪可能使他们回避做作业或者不认真复习。

影响：如果学生没有积极接受错误反馈，并加以改正，他们的学习进度就会受到阻碍。错误如果没有及时纠正，容易加深记忆错误，影响学生的长远学习效果。

解决方案：
·  鼓励错误是学习的一部分：要让学生认识到犯错是学习过程中的一部分，是进步的必要条件。在课堂上，老师可以通过正向的语言帮助学生从错误中学习，而不是批评或责备。
·  积极反馈和鼓励：通过提供具体的、建设性的反馈来帮助学生改正错误。反馈时要指出学生做得好的地方，并给出改善建议。这样能帮助学生建立自信，减少对错误的负面情绪。
·  让学生自己发现错误：通过引导学生自己找到错误并进行改正，不仅能够增强他们的学习自主性，还能帮助他们更深刻地理解所学内容。

6. 家庭支持不足
原因：家庭环境对学生学习有很大的影响。如果家长对学生的英语学习不够重视，或者没有提供适当的支持和激励，学生可能缺乏外部的监督和鼓励，导致学习动力减弱。尤其是在学生课外学习时，家长的监督和配合非常重要。

影响：没有足够的家庭支持，学生可能缺乏学习的动力和约束，难以按计划完成作业或进行有效的学习，进而影响整体学习成果。

7. 学习压力过大导致的焦虑
原因：有些学生可能会感到英语学习的压力过大，尤其是在面对较为困难的学术英语内容时。如果学生因为某些任务或目标感到焦虑，他们可能选择逃避学习，甚至不愿意面对课堂上的挑战。

影响：学生无法正面应对挑战，甚至可能因焦虑产生情绪问题，从而影响学习表现和结果。如果教学方法过于急功近利，也可能加剧学生的压力。
解决方法：
关注学生的心理状态：定期与学生交流，了解他们的情绪和心理状况，尤其是在面对学业压力时。帮助学生学会管理学习压力，保持积极的心态。

8. 外部因素（如健康问题、情绪波动等）
原因：学生在学习过程中可能会因为身体健康问题、家庭压力、心理情绪问题等外部因素，导致无法集中注意力，影响学习效率。这些因素可能不容易察觉，但却会对学习产生很大影响。

影响：情绪不佳或健康问题会导致学生的学习注意力和效率下降，无法按计划进行学习和任务完成，学习进展自然会受到影响。

解决方法：
关注学生的心理状态：定期与学生交流，了解他们的情绪和心理状况，尤其是在面对学业压力时。帮助学生学会管理学习压力，保持积极的心态。
            """.trimIndent()
            systemConfigRepository.save(SystemConfig(configKey = "GLOBAL_PLAN_RISK_TEMPLATE", configValue = defaultPlanRisk))
            println("=== Initialized global plan risk template ===")
        }

        // Initialize default student types if the table is empty
        if (studentTypeDictionaryRepository.count() == 0L) {
            val defaults = listOf(
                StudentTypeDictionary(typeCode = "INTL", typeName = "国际学校", sortOrder = 1, capabilityMatrixCsv = generateCsvForType("INTL"), associatedColumns = getAssociatedColumns("INTL")),
                StudentTypeDictionary(typeCode = "TRANSITION_INTL", typeName = "体制内转国际", sortOrder = 2, capabilityMatrixCsv = generateCsvForType("TRANSITION_INTL"), associatedColumns = getAssociatedColumns("TRANSITION_INTL")),
                StudentTypeDictionary(typeCode = "TRANSITION_HKDSE", typeName = "体制内转HKDSE", sortOrder = 3, capabilityMatrixCsv = generateCsvForType("TRANSITION_HKDSE"), associatedColumns = getAssociatedColumns("TRANSITION_HKDSE")),
                StudentTypeDictionary(typeCode = "TRANSITION_HANGZHOU_INTL", typeName = "体制内转杭州国际学校", sortOrder = 4, capabilityMatrixCsv = generateCsvForType("TRANSITION_HANGZHOU_INTL"), associatedColumns = getAssociatedColumns("TRANSITION_HANGZHOU_INTL")),
                StudentTypeDictionary(typeCode = "DOMESTIC", typeName = "体制内", sortOrder = 5, capabilityMatrixCsv = generateCsvForType("DOMESTIC"), associatedColumns = getAssociatedColumns("DOMESTIC"))
            )
            studentTypeDictionaryRepository.saveAll(defaults)
            println("=== Initialized default student types in database with CSV matrices and associated columns ===")
        } else {
            // Migration for existing records missing CSV
            val existingTypes = studentTypeDictionaryRepository.findAll()
            var modified = false
            existingTypes.forEach { type ->
                if (type.capabilityMatrixCsv.isNullOrBlank()) {
                    type.capabilityMatrixCsv = generateCsvForType(type.typeCode)
                    modified = true
                }
                if (type.associatedColumns.isNullOrBlank()) {
                    type.associatedColumns = getAssociatedColumns(type.typeCode)
                    modified = true
                }
            }
            if (modified) {
                studentTypeDictionaryRepository.saveAll(existingTypes)
                println("=== Migrated existing student types to include CSV matrices and/or associated columns ===")
            }
        }
    }
}
