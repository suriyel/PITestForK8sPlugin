package com.distributed.pitest.report;

import com.distributed.pitest.model.AggregatedResult;
import com.distributed.pitest.model.MutationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * 标准XML报告生成器
 */
public class StandardXmlReportGenerator {
    private static final Logger logger = LoggerFactory.getLogger(StandardXmlReportGenerator.class);

    /**
     * 生成XML格式的变异测试报告
     *
     * @param result 聚合的测试结果
     * @param outputDirectory 输出目录
     * @throws IOException 如果写入报告时发生错误
     */
    public void generateReport(AggregatedResult result, File outputDirectory) throws IOException {
        File xmlFile = new File(outputDirectory, "mutations.xml");
        logger.info("Generating XML report at {}", xmlFile.getAbsolutePath());

        try (PrintWriter writer = new PrintWriter(new FileWriter(xmlFile))) {
            writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            writer.println("<mutations>");

            // 按类输出突变结果
            for (Map.Entry<String, List<MutationResult>> entry : result.getMutationsByClass().entrySet()) {
                List<MutationResult> mutations = entry.getValue();

                for (MutationResult mutation : mutations) {
                    writer.println("  <mutation detected=\"" + mutation.isDetected() +
                            "\" status=\"" + mutation.getStatus() + "\">");
                    writer.println("    <sourceFile>" + escapeXml(mutation.getSourceFile()) + "</sourceFile>");
                    writer.println("    <mutatedClass>" + escapeXml(mutation.getMutatedClass()) + "</mutatedClass>");
                    writer.println("    <mutatedMethod>" + escapeXml(mutation.getMutatedMethod()) + "</mutatedMethod>");
                    writer.println("    <methodDescription>" + escapeXml(mutation.getMethodDescription()) +
                            "</methodDescription>");
                    writer.println("    <lineNumber>" + mutation.getLineNumber() + "</lineNumber>");
                    writer.println("    <mutator>" + escapeXml(mutation.getMutator()) + "</mutator>");

                    writer.println("    <indexes>");
                    for (int index : mutation.getIndexes()) {
                        writer.println("      <index>" + index + "</index>");
                    }
                    writer.println("    </indexes>");

                    writer.println("    <blocks>");
                    for (int block : mutation.getBlocks()) {
                        writer.println("      <block>" + block + "</block>");
                    }
                    writer.println("    </blocks>");

                    if (mutation.getKillingTest() != null && !mutation.getKillingTest().isEmpty()) {
                        writer.println("    <killingTest>" + escapeXml(mutation.getKillingTest()) + "</killingTest>");
                    }

                    writer.println("    <description>" + escapeXml(mutation.getDescription()) + "</description>");

                    // 添加扩展信息（如果有）
                    if (mutation.getPackageName() != null && !mutation.getPackageName().isEmpty()) {
                        writer.println("    <packageName>" + escapeXml(mutation.getPackageName()) + "</packageName>");
                    }

                    if (mutation.getFirstLine() > 0) {
                        writer.println("    <firstLine>" + mutation.getFirstLine() + "</firstLine>");
                    }

                    if (mutation.getLastLine() > 0) {
                        writer.println("    <lastLine>" + mutation.getLastLine() + "</lastLine>");
                    }

                    writer.println("  </mutation>");
                }
            }

            writer.println("</mutations>");
        }

        logger.info("XML report generated successfully");
    }

    /**
     * XML转义
     */
    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }

        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}