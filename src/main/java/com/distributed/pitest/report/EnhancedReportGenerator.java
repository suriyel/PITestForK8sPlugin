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
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 增强的报告生成器，生成包含详细变异信息的报告
 */
public class EnhancedReportGenerator implements ReportGenerator {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedReportGenerator.class);

    @Override
    public void generateReport(AggregatedResult result, File outputDirectory) {
        logger.info("Generating enhanced reports in {}", outputDirectory);

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

            // 生成源代码查看器
            generateSourceCodeViewer(result, outputDirectory);

            logger.info("Enhanced reports generated successfully");

        } catch (Exception e) {
            logger.error("Error generating enhanced reports", e);
        }
    }

    /**
     * 生成XML报告
     */
    private void generateXmlReport(AggregatedResult result, File outputDirectory) throws IOException {
        File xmlFile = new File(outputDirectory, "mutations.xml");
        logger.info("Generating enhanced XML report at {}", xmlFile.getAbsolutePath());

        try (PrintWriter writer = new PrintWriter(new FileWriter(xmlFile))) {
            writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            writer.println("<mutations>");

            // 按类输出突变结果
            for (Map.Entry<String, List<MutationResult>> entry : result.getMutationsByClass().entrySet()) {
                String className = entry.getKey();
                List<MutationResult> mutations = entry.getValue();

                for (MutationResult mutation : mutations) {
                    writer.println("  <mutation detected=\"" + mutation.isDetected() +
                            "\" status=\"" + mutation.getStatus() + "\">");
                    writer.println("    <sourceFile>" + mutation.getSourceFile() + "</sourceFile>");
                    writer.println("    <mutatedClass>" + mutation.getMutatedClass() + "</mutatedClass>");
                    writer.println("    <mutatedMethod>" + mutation.getMutatedMethod() + "</mutatedMethod>");
                    writer.println("    <methodDescription>" + escapeXml(mutation.getMethodDescription()) +
                            "</methodDescription>");
                    writer.println("    <lineNumber>" + mutation.getLineNumber() + "</lineNumber>");
                    writer.println("    <mutator>" + mutation.getMutator() + "</mutator>");

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
                        writer.println("    <killingTest>" + mutation.getKillingTest() + "</killingTest>");
                    }

                    writer.println("    <description>" + escapeXml(mutation.getDescription()) + "</description>");

                    // 添加增强的信息
                    if (mutation.getOriginalCode() != null && !mutation.getOriginalCode().isEmpty()) {
                        writer.println("    <originalCode><![CDATA[" + mutation.getOriginalCode() + "]]></originalCode>");
                    }

                    if (mutation.getMutatedCode() != null && !mutation.getMutatedCode().isEmpty()) {
                        writer.println("    <mutatedCode><![CDATA[" + mutation.getMutatedCode() + "]]></mutatedCode>");
                    }

                    writer.println("    <packageName>" + mutation.getPackageName() + "</packageName>");
                    writer.println("    <firstLine>" + mutation.getFirstLine() + "</firstLine>");
                    writer.println("    <lastLine>" + mutation.getLastLine() + "</lastLine>");

                    writer.println("  </mutation>");
                }
            }

            writer.println("</mutations>");
        }
    }

    /**
     * 生成HTML报告
     */
    private void generateHtmlReport(AggregatedResult result, File outputDirectory) throws IOException {
        File htmlDir = new File(outputDirectory, "html");
        if (!htmlDir.exists()) {
            htmlDir.mkdirs();
        }

        // 生成索引文件
        File indexFile = new File(htmlDir, "index.html");
        logger.info("Generating enhanced HTML index at {}", indexFile.getAbsolutePath());

        Map<String, ClassSummary> classSummaries = calculateClassSummaries(result);

        try (PrintWriter writer = new PrintWriter(new FileWriter(indexFile))) {
            writer.println("<!DOCTYPE html>");
            writer.println("<html>");
            writer.println("<head>");
            writer.println("  <title>PIT Mutation Test Report</title>");
            writer.println("  <link rel=\"stylesheet\" href=\"style.css\">");
            writer.println("  <script src=\"script.js\"></script>");
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
            writer.println("  <table class=\"mutations\">");
            writer.println("    <thead>");
            writer.println("      <tr>");
            writer.println("        <th>Class</th>");
            writer.println("        <th>Total Mutations</th>");
            writer.println("        <th>Killed</th>");
            writer.println("        <th>Score</th>");
            writer.println("      </tr>");
            writer.println("    </thead>");
            writer.println("    <tbody>");

            // 按类输出汇总信息
            for (Map.Entry<String, ClassSummary> entry : classSummaries.entrySet()) {
                String className = entry.getKey();
                ClassSummary summary = entry.getValue();

                // 替换.为/以创建正确的路径
                String classPath = className.replace('.', '/');
                String reportPath = "classes/" + classPath + ".html";

                writer.println("    <tr>");
                writer.println("      <td><a href=\"" + reportPath + "\">" + className + "</a></td>");
                writer.println("      <td>" + summary.total + "</td>");
                writer.println("      <td>" + summary.killed + "</td>");
                writer.println("      <td>" + String.format("%.2f%%", summary.score) + "</td>");
                writer.println("    </tr>");
            }

            writer.println("    </tbody>");
            writer.println("  </table>");
            writer.println("</body>");
            writer.println("</html>");
        }

        // 生成CSS样式
        generateCssFile(htmlDir);

        // 生成JavaScript
        generateJavaScript(htmlDir);

        // 为每个类生成详细报告
        for (Map.Entry<String, List<MutationResult>> entry : result.getMutationsByClass().entrySet()) {
            String className = entry.getKey();
            List<MutationResult> mutations = entry.getValue();

            generateClassReport(className, mutations, htmlDir, classSummaries.get(className));
        }
    }

    /**
     * 生成每个类的详细报告
     */
    private void generateClassReport(String className, List<MutationResult> mutations,
                                     File htmlDir, ClassSummary summary) throws IOException {
        // 创建类目录结构
        String classPath = className.replace('.', '/');
        File classDir = new File(htmlDir, "classes/" + classPath.substring(0, classPath.lastIndexOf('/')));
        classDir.mkdirs();

        // 创建类报告文件
        File classReportFile = new File(htmlDir, "classes/" + classPath + ".html");

        try (PrintWriter writer = new PrintWriter(new FileWriter(classReportFile))) {
            writer.println("<!DOCTYPE html>");
            writer.println("<html>");
            writer.println("<head>");
            writer.println("  <title>PIT Mutation Report - " + className + "</title>");
            writer.println("  <link rel=\"stylesheet\" href=\"../../style.css\">");
            writer.println("  <script src=\"../../script.js\"></script>");
            writer.println("</head>");
            writer.println("<body>");
            writer.println("  <h1>PIT Mutation Report</h1>");
            writer.println("  <h2>Class: " + className + "</h2>");

            writer.println("  <div class=\"class-summary\">");
            writer.println("    <p>Total mutations: " + summary.total + "</p>");
            writer.println("    <p>Killed mutations: " + summary.killed + "</p>");
            writer.println("    <p>Mutation Score: " + String.format("%.2f%%", summary.score) + "</p>");
            writer.println("    <p><a href=\"../../index.html\">Back to index</a></p>");
            writer.println("  </div>");

            // 按方法分组
            Map<String, List<MutationResult>> mutationsByMethod = mutations.stream()
                    .collect(Collectors.groupingBy(MutationResult::getMutatedMethod));

            // 输出每个方法的变异
            for (Map.Entry<String, List<MutationResult>> methodEntry : mutationsByMethod.entrySet()) {
                String methodName = methodEntry.getKey();
                List<MutationResult> methodMutations = methodEntry.getValue();

                writer.println("  <div class=\"method\">");
                writer.println("    <h3>Method: " + methodName + "</h3>");

                // 查找方法的源代码范围
                int firstLine = Integer.MAX_VALUE;
                int lastLine = Integer.MIN_VALUE;
                for (MutationResult mutation : methodMutations) {
                    if (mutation.getFirstLine() > 0 && mutation.getFirstLine() < firstLine) {
                        firstLine = mutation.getFirstLine();
                    }
                    if (mutation.getLastLine() > 0 && mutation.getLastLine() > lastLine) {
                        lastLine = mutation.getLastLine();
                    }
                }

                // 如果有有效的行号范围，尝试展示源代码
                if (firstLine < Integer.MAX_VALUE && lastLine > Integer.MIN_VALUE) {
                    writer.println("    <p>Lines: " + firstLine + " - " + lastLine + "</p>");

                    // 尝试获取源代码
                    MutationResult firstMutation = methodMutations.get(0);
                    File sourceFile = findSourceFile(htmlDir, firstMutation);
                    if (sourceFile != null && sourceFile.exists()) {
                        writer.println("    <div class=\"source-code\">");
                        writer.println("      <pre><code>");
                        writeSourceWithHighlights(writer, sourceFile, firstLine, lastLine, methodMutations);
                        writer.println("      </code></pre>");
                        writer.println("    </div>");
                    }
                }

                // 输出变异表格
                writer.println("    <table class=\"mutations\">");
                writer.println("      <thead>");
                writer.println("        <tr>");
                writer.println("          <th>Line</th>");
                writer.println("          <th>Mutator</th>");
                writer.println("          <th>Description</th>");
                writer.println("          <th>Status</th>");
                writer.println("          <th>Killing Test</th>");
                writer.println("        </tr>");
                writer.println("      </thead>");
                writer.println("      <tbody>");

                for (MutationResult mutation : methodMutations) {
                    String statusClass = mutation.isDetected() ? "killed" : "survived";

                    writer.println("        <tr class=\"" + statusClass + "\">");
                    writer.println("          <td>" + mutation.getLineNumber() + "</td>");
                    writer.println("          <td>" + simplifyMutatorName(mutation.getMutator()) + "</td>");
                    writer.println("          <td>" + mutation.getDescription() + "</td>");
                    writer.println("          <td>" + mutation.getStatus() + "</td>");
                    writer.println("          <td>" + (mutation.getKillingTest() != null ?
                            mutation.getKillingTest() : "") + "</td>");
                    writer.println("        </tr>");

                    // 如果有原始代码和变异代码，显示对比
                    if (mutation.getOriginalCode() != null && !mutation.getOriginalCode().isEmpty() &&
                            mutation.getMutatedCode() != null && !mutation.getMutatedCode().isEmpty()) {
                        writer.println("        <tr class=\"code-diff\">");
                        writer.println("          <td colspan=\"5\">");
                        writer.println("            <div class=\"diff-container\">");
                        writer.println("              <div class=\"original\">");
                        writer.println("                <h4>Original Code:</h4>");
                        writer.println("                <pre><code>" + escapeHtml(mutation.getOriginalCode()) + "</code></pre>");
                        writer.println("              </div>");
                        writer.println("              <div class=\"mutated\">");
                        writer.println("                <h4>Mutated Code:</h4>");
                        writer.println("                <pre><code>" + escapeHtml(mutation.getMutatedCode()) + "</code></pre>");
                        writer.println("              </div>");
                        writer.println("            </div>");
                        writer.println("          </td>");
                        writer.println("        </tr>");
                    }
                }

                writer.println("      </tbody>");
                writer.println("    </table>");
                writer.println("  </div>");
            }

            writer.println("</body>");
            writer.println("</html>");
        }
    }

    /**
     * 查找源文件
     */
    private File findSourceFile(File htmlDir, MutationResult mutation) {
        String packagePath = mutation.getPackageName().replace('.', '/');
        String sourceFileName = mutation.getSourceFile();

        // 检查report目录中的sources文件夹
        File sourcesDir = new File(htmlDir.getParentFile(), "sources");
        File sourceFile = new File(sourcesDir, packagePath + "/" + sourceFileName);

        if (sourceFile.exists()) {
            return sourceFile;
        }

        return null;
    }

    /**
     * 写入源代码并高亮变异行
     */
    private void writeSourceWithHighlights(PrintWriter writer, File sourceFile,
                                           int firstLine, int lastLine,
                                           List<MutationResult> mutations) throws IOException {
        List<String> lines = Files.readAllLines(sourceFile.toPath());

        // 创建行号到变异的映射
        Map<Integer, List<MutationResult>> mutationsByLine = mutations.stream()
                .collect(Collectors.groupingBy(MutationResult::getLineNumber));

        // 输出相关行，并高亮突变行
        for (int i = Math.max(0, firstLine - 1); i < Math.min(lines.size(), lastLine + 1); i++) {
            int lineNum = i + 1;  // 1-based line number
            String line = lines.get(i);

            if (mutationsByLine.containsKey(lineNum)) {
                // 计算高亮状态
                boolean anyKilled = mutationsByLine.get(lineNum).stream()
                        .anyMatch(MutationResult::isDetected);
                boolean allKilled = mutationsByLine.get(lineNum).stream()
                        .allMatch(MutationResult::isDetected);

                String highlightClass = allKilled ? "fully-covered" :
                        anyKilled ? "partially-covered" : "not-covered";

                writer.println("<span class=\"line-number\">" + lineNum + "</span>" +
                        "<span class=\"line " + highlightClass + "\">" +
                        escapeHtml(line) + "</span>");
            } else {
                writer.println("<span class=\"line-number\">" + lineNum + "</span>" +
                        "<span class=\"line\">" + escapeHtml(line) + "</span>");
            }
        }
    }

    /**
     * 生成CSS样式文件
     */
    private void generateCssFile(File htmlDir) throws IOException {
        File cssFile = new File(htmlDir, "style.css");

        try (PrintWriter writer = new PrintWriter(new FileWriter(cssFile))) {
            writer.println("body { font-family: Arial, sans-serif; margin: 20px; line-height: 1.5; }");
            writer.println("h1 { color: #333; }");
            writer.println("h2 { color: #444; margin-top: 30px; }");
            writer.println("h3 { color: #555; }");
            writer.println("table.mutations { border-collapse: collapse; width: 100%; margin-top: 20px; }");
            writer.println("table.mutations th, table.mutations td { border: 1px solid #ddd; padding: 8px; text-align: left; }");
            writer.println("table.mutations th { background-color: #f2f2f2; }");
            writer.println("table.mutations tr:nth-child(even) { background-color: #f9f9f9; }");
            writer.println("table.mutations tr.killed { background-color: #dff0d8; }");
            writer.println("table.mutations tr.survived { background-color: #f2dede; }");
            writer.println(".summary { margin-bottom: 20px; padding: 10px; background-color: #f5f5f5; border-radius: 5px; }");
            writer.println(".class-summary { margin-bottom: 20px; padding: 10px; background-color: #f0f8ff; border-radius: 5px; }");
            writer.println(".killed, .fully-covered { color: green; }");
            writer.println(".survived, .not-covered { color: red; }");
            writer.println(".partially-covered { color: orange; }");
            writer.println(".line-number { display: inline-block; width: 40px; text-align: right; padding-right: 10px; color: #999; }");
            writer.println(".source-code { margin: 20px 0; padding: 10px; background-color: #f8f8f8; border: 1px solid #ddd; overflow-x: auto; }");
            writer.println(".source-code pre { margin: 0; }");
            writer.println(".diff-container { display: flex; margin: 10px 0; }");
            writer.println(".original, .mutated { flex: 1; padding: 10px; margin: 0 5px; background-color: #f8f8f8; border: 1px solid #ddd; }");
            writer.println(".original h4, .mutated h4 { margin-top: 0; }");
            writer.println(".original pre, .mutated pre { margin: 0; }");
            writer.println(".code-diff { background-color: #f5f5f5; }");
            writer.println(".line { display: block; }");
            writer.println(".line.fully-covered { background-color: #dff0d8; }");
            writer.println(".line.partially-covered { background-color: #fcf8e3; }");
            writer.println(".line.not-covered { background-color: #f2dede; }");
        }
    }

    /**
     * 生成JavaScript文件
     */
    private void generateJavaScript(File htmlDir) throws IOException {
        File jsFile = new File(htmlDir, "script.js");

        try (PrintWriter writer = new PrintWriter(new FileWriter(jsFile))) {
            writer.println("document.addEventListener('DOMContentLoaded', function() {");
            writer.println("  // Add toggle functionality for code diffs");
            writer.println("  var codeDiffs = document.querySelectorAll('.code-diff');");
            writer.println("  codeDiffs.forEach(function(diff) {");
            writer.println("    diff.style.display = 'none';");
            writer.println("    var tr = diff.previousElementSibling;");
            writer.println("    tr.style.cursor = 'pointer';");
            writer.println("    tr.addEventListener('click', function() {");
            writer.println("      var style = diff.style.display;");
            writer.println("      diff.style.display = style === 'none' ? 'table-row' : 'none';");
            writer.println("    });");
            writer.println("  });");
            writer.println("});");
        }
    }

    /**
     * 生成摘要报告
     */
    private void generateSummaryReport(AggregatedResult result, File outputDirectory) throws IOException {
        File summaryFile = new File(outputDirectory, "summary.txt");
        logger.info("Generating enhanced summary report at {}", summaryFile.getAbsolutePath());

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

            Map<String, ClassSummary> classSummaries = calculateClassSummaries(result);
            for (Map.Entry<String, ClassSummary> entry : classSummaries.entrySet()) {
                String className = entry.getKey();
                ClassSummary summary = entry.getValue();

                writer.println(className + ": " + summary.killed + "/" + summary.total +
                        " (" + String.format("%.2f%%", summary.score) + ")");
            }

            writer.println();
            writer.println("Mutator Summary:");

            Map<String, MutatorSummary> mutatorSummaries = calculateMutatorSummaries(result);
            for (Map.Entry<String, MutatorSummary> entry : mutatorSummaries.entrySet()) {
                String mutator = entry.getKey();
                MutatorSummary summary = entry.getValue();

                writer.println(simplifyMutatorName(mutator) + ": " + summary.killed + "/" + summary.total +
                        " (" + String.format("%.2f%%", summary.score) + ")");
            }
        }
    }

    /**
     * 生成源代码浏览器
     */
    private void generateSourceCodeViewer(AggregatedResult result, File outputDirectory) throws IOException {
        // 创建源代码目录
        File sourcesDir = new File(outputDirectory, "sources");
        if (!sourcesDir.exists()) {
            return;  // 如果没有源代码，则跳过
        }

        // 创建源代码索引文件
        File indexFile = new File(sourcesDir, "index.html");

        try (PrintWriter writer = new PrintWriter(new FileWriter(indexFile))) {
            writer.println("<!DOCTYPE html>");
            writer.println("<html>");
            writer.println("<head>");
            writer.println("  <title>PIT Source Code Browser</title>");
            writer.println("  <link rel=\"stylesheet\" href=\"../html/style.css\">");
            writer.println("</head>");
            writer.println("<body>");
            writer.println("  <h1>PIT Source Code Browser</h1>");
            writer.println("  <p><a href=\"../html/index.html\">Back to report index</a></p>");

            writer.println("  <h2>Packages</h2>");
            writer.println("  <ul>");

            // 获取所有不同的包
            Set<String> packages = new java.util.TreeSet<>();
            for (String className : result.getMutationsByClass().keySet()) {
                int lastDot = className.lastIndexOf('.');
                if (lastDot > 0) {
                    packages.add(className.substring(0, lastDot));
                }
            }

            // 输出包列表
            for (String pkg : packages) {
                String pkgPath = pkg.replace('.', '/');
                writer.println("    <li><a href=\"" + pkgPath + "/index.html\">" + pkg + "</a></li>");

                // 为每个包创建索引
                createPackageIndex(pkg, result, sourcesDir);
            }

            writer.println("  </ul>");
            writer.println("</body>");
            writer.println("</html>");
        }
    }

    /**
     * 为包创建索引文件
     */
    private void createPackageIndex(String packageName, AggregatedResult result, File sourcesDir)
            throws IOException {
        String packagePath = packageName.replace('.', '/');
        File packageDir = new File(sourcesDir, packagePath);
        if (!packageDir.exists()) {
            packageDir.mkdirs();
        }

        File indexFile = new File(packageDir, "index.html");

        try (PrintWriter writer = new PrintWriter(new FileWriter(indexFile))) {
            writer.println("<!DOCTYPE html>");
            writer.println("<html>");
            writer.println("<head>");
            writer.println("  <title>PIT Source Browser - " + packageName + "</title>");
            writer.println("  <link rel=\"stylesheet\" href=\"" +
                    repeat("../",packageName.split("\\.").length + 1) + "html/style.css\">");
            writer.println("</head>");
            writer.println("<body>");
            writer.println("  <h1>Package: " + packageName + "</h1>");
            writer.println("  <p><a href=\"" + repeat("../",packageName.split("\\.").length) +
                    "index.html\">Back to packages</a></p>");

            writer.println("  <h2>Source Files</h2>");
            writer.println("  <ul>");

            // 获取该包中的所有源文件
            File[] sourceFiles = packageDir.listFiles(file -> file.isFile() && file.getName().endsWith(".java"));
            if (sourceFiles != null) {
                for (File sourceFile : sourceFiles) {
                    writer.println("    <li><a href=\"" + sourceFile.getName() + "\">" +
                            sourceFile.getName() + "</a></li>");

                    // 创建源文件的HTML视图
                    createSourceFileView(packageName, sourceFile, result,
                            repeat("../",packageName.split("\\.").length + 1) + "html/");
                }
            }

            writer.println("  </ul>");
            writer.println("</body>");
            writer.println("</html>");
        }
    }

    private static String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }


    /**
     * 创建源文件HTML视图
     */
    private void createSourceFileView(String packageName, File sourceFile,
                                      AggregatedResult result, String cssPath) throws IOException {
        String sourceFileName = sourceFile.getName();
        File htmlSourceFile = new File(sourceFile.getParentFile(),
                sourceFileName.replace(".java", ".html"));

        if (htmlSourceFile.exists()) {
            return;  // 已存在，跳过
        }

        // 读取源文件内容
        List<String> lines = Files.readAllLines(sourceFile.toPath());

        // 找出这个源文件的所有变异
        String fullClassName = packageName + "." + sourceFileName.replace(".java", "");
        List<MutationResult> fileMutations = new ArrayList<>();

        for (Map.Entry<String, List<MutationResult>> entry : result.getMutationsByClass().entrySet()) {
            String className = entry.getKey();
            if (className.equals(fullClassName) || className.startsWith(fullClassName + "$")) {
                fileMutations.addAll(entry.getValue());
            }
        }

        // 按行号分组变异
        Map<Integer, List<MutationResult>> mutationsByLine = fileMutations.stream()
                .collect(Collectors.groupingBy(MutationResult::getLineNumber));

        // 创建HTML视图
        try (PrintWriter writer = new PrintWriter(new FileWriter(htmlSourceFile))) {
            writer.println("<!DOCTYPE html>");
            writer.println("<html>");
            writer.println("<head>");
            writer.println("  <title>Source: " + sourceFileName + "</title>");
            writer.println("  <link rel=\"stylesheet\" href=\"" + cssPath + "style.css\">");
            writer.println("  <style>");
            writer.println("    .source-line { font-family: monospace; white-space: pre; }");
            writer.println("    .mutation-details { display: none; background-color: #f8f8f8; " +
                    "border: 1px solid #ddd; padding: 10px; margin: 5px 0 5px 40px; }");
            writer.println("    .toggle-mutations { cursor: pointer; color: blue; text-decoration: underline; }");
            writer.println("  </style>");
            writer.println("  <script>");
            writer.println("    function toggleMutations(lineNum) {");
            writer.println("      var details = document.getElementById('mutations-' + lineNum);");
            writer.println("      if (details.style.display === 'none' || !details.style.display) {");
            writer.println("        details.style.display = 'block';");
            writer.println("      } else {");
            writer.println("        details.style.display = 'none';");
            writer.println("      }");
            writer.println("    }");
            writer.println("  </script>");
            writer.println("</head>");
            writer.println("<body>");
            writer.println("  <h1>Source File: " + sourceFileName + "</h1>");
            writer.println("  <p><a href=\"index.html\">Back to package</a></p>");

            writer.println("  <div class=\"source-code\">");

            // 输出每一行源代码
            for (int i = 0; i < lines.size(); i++) {
                int lineNum = i + 1;
                String line = lines.get(i);

                if (mutationsByLine.containsKey(lineNum)) {
                    List<MutationResult> lineMutations = mutationsByLine.get(lineNum);
                    boolean anyKilled = lineMutations.stream().anyMatch(MutationResult::isDetected);
                    boolean allKilled = lineMutations.stream().allMatch(MutationResult::isDetected);

                    String highlightClass = allKilled ? "fully-covered" :
                            anyKilled ? "partially-covered" : "not-covered";

                    writer.println("  <div class=\"source-line " + highlightClass + "\">");
                    writer.println("    <span class=\"line-number\">" + lineNum + "</span>");
                    writer.println("    <span class=\"line\">" + escapeHtml(line) + "</span>");
                    writer.println("    <span class=\"toggle-mutations\" onclick=\"toggleMutations(" +
                            lineNum + ")\">(" + lineMutations.size() + " mutations)</span>");
                    writer.println("  </div>");

                    // 添加变异详情
                    writer.println("  <div id=\"mutations-" + lineNum + "\" class=\"mutation-details\">");
                    writer.println("    <h4>Mutations on line " + lineNum + ":</h4>");
                    writer.println("    <table class=\"mutations\">");
                    writer.println("      <thead>");
                    writer.println("        <tr>");
                    writer.println("          <th>Mutator</th>");
                    writer.println("          <th>Description</th>");
                    writer.println("          <th>Status</th>");
                    writer.println("        </tr>");
                    writer.println("      </thead>");
                    writer.println("      <tbody>");

                    for (MutationResult mutation : lineMutations) {
                        writer.println("        <tr class=\"" +
                                (mutation.isDetected() ? "killed" : "survived") + "\">");
                        writer.println("          <td>" + simplifyMutatorName(mutation.getMutator()) + "</td>");
                        writer.println("          <td>" + mutation.getDescription() + "</td>");
                        writer.println("          <td>" + mutation.getStatus() + "</td>");
                        writer.println("        </tr>");

                        // 如果有原始代码和变异代码，显示对比
                        if (mutation.getOriginalCode() != null && !mutation.getOriginalCode().isEmpty() &&
                                mutation.getMutatedCode() != null && !mutation.getMutatedCode().isEmpty()) {
                            writer.println("        <tr>");
                            writer.println("          <td colspan=\"3\">");
                            writer.println("            <div class=\"diff-container\">");
                            writer.println("              <div class=\"original\">");
                            writer.println("                <h5>Original:</h5>");
                            writer.println("                <pre><code>" +
                                    escapeHtml(mutation.getOriginalCode()) + "</code></pre>");
                            writer.println("              </div>");
                            writer.println("              <div class=\"mutated\">");
                            writer.println("                <h5>Mutated:</h5>");
                            writer.println("                <pre><code>" +
                                    escapeHtml(mutation.getMutatedCode()) + "</code></pre>");
                            writer.println("              </div>");
                            writer.println("            </div>");
                            writer.println("          </td>");
                            writer.println("        </tr>");
                        }
                    }

                    writer.println("      </tbody>");
                    writer.println("    </table>");
                    writer.println("  </div>");
                } else {
                    writer.println("  <div class=\"source-line\">");
                    writer.println("    <span class=\"line-number\">" + lineNum + "</span>");
                    writer.println("    <span class=\"line\">" + escapeHtml(line) + "</span>");
                    writer.println("  </div>");
                }
            }

            writer.println("  </div>");
            writer.println("</body>");
            writer.println("</html>");
        }
    }

    /**
     * 计算每个类的突变测试摘要
     */
    private Map<String, ClassSummary> calculateClassSummaries(AggregatedResult result) {
        Map<String, ClassSummary> summaries = new HashMap<>();

        for (Map.Entry<String, List<MutationResult>> entry : result.getMutationsByClass().entrySet()) {
            String className = entry.getKey();
            List<MutationResult> mutations = entry.getValue();

            int total = mutations.size();
            int killed = (int) mutations.stream().filter(MutationResult::isDetected).count();
            double score = total > 0 ? (double) killed / total * 100.0 : 0.0;

            summaries.put(className, new ClassSummary(total, killed, score));
        }

        return summaries;
    }

    /**
     * 计算每种变异器的摘要
     */
    private Map<String, MutatorSummary> calculateMutatorSummaries(AggregatedResult result) {
        Map<String, MutatorSummary> summaries = new HashMap<>();

        for (List<MutationResult> mutations : result.getMutationsByClass().values()) {
            for (MutationResult mutation : mutations) {
                String mutator = mutation.getMutator();

                if (!summaries.containsKey(mutator)) {
                    summaries.put(mutator, new MutatorSummary());
                }

                MutatorSummary summary = summaries.get(mutator);
                summary.total++;

                if (mutation.isDetected()) {
                    summary.killed++;
                }
            }
        }

        // 计算百分比
        for (MutatorSummary summary : summaries.values()) {
            summary.score = summary.total > 0 ? (double) summary.killed / summary.total * 100.0 : 0.0;
        }

        return summaries;
    }

    /**
     * 简化变异器名称
     */
    private String simplifyMutatorName(String mutator) {
        // 移除包名
        int lastDot = mutator.lastIndexOf('.');
        if (lastDot > 0) {
            mutator = mutator.substring(lastDot + 1);
        }

        // 移除_MUTATOR后缀
        if (mutator.endsWith("_MUTATOR")) {
            mutator = mutator.substring(0, mutator.length() - 8);
        }

        return mutator;
    }

    /**
     * 转义HTML特殊字符
     */
    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * 转义XML特殊字符
     */
    private String escapeXml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * 类摘要信息
     */
    private static class ClassSummary {
        final int total;
        final int killed;
        final double score;

        ClassSummary(int total, int killed, double score) {
            this.total = total;
            this.killed = killed;
            this.score = score;
        }
    }

    /**
     * 变异器摘要信息
     */
    private static class MutatorSummary {
        int total = 0;
        int killed = 0;
        double score = 0.0;
    }
}