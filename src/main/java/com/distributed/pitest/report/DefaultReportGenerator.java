package com.distributed.pitest.report;

import com.distributed.pitest.model.AggregatedResult;
import com.distributed.pitest.model.MutationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 默认报告生成器实现，生成HTML和XML格式的报告
 */
public class DefaultReportGenerator implements ReportGenerator {
    private static final Logger logger = LoggerFactory.getLogger(DefaultReportGenerator.class);

    @Override
    public void generateReport(AggregatedResult result, File outputDirectory) {
        logger.info("Generating reports in {}", outputDirectory);

        try {
            // 确保输出目录存在
            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs();
            }

            // 生成XML报告
            generateXmlReport(result, outputDirectory);

            // 生成HTML报告
            generateHtmlReport(result, outputDirectory);

            // 生成摘要报告
            generateSummaryReport(result, outputDirectory);

            logger.info("Reports generated successfully");

        } catch (Exception e) {
            logger.error("Error generating reports", e);
        }
    }

    private void generateXmlReport(AggregatedResult result, File outputDirectory) throws IOException {
        File xmlFile = new File(outputDirectory, "mutations.xml");
        logger.info("Generating XML report at {}", xmlFile.getAbsolutePath());

        try (PrintWriter writer = new PrintWriter(new FileWriter(xmlFile))) {
            writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            writer.println("<mutations>");

            // 按类输出突变结果
            for (Map.Entry<String, List<MutationResult>> entry : result.getMutationsByClass().entrySet()) {
                String className = entry.getKey();
                List<MutationResult> mutations = entry.getValue();

                for (MutationResult mutation : mutations) {
                    writer.println("  <mutation detected=\"" + mutation.isDetected() + "\" status=\"" + mutation.getStatus() + "\">");
                    writer.println("    <sourceFile>" + getSourceFileName(className) + "</sourceFile>");
                    writer.println("    <mutatedClass>" + mutation.getMutatedClass() + "</mutatedClass>");
                    writer.println("    <mutatedMethod>" + mutation.getMutatedMethod() + "</mutatedMethod>");
                    writer.println("    <methodDescription>...</methodDescription>");
                    writer.println("    <lineNumber>" + mutation.getLineNumber() + "</lineNumber>");
                    writer.println("    <mutator>" + mutation.getMutator() + "</mutator>");
                    writer.println("    <indexes><index>0</index></indexes>");
                    writer.println("    <blocks><block>0</block></blocks>");
                    writer.println("    <killingTest>" + mutation.getKillingTest() + "</killingTest>");
                    writer.println("    <description>" + mutation.getDescription() + "</description>");
                    writer.println("  </mutation>");
                }
            }

            writer.println("</mutations>");
        }
    }

    private void generateHtmlReport(AggregatedResult result, File outputDirectory) throws IOException {
        File htmlDir = new File(outputDirectory, "html");
        if (!htmlDir.exists()) {
            htmlDir.mkdirs();
        }

        // 生成索引文件
        File indexFile = new File(htmlDir, "index.html");
        logger.info("Generating HTML index at {}", indexFile.getAbsolutePath());

        try (PrintWriter writer = new PrintWriter(new FileWriter(indexFile))) {
            writer.println("<!DOCTYPE html>");
            writer.println("<html>");
            writer.println("<head>");
            writer.println("  <title>PIT Mutation Report</title>");
            writer.println("  <style>");
            writer.println("    body { font-family: Arial, sans-serif; margin: 20px; }");
            writer.println("    h1 { color: #333; }");
            writer.println("    table { border-collapse: collapse; width: 100%; }");
            writer.println("    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }");
            writer.println("    th { background-color: #f2f2f2; }");
            writer.println("    tr:nth-child(even) { background-color: #f9f9f9; }");
            writer.println("    .summary { margin-bottom: 20px; }");
            writer.println("    .passed { color: green; }");
            writer.println("    .failed { color: red; }");
            writer.println("  </style>");
            writer.println("</head>");
            writer.println("<body>");
            writer.println("  <h1>PIT Mutation Test Report</h1>");
            writer.println("  <div class=\"summary\">");
            writer.println("    <h2>Summary</h2>");
            writer.println("    <p>Generated at: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "</p>");
            writer.println("    <p>Total mutations: " + result.getTotalMutations() + "</p>");
            writer.println("    <p>Killed mutations: " + result.getKilledMutations() + "</p>");
            writer.println("    <p>Mutation Score: " + String.format("%.2f%%", result.getMutationScore()) + "</p>");
            writer.println("  </div>");

            if (result.hasErrors()) {
                writer.println("  <div class=\"errors\">");
                writer.println("    <h2>Errors</h2>");
                writer.println("    <ul>");
                for (String error : result.getErrors()) {
                    writer.println("      <li class=\"failed\">" + error + "</li>");
                }
                writer.println("    </ul>");
                writer.println("  </div>");
            }

            writer.println("  <h2>Classes</h2>");
            writer.println("  <table>");
            writer.println("    <tr>");
            writer.println("      <th>Class</th>");
            writer.println("      <th>Total Mutations</th>");
            writer.println("      <th>Killed</th>");
            writer.println("      <th>Score</th>");
            writer.println("    </tr>");

            // 按类输出汇总信息
            for (Map.Entry<String, List<MutationResult>> entry : result.getMutationsByClass().entrySet()) {
                String className = entry.getKey();
                List<MutationResult> mutations = entry.getValue();

                int total = mutations.size();
                int killed = (int) mutations.stream().filter(MutationResult::isDetected).count();
                double score = total > 0 ? (double) killed / total * 100.0 : 0.0;

                writer.println("    <tr>");
                writer.println("      <td><a href=\"classes/" + className.replace('.', '/') + ".html\">" + className + "</a></td>");
                writer.println("      <td>" + total + "</td>");
                writer.println("      <td>" + killed + "</td>");
                writer.println("      <td>" + String.format("%.2f%%", score) + "</td>");
                writer.println("    </tr>");
            }

            writer.println("  </table>");
            writer.println("</body>");
            writer.println("</html>");
        }

        // 为每个类生成详细报告
        // 简化实现，实际中需要为每个类生成单独的HTML文件
    }

    private void generateSummaryReport(AggregatedResult result, File outputDirectory) throws IOException {
        File summaryFile = new File(outputDirectory, "summary.txt");
        logger.info("Generating summary report at {}", summaryFile.getAbsolutePath());

        try (PrintWriter writer = new PrintWriter(new FileWriter(summaryFile))) {
            writer.println("PIT Mutation Testing Summary");
            writer.println("=============================");
            writer.println();
            writer.println("Generated at: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            writer.println();
            writer.println("Total mutations: " + result.getTotalMutations());
            writer.println("Killed mutations: " + result.getKilledMutations());
            writer.println("Mutation Score: " + String.format("%.2f%%", result.getMutationScore()));
            writer.println();

            if (result.hasErrors()) {
                writer.println("Errors:");
                for (String error : result.getErrors()) {
                    writer.println("- " + error);
                }
                writer.println();
            }

            writer.println("Classes:");
            for (Map.Entry<String, List<MutationResult>> entry : result.getMutationsByClass().entrySet()) {
                String className = entry.getKey();
                List<MutationResult> mutations = entry.getValue();

                int total = mutations.size();
                int killed = (int) mutations.stream().filter(MutationResult::isDetected).count();
                double score = total > 0 ? (double) killed / total * 100.0 : 0.0;

                writer.println(className + ": " + killed + "/" + total + " (" + String.format("%.2f%%", score) + ")");
            }
        }
    }

    private String getSourceFileName(String className) {
        return className.substring(className.lastIndexOf('.') + 1) + ".java";
    }
}