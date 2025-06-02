package com.distributed.pitest.report;

import com.distributed.pitest.model.AggregatedResult;
import com.distributed.pitest.model.MutationResult;
import com.distributed.pitest.util.CodeDifferenceGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HTML报告模板生成器，用于创建详细的突变测试报告
 */
public class HtmlReportTemplateGenerator {
    private static final Logger logger = LoggerFactory.getLogger(HtmlReportTemplateGenerator.class);
    private final CodeDifferenceGenerator diffGenerator;

    public HtmlReportTemplateGenerator() {
        this.diffGenerator = new CodeDifferenceGenerator();
    }

    /**
     * 生成主索引页面
     */
    public void generateIndexPage(AggregatedResult result, File outputDir) throws IOException {
        File indexFile = new File(outputDir, "index.html");
        logger.info("Generating index page at {}", indexFile.getAbsolutePath());

        Map<String, ClassSummary> classSummaries = calculateClassSummaries(result);
        Map<String, MutatorSummary> mutatorSummaries = calculateMutatorSummaries(result);

        try (PrintWriter writer = new PrintWriter(new FileWriter(indexFile))) {
            writer.println("<!DOCTYPE html>");
            writer.println("<html lang=\"en\">");
            writer.println("<head>");
            writer.println("  <meta charset=\"UTF-8\">");
            writer.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
            writer.println("  <title>Distributed PIT Mutation Test Report</title>");
            writer.println("  <link rel=\"stylesheet\" href=\"css/style.css\">");
            writer.println("  <script src=\"js/report.js\"></script>");
            writer.println("</head>");
            writer.println("<body>");
            writer.println("  <header>");
            writer.println("    <div class=\"container\">");
            writer.println("      <h1>Distributed PIT Mutation Test Report</h1>");
            writer.println("      <div class=\"report-metadata\">");
            writer.println("        <p>Generated at: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "</p>");
            writer.println("      </div>");
            writer.println("    </div>");
            writer.println("  </header>");

            writer.println("  <main class=\"container\">");

            // 总体摘要
            writer.println("    <section class=\"summary-section\">");
            writer.println("      <h2>Summary</h2>");
            writer.println("      <div class=\"summary-cards\">");
            writer.println("        <div class=\"summary-card\">");
            writer.println("          <h3>Total Mutations</h3>");
            writer.println("          <div class=\"summary-number\">" + result.getTotalMutations() + "</div>");
            writer.println("        </div>");
            writer.println("        <div class=\"summary-card\">");
            writer.println("          <h3>Killed Mutations</h3>");
            writer.println("          <div class=\"summary-number\">" + result.getKilledMutations() + "</div>");
            writer.println("        </div>");
            writer.println("        <div class=\"summary-card\">");
            writer.println("          <h3>Mutation Score</h3>");
            writer.println("          <div class=\"summary-number\">" + String.format("%.2f%%", result.getMutationScore()) + "</div>");
            writer.println("        </div>");
            writer.println("      </div>");

            // 错误列表
            if (result.hasErrors()) {
                writer.println("      <div class=\"error-section\">");
                writer.println("        <h3>Errors</h3>");
                writer.println("        <ul class=\"error-list\">");
                for (String error : result.getErrors()) {
                    writer.println("          <li>" + escapeHtml(error) + "</li>");
                }
                writer.println("        </ul>");
                writer.println("      </div>");
            }
            writer.println("    </section>");

            // 变异器摘要
            writer.println("    <section class=\"mutators-section\">");
            writer.println("      <h2>Mutator Summary</h2>");
            writer.println("      <table class=\"data-table\">");
            writer.println("        <thead>");
            writer.println("          <tr>");
            writer.println("            <th>Mutator</th>");
            writer.println("            <th>Total</th>");
            writer.println("            <th>Killed</th>");
            writer.println("            <th>Survived</th>");
            writer.println("            <th>Score</th>");
            writer.println("          </tr>");
            writer.println("        </thead>");
            writer.println("        <tbody>");

            for (Map.Entry<String, MutatorSummary> entry : mutatorSummaries.entrySet()) {
                String mutator = entry.getKey();
                MutatorSummary summary = entry.getValue();

                writer.println("          <tr>");
                writer.println("            <td>" + simplifyMutatorName(mutator) + "</td>");
                writer.println("            <td>" + summary.total + "</td>");
                writer.println("            <td>" + summary.killed + "</td>");
                writer.println("            <td>" + (summary.total - summary.killed) + "</td>");
                writer.println("            <td>" + String.format("%.2f%%", summary.score) + "</td>");
                writer.println("          </tr>");
            }

            writer.println("        </tbody>");
            writer.println("      </table>");
            writer.println("    </section>");

            // 类列表
            writer.println("    <section class=\"classes-section\">");
            writer.println("      <h2>Classes</h2>");
            writer.println("      <table class=\"data-table sortable\">");
            writer.println("        <thead>");
            writer.println("          <tr>");
            writer.println("            <th>Class</th>");
            writer.println("            <th>Total</th>");
            writer.println("            <th>Killed</th>");
            writer.println("            <th>Survived</th>");
            writer.println("            <th>Score</th>");
            writer.println("          </tr>");
            writer.println("        </thead>");
            writer.println("        <tbody>");

            for (Map.Entry<String, ClassSummary> entry : classSummaries.entrySet()) {
                String className = entry.getKey();
                ClassSummary summary = entry.getValue();
                String classPath = className.replace('.', '/');

                writer.println("          <tr>");
                writer.println("            <td><a href=\"classes/" + classPath + ".html\">" + className + "</a></td>");
                writer.println("            <td>" + summary.total + "</td>");
                writer.println("            <td>" + summary.killed + "</td>");
                writer.println("            <td>" + (summary.total - summary.killed) + "</td>");
                writer.println("            <td>" + String.format("%.2f%%", summary.score) + "</td>");
                writer.println("          </tr>");
            }

            writer.println("        </tbody>");
            writer.println("      </table>");
            writer.println("    </section>");

            writer.println("  </main>");

            writer.println("  <footer class=\"container\">");
            writer.println("    <p>Generated by Distributed PIT Mutation Testing Maven Plugin</p>");
            writer.println("  </footer>");
            writer.println("</body>");
            writer.println("</html>");
        }

        // 复制静态资源
        generateCssFile(new File(outputDir, "css"));
        generateJsFile(new File(outputDir, "js"));
    }

    /**
     * 生成类详情页面
     */
    public void generateClassReport(String className, List<MutationResult> mutations,
                                    File outputDir, ClassSummary summary) throws IOException {
        // 创建目录
        String classPath = className.replace('.', '/');
        File classDir = new File(outputDir, "classes/" + classPath.substring(0, classPath.lastIndexOf('/')));
        classDir.mkdirs();

        // 创建报告文件
        File reportFile = new File(outputDir, "classes/" + classPath + ".html");

        // 按方法分组
        Map<String, List<MutationResult>> methodMutations = mutations.stream()
                .collect(Collectors.groupingBy(MutationResult::getMutatedMethod));

        try (PrintWriter writer = new PrintWriter(new FileWriter(reportFile))) {
            writer.println("<!DOCTYPE html>");
            writer.println("<html lang=\"en\">");
            writer.println("<head>");
            writer.println("  <meta charset=\"UTF-8\">");
            writer.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
            writer.println("  <title>PIT Mutation Report - " + className + "</title>");
            writer.println("  <link rel=\"stylesheet\" href=\"" + getRelativePath(classPath) + "css/style.css\">");
            writer.println("  <script src=\"" + getRelativePath(classPath) + "js/report.js\"></script>");
            writer.println("</head>");
            writer.println("<body>");

            writer.println("  <header>");
            writer.println("    <div class=\"container\">");
            writer.println("      <h1>PIT Mutation Report</h1>");
            writer.println("      <div class=\"breadcrumbs\">");
            writer.println("        <a href=\"" + getRelativePath(classPath) + "index.html\">Home</a> &gt; ");
            writer.println("        <span>" + className + "</span>");
            writer.println("      </div>");
            writer.println("    </div>");
            writer.println("  </header>");

            writer.println("  <main class=\"container\">");

            // 类摘要
            writer.println("    <section class=\"class-summary\">");
            writer.println("      <h2>" + className + "</h2>");
            writer.println("      <div class=\"summary-cards\">");
            writer.println("        <div class=\"summary-card\">");
            writer.println("          <h3>Total Mutations</h3>");
            writer.println("          <div class=\"summary-number\">" + summary.total + "</div>");
            writer.println("        </div>");
            writer.println("        <div class=\"summary-card\">");
            writer.println("          <h3>Killed Mutations</h3>");
            writer.println("          <div class=\"summary-number\">" + summary.killed + "</div>");
            writer.println("        </div>");
            writer.println("        <div class=\"summary-card\">");
            writer.println("          <h3>Mutation Score</h3>");
            writer.println("          <div class=\"summary-number\">" + String.format("%.2f%%", summary.score) + "</div>");
            writer.println("        </div>");
            writer.println("      </div>");
            writer.println("    </section>");

            // 源文件链接
            String sourceFileName = mutations.isEmpty() ? null : mutations.get(0).getSourceFile();
            if (sourceFileName != null) {
                writer.println("    <section class=\"source-section\">");
                writer.println("      <h3>Source</h3>");
                writer.println("      <p><a href=\"" + getRelativePath(classPath) + "sources/" +
                        className.replace('.', '/') + ".java\" class=\"source-link\">View Source</a></p>");
                writer.println("    </section>");
            }

            // 方法列表
            writer.println("    <section class=\"methods-section\">");
            writer.println("      <h3>Methods</h3>");
            writer.println("      <div class=\"method-list\">");
            writer.println("        <ul>");

            for (String methodName : methodMutations.keySet()) {
                writer.println("          <li><a href=\"#method-" + methodName + "\">" + methodName + "</a></li>");
            }

            writer.println("        </ul>");
            writer.println("      </div>");
            writer.println("    </section>");

            // 变异详情
            for (Map.Entry<String, List<MutationResult>> entry : methodMutations.entrySet()) {
                String methodName = entry.getKey();
                List<MutationResult> methodMuts = entry.getValue();

                writer.println("    <section id=\"method-" + methodName + "\" class=\"method-section\">");
                writer.println("      <h3>Method: " + methodName + "</h3>");

                // 计算方法摘要
                int total = methodMuts.size();
                int killed = (int) methodMuts.stream().filter(MutationResult::isDetected).count();
                double score = total > 0 ? (double) killed / total * 100.0 : 0.0;

                writer.println("      <div class=\"method-summary\">");
                writer.println("        <p>Mutations: " + killed + "/" + total + " (" +
                        String.format("%.2f%%", score) + ")</p>");
                writer.println("      </div>");

                // 变异列表
                writer.println("      <table class=\"mutations-table\">");
                writer.println("        <thead>");
                writer.println("          <tr>");
                writer.println("            <th>Status</th>");
                writer.println("            <th>Line</th>");
                writer.println("            <th>Mutator</th>");
                writer.println("            <th>Description</th>");
                writer.println("            <th>Killing Test</th>");
                writer.println("          </tr>");
                writer.println("        </thead>");
                writer.println("        <tbody>");

                for (MutationResult mutation : methodMuts) {
                    String statusClass = mutation.isDetected() ? "killed" : "survived";

                    writer.println("          <tr class=\"" + statusClass + "\" data-mutation-id=\"" +
                            mutation.getUniqueKey().hashCode() + "\">");
                    writer.println("            <td class=\"mutation-status\">" +
                            (mutation.isDetected() ? "KILLED" : "SURVIVED") + "</td>");
                    writer.println("            <td>" + mutation.getLineNumber() + "</td>");
                    writer.println("            <td>" + simplifyMutatorName(mutation.getMutator()) + "</td>");
                    writer.println("            <td>" + escapeHtml(mutation.getDescription()) + "</td>");
                    writer.println("            <td>" + (mutation.getKillingTest() != null ?
                            escapeHtml(mutation.getKillingTest()) : "") + "</td>");
                    writer.println("          </tr>");

                    // 如果有代码细节，添加可展开面板
                    if (mutation.getOriginalCode() != null && !mutation.getOriginalCode().isEmpty()) {
                        writer.println("          <tr class=\"code-panel\" id=\"panel-" +
                                mutation.getUniqueKey().hashCode() + "\">");
                        writer.println("            <td colspan=\"5\">");

                        if (mutation.getMutatedCode() != null && !mutation.getMutatedCode().isEmpty()) {
                            // 生成代码比较视图
                            CodeDifferenceGenerator.DiffResult diff =
                                    diffGenerator.generateDiff(mutation.getOriginalCode(), mutation.getMutatedCode());
                            writer.println(diff.getHtmlDiff());
                        } else {
                            // 只显示原始代码
                            writer.println("              <div class=\"code-block\">");
                            writer.println("                <h4>Original Code:</h4>");
                            writer.println("                <pre><code>" +
                                    escapeHtml(mutation.getOriginalCode()) + "</code></pre>");
                            writer.println("              </div>");
                        }

                        writer.println("            </td>");
                        writer.println("          </tr>");
                    }
                }

                writer.println("        </tbody>");
                writer.println("      </table>");
                writer.println("    </section>");
            }

            writer.println("  </main>");

            writer.println("  <footer class=\"container\">");
            writer.println("    <p>Generated by Distributed PIT Mutation Testing Maven Plugin</p>");
            writer.println("  </footer>");

            writer.println("</body>");
            writer.println("</html>");
        }
    }

    /**
     * 生成源代码查看器页面
     */
    public void generateSourceView(String className, String sourceCode, File outputDir) throws IOException {
        // 创建目录
        String packagePath = className.substring(0, className.lastIndexOf('.'));
        String classPath = packagePath.replace('.', '/');
        File sourceDir = new File(outputDir, "sources/" + classPath);
        sourceDir.mkdirs();

        // 创建源文件
        File sourceFile = new File(sourceDir, className.substring(className.lastIndexOf('.') + 1) + ".java");

        try (PrintWriter writer = new PrintWriter(new FileWriter(sourceFile))) {
            writer.println("<!DOCTYPE html>");
            writer.println("<html lang=\"en\">");
            writer.println("<head>");
            writer.println("  <meta charset=\"UTF-8\">");
            writer.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
            writer.println("  <title>Source: " + className + "</title>");
            writer.println("  <link rel=\"stylesheet\" href=\"" + getRelativePathToRoot(classPath) + "css/style.css\">");
            writer.println("  <style>");
            writer.println("    .source-code { font-family: monospace; white-space: pre; line-height: 1.5; }");
            writer.println("    .line-number { display: inline-block; width: 3em; color: #999; text-align: right; margin-right: 1em; }");
            writer.println("  </style>");
            writer.println("</head>");
            writer.println("<body>");

            writer.println("  <header>");
            writer.println("    <div class=\"container\">");
            writer.println("      <h1>Source Code</h1>");
            writer.println("      <div class=\"breadcrumbs\">");
            writer.println("        <a href=\"" + getRelativePathToRoot(classPath) + "index.html\">Home</a> &gt; ");
            writer.println("        <a href=\"" + getRelativePathToRoot(classPath) + "classes/" +
                    className.replace('.', '/') + ".html\">" + className + "</a> &gt; ");
            writer.println("        <span>Source</span>");
            writer.println("      </div>");
            writer.println("    </div>");
            writer.println("  </header>");

            writer.println("  <main class=\"container\">");
            writer.println("    <div class=\"source-code\">");

            // 按行输出源代码
            String[] lines = sourceCode.split("\n");
            for (int i = 0; i < lines.length; i++) {
                int lineNum = i + 1;
                writer.println("<span class=\"line-number\">" + lineNum + "</span>" + escapeHtml(lines[i]));
            }

            writer.println("    </div>");
            writer.println("  </main>");

            writer.println("  <footer class=\"container\">");
            writer.println("    <p>Generated by Distributed PIT Mutation Testing Maven Plugin</p>");
            writer.println("  </footer>");

            writer.println("</body>");
            writer.println("</html>");
        }
    }

    /**
     * 生成CSS样式文件
     */
    private void generateCssFile(File cssDir) throws IOException {
        cssDir.mkdirs();
        File cssFile = new File(cssDir, "style.css");

        try (PrintWriter writer = new PrintWriter(new FileWriter(cssFile))) {
            writer.println("/* Distributed PIT Mutation Testing Report Styles */");
            writer.println("");
            writer.println("/* Reset and base styles */");
            writer.println("* { margin: 0; padding: 0; box-sizing: border-box; }");
            writer.println("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif; line-height: 1.6; color: #333; background-color: #f8f9fa; }");
            writer.println("a { color: #0366d6; text-decoration: none; }");
            writer.println("a:hover { text-decoration: underline; }");
            writer.println("h1, h2, h3, h4 { margin-bottom: 0.5em; color: #24292e; }");
            writer.println("");
            writer.println("/* Layout */");
            writer.println(".container { max-width: 1200px; margin: 0 auto; padding: 0 15px; }");
            writer.println("header { background-color: #24292e; color: white; padding: 1rem 0; margin-bottom: 2rem; }");
            writer.println("header h1 { margin: 0; color: white; }");
            writer.println("footer { margin-top: 3rem; padding: 1.5rem 0; border-top: 1px solid #e1e4e8; color: #586069; font-size: 0.9rem; }");
            writer.println("main { min-height: 70vh; }");
            writer.println("section { margin-bottom: 2.5rem; }");
            writer.println("");
            writer.println("/* Breadcrumbs */");
            writer.println(".breadcrumbs { font-size: 0.9rem; margin-top: 0.5rem; color: #ccc; }");
            writer.println(".breadcrumbs a { color: #ccc; }");
            writer.println(".breadcrumbs a:hover { color: white; }");
            writer.println("");
            writer.println("/* Summary cards */");
            writer.println(".summary-cards { display: flex; flex-wrap: wrap; gap: 1rem; margin-bottom: 1.5rem; }");
            writer.println(".summary-card { flex: 1; min-width: 200px; background-color: white; border-radius: 4px; box-shadow: 0 1px 3px rgba(0,0,0,0.12); padding: 1rem; border-top: 3px solid #0366d6; }");
            writer.println(".summary-number { font-size: 2rem; font-weight: bold; color: #0366d6; margin-top: 0.5rem; }");
            writer.println("");
            writer.println("/* Tables */");
            writer.println(".data-table { width: 100%; border-collapse: collapse; margin-top: 1rem; background-color: white; box-shadow: 0 1px 3px rgba(0,0,0,0.12); }");
            writer.println(".data-table th, .data-table td { padding: 0.75rem; text-align: left; border-bottom: 1px solid #e1e4e8; }");
            writer.println(".data-table th { background-color: #f6f8fa; font-weight: 600; }");
            writer.println(".data-table tr:hover { background-color: #f6f8fa; }");
            writer.println("");
            writer.println("/* Mutations table */");
            writer.println(".mutations-table { width: 100%; border-collapse: collapse; margin-top: 1rem; background-color: white; box-shadow: 0 1px 3px rgba(0,0,0,0.12); }");
            writer.println(".mutations-table th, .mutations-table td { padding: 0.75rem; text-align: left; border-bottom: 1px solid #e1e4e8; }");
            writer.println(".mutations-table th { background-color: #f6f8fa; font-weight: 600; }");
            writer.println(".mutations-table tr.killed { background-color: #e6ffed; }");
            writer.println(".mutations-table tr.survived { background-color: #ffeef0; }");
            writer.println(".mutations-table tr.killed:hover { background-color: #dcffe4; }");
            writer.println(".mutations-table tr.survived:hover { background-color: #fee8ea; }");
            writer.println(".mutations-table tr.killed td.mutation-status { color: #22863a; font-weight: bold; }");
            writer.println(".mutations-table tr.survived td.mutation-status { color: #cb2431; font-weight: bold; }");
            writer.println("");
            writer.println("/* Code panels */");
            writer.println(".code-panel { display: none; }");
            writer.println(".code-block { margin: 1rem 0; padding: 1rem; background-color: #f6f8fa; border-radius: 3px; overflow-x: auto; }");
            writer.println(".code-block h4 { margin-top: 0; margin-bottom: 0.5rem; }");
            writer.println(".code-block pre { margin: 0; font-family: SFMono-Regular, Consolas, 'Liberation Mono', Menlo, monospace; }");
            writer.println("");
            writer.println("/* Code diff */");
            writer.println(".code-diff { margin: 1rem 0; overflow-x: auto; }");
            writer.println(".diff-table { width: 100%; border-collapse: collapse; font-family: SFMono-Regular, Consolas, 'Liberation Mono', Menlo, monospace; font-size: 0.9rem; }");
            writer.println(".diff-table tr.diff-unchanged { background-color: #f6f8fa; }");
            writer.println(".diff-table tr.diff-added { background-color: #e6ffed; }");
            writer.println(".diff-table tr.diff-removed { background-color: #ffeef0; }");
            writer.println(".diff-table td { padding: 0 0.5rem; white-space: pre; }");
            writer.println(".diff-table td.diff-line-num { color: #959da5; text-align: right; width: 1%; user-select: none; padding-right: 1rem; border-right: 1px solid #e1e4e8; }");
            writer.println("");
            writer.println("/* Error section */");
            writer.println(".error-section { padding: 1rem; background-color: #ffeef0; border-radius: 3px; margin-top: 1rem; }");
            writer.println(".error-list { margin-left: 1.5rem; }");
            writer.println("");
            writer.println("/* Method list */");
            writer.println(".method-list { margin-bottom: 1rem; }");
            writer.println(".method-list ul { list-style-type: none; display: flex; flex-wrap: wrap; gap: 0.5rem; }");
            writer.println(".method-list li { background-color: #f6f8fa; padding: 0.5rem 0.75rem; border-radius: 3px; }");
            writer.println("");
            writer.println("/* Source link */");
            writer.println(".source-link { display: inline-block; padding: 0.5rem 1rem; background-color: #0366d6; color: white; border-radius: 3px; margin-top: 0.5rem; }");
            writer.println(".source-link:hover { background-color: #0255b8; text-decoration: none; }");
            writer.println("");
            writer.println("/* Source code view */");
            writer.println(".source-code { background-color: white; padding: 1rem; border-radius: 3px; box-shadow: 0 1px 3px rgba(0,0,0,0.12); overflow-x: auto; }");
        }
    }

    /**
     * 生成JavaScript文件
     */
    private void generateJsFile(File jsDir) throws IOException {
        jsDir.mkdirs();
        File jsFile = new File(jsDir, "report.js");

        try (PrintWriter writer = new PrintWriter(new FileWriter(jsFile))) {
            writer.println("// Distributed PIT Mutation Testing Report Scripts");
            writer.println("");
            writer.println("document.addEventListener('DOMContentLoaded', function() {");
            writer.println("  // Toggle code panels when clicking mutation rows");
            writer.println("  const mutationRows = document.querySelectorAll('tr[data-mutation-id]');");
            writer.println("  mutationRows.forEach(row => {");
            writer.println("    row.addEventListener('click', function() {");
            writer.println("      const mutationId = this.getAttribute('data-mutation-id');");
            writer.println("      const codePanel = document.getElementById('panel-' + mutationId);");
            writer.println("      if (codePanel) {");
            writer.println("        if (codePanel.style.display === 'table-row') {");
            writer.println("          codePanel.style.display = 'none';");
            writer.println("        } else {");
            writer.println("          codePanel.style.display = 'table-row';");
            writer.println("        }");
            writer.println("      }");
            writer.println("    });");
            writer.println("  });");
            writer.println("");
            writer.println("  // Make tables sortable");
            writer.println("  const sortableTables = document.querySelectorAll('table.sortable');");
            writer.println("  sortableTables.forEach(table => {");
            writer.println("    const headers = table.querySelectorAll('th');");
            writer.println("    headers.forEach((header, index) => {");
            writer.println("      header.style.cursor = 'pointer';");
            writer.println("      header.addEventListener('click', function() {");
            writer.println("        sortTable(table, index);");
            writer.println("      });");
            writer.println("    });");
            writer.println("  });");
            writer.println("});");
            writer.println("");
            writer.println("function sortTable(table, column) {");
            writer.println("  const tbody = table.querySelector('tbody');");
            writer.println("  const rows = Array.from(tbody.querySelectorAll('tr'));");
            writer.println("  const headers = table.querySelectorAll('th');");
            writer.println("  ");
            writer.println("  // Determine sort direction");
            writer.println("  const currentDirection = headers[column].getAttribute('data-sort-direction') || 'asc';");
            writer.println("  const newDirection = currentDirection === 'asc' ? 'desc' : 'asc';");
            writer.println("  ");
            writer.println("  // Update sort direction attribute");
            writer.println("  headers.forEach(h => h.removeAttribute('data-sort-direction'));");
            writer.println("  headers[column].setAttribute('data-sort-direction', newDirection);");
            writer.println("  ");
            writer.println("  // Sort rows");
            writer.println("  rows.sort((a, b) => {");
            writer.println("    const cellA = a.cells[column].textContent.trim();");
            writer.println("    const cellB = b.cells[column].textContent.trim();");
            writer.println("    ");
            writer.println("    // Check if content is numeric");
            writer.println("    const numA = parseFloat(cellA);");
            writer.println("    const numB = parseFloat(cellB);");
            writer.println("    ");
            writer.println("    if (!isNaN(numA) && !isNaN(numB)) {");
            writer.println("      return newDirection === 'asc' ? numA - numB : numB - numA;");
            writer.println("    } else {");
            writer.println("      return newDirection === 'asc' ? cellA.localeCompare(cellB) : cellB.localeCompare(cellA);");
            writer.println("    }");
            writer.println("  });");
            writer.println("  ");
            writer.println("  // Reorder rows in the DOM");
            writer.println("  rows.forEach(row => tbody.appendChild(row));");
            writer.println("}");
        }
    }

    /**
     * 计算类摘要信息
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
     * 计算变异器摘要信息
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
     * 获取相对路径
     */
    private String getRelativePath(String classPath) {
        // 计算从类HTML文件到项目根目录的相对路径
        int depth = classPath.split("/").length;
        StringBuilder path = new StringBuilder();

        for (int i = 0; i < depth; i++) {
            path.append("../");
        }

        return path.toString();
    }

    /**
     * 获取到根目录的相对路径
     */
    private String getRelativePathToRoot(String packagePath) {
        // 计算从源文件到项目根目录的相对路径
        int depth = packagePath.split("/").length + 2; // +2 for sources/[className]
        StringBuilder path = new StringBuilder();

        for (int i = 0; i < depth; i++) {
            path.append("../");
        }

        return path.toString();
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
     * HTML转义
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }

        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * 类摘要信息
     */
    public static class ClassSummary {
        final int total;
        final int killed;
        final double score;

        public ClassSummary(int total, int killed, double score) {
            this.total = total;
            this.killed = killed;
            this.score = score;
        }

        public int getTotal() {
            return total;
        }

        public int getKilled() {
            return killed;
        }

        public double getScore() {
            return score;
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