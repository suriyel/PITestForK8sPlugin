package com.distributed.pitest.report;

import com.distributed.pitest.model.AggregatedResult;
import com.distributed.pitest.model.MutationResult;
import com.distributed.pitest.service.MutationSourceService;
import com.distributed.pitest.service.SourceCodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 完整增强的报告生成器，集成了所有增强功能
 */
public class CompleteEnhancedReportGenerator implements ReportGenerator {
    private static final Logger logger = LoggerFactory.getLogger(CompleteEnhancedReportGenerator.class);
    private final HtmlReportTemplateGenerator templateGenerator;
    private final SourceCodeService sourceCodeService;
    private final MutationSourceService mutationSourceService;

    public CompleteEnhancedReportGenerator(File projectBaseDir) {
        this.templateGenerator = new HtmlReportTemplateGenerator();
        this.sourceCodeService = new SourceCodeService(projectBaseDir);
        this.mutationSourceService = new MutationSourceService(projectBaseDir);
    }

    @Override
    public void generateReport(AggregatedResult result, File outputDirectory) {
        logger.info("Generating complete enhanced report in {}", outputDirectory);

        try {
            // 确保输出目录存在
            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs();
            }

            // 增强突变结果
            enhanceMutationResults(result);

            // 生成报告
            generateHtmlReport(result, outputDirectory);

            // 生成XML报告
            generateXmlReport(result, outputDirectory);

            // 生成摘要报告
            generateSummaryReport(result, outputDirectory);

            logger.info("Complete enhanced report generation finished");

        } catch (Exception e) {
            logger.error("Error generating complete enhanced report", e);
        }
    }

    /**
     * 增强突变结果，添加源代码和变异代码信息
     */
    private void enhanceMutationResults(AggregatedResult result) {
        logger.info("Enhancing mutation results with source code information");

        for (Map.Entry<String, List<MutationResult>> entry : result.getMutationsByClass().entrySet()) {
            String className = entry.getKey();
            List<MutationResult> mutations = entry.getValue();

            // 使用变异源服务增强结果
            List<MutationResult> enhancedMutations = mutationSourceService.enhanceMutationResults(mutations);

            // 替换原有列表
            if (enhancedMutations.size() == mutations.size()) {
                mutations.clear();
                mutations.addAll(enhancedMutations);
            } else {
                logger.warn("Enhanced mutations count mismatch for class: {}", className);
            }
        }
    }

    /**
     * 生成HTML报告
     */
    private void generateHtmlReport(AggregatedResult result, File outputDirectory) throws IOException {
        logger.info("Generating HTML report");

        // 创建HTML目录
        File htmlDir = new File(outputDirectory, "html");
        if (!htmlDir.exists()) {
            htmlDir.mkdirs();
        }

        // 生成索引页面
        templateGenerator.generateIndexPage(result, htmlDir);

        // 计算类摘要
        Map<String, HtmlReportTemplateGenerator.ClassSummary> classSummaries = calculateClassSummaries(result);

        // 为每个类生成报告
        for (Map.Entry<String, List<MutationResult>> entry : result.getMutationsByClass().entrySet()) {
            String className = entry.getKey();
            List<MutationResult> mutations = entry.getValue();

            HtmlReportTemplateGenerator.ClassSummary summary = classSummaries.get(className);
            if (summary == null) {
                logger.warn("Missing summary for class: {}", className);
                continue;
            }

            // 生成类报告
            templateGenerator.generateClassReport(className, mutations, htmlDir, summary);

            // 生成源代码视图
            generateSourceView(className, mutations, htmlDir);
        }
    }

    /**
     * 生成源代码视图
     */
    private void generateSourceView(String className, List<MutationResult> mutations, File htmlDir) throws IOException {
        if (mutations.isEmpty()) {
            return;
        }

        // 获取源文件名
        String sourceFileName = mutations.get(0).getSourceFile();
        if (sourceFileName == null || sourceFileName.isEmpty()) {
            logger.warn("No source file name available for class: {}", className);
            return;
        }

        // 获取源代码
        String sourceCode = sourceCodeService.getSourceCode(className, sourceFileName);
        if (sourceCode == null || sourceCode.isEmpty()) {
            logger.warn("Failed to retrieve source code for class: {}", className);
            return;
        }

        // 生成源代码视图
        templateGenerator.generateSourceView(className, sourceCode, htmlDir);
    }

    /**
     * 生成XML报告
     */
    private void generateXmlReport(AggregatedResult result, File outputDirectory) throws IOException {
        logger.info("Generating XML report");

        // 使用标准XML生成器
        StandardXmlReportGenerator xmlGenerator = new StandardXmlReportGenerator();
        xmlGenerator.generateReport(result, outputDirectory);
    }

    /**
     * 生成摘要报告
     */
    private void generateSummaryReport(AggregatedResult result, File outputDirectory) throws IOException {
        logger.info("Generating summary report");

        // 使用标准摘要生成器
        StandardSummaryReportGenerator summaryGenerator = new StandardSummaryReportGenerator();
        summaryGenerator.generateReport(result, outputDirectory);
    }

    /**
     * 计算类摘要信息
     */
    private Map<String, HtmlReportTemplateGenerator.ClassSummary> calculateClassSummaries(AggregatedResult result) {
        Map<String, HtmlReportTemplateGenerator.ClassSummary> summaries = new HashMap<>();

        for (Map.Entry<String, List<MutationResult>> entry : result.getMutationsByClass().entrySet()) {
            String className = entry.getKey();
            List<MutationResult> mutations = entry.getValue();

            int total = mutations.size();
            int killed = (int) mutations.stream().filter(MutationResult::isDetected).count();
            double score = total > 0 ? (double) killed / total * 100.0 : 0.0;

            summaries.put(className, new HtmlReportTemplateGenerator.ClassSummary(total, killed, score));
        }

        return summaries;
    }
}