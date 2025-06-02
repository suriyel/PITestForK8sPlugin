package com.distributed.pitest.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 源代码比较工具，用于生成原始代码和变异代码之间的差异
 */
public class CodeDifferenceGenerator {
    private static final Logger logger = LoggerFactory.getLogger(CodeDifferenceGenerator.class);

    public static class DiffResult {
        private final List<DiffLine> diffLines;
        private final String htmlDiff;

        public DiffResult(List<DiffLine> diffLines, String htmlDiff) {
            this.diffLines = diffLines;
            this.htmlDiff = htmlDiff;
        }

        public List<DiffLine> getDiffLines() {
            return diffLines;
        }

        public String getHtmlDiff() {
            return htmlDiff;
        }
    }

    public static class DiffLine {
        public enum Type { UNCHANGED, ADDED, REMOVED }

        private final Type type;
        private final String content;
        private final int lineNumber;

        public DiffLine(Type type, String content, int lineNumber) {
            this.type = type;
            this.content = content;
            this.lineNumber = lineNumber;
        }

        public Type getType() {
            return type;
        }

        public String getContent() {
            return content;
        }

        public int getLineNumber() {
            return lineNumber;
        }
    }

    /**
     * 生成原始代码和变异代码之间的差异
     *
     * @param originalCode 原始代码
     * @param mutatedCode 变异代码
     * @return 差异结果
     */
    public DiffResult generateDiff(String originalCode, String mutatedCode) {
        if (originalCode == null || mutatedCode == null) {
            return new DiffResult(new ArrayList<>(), "");
        }

        try {
            // 将代码拆分为行
            String[] originalLines = originalCode.split("\n");
            String[] mutatedLines = mutatedCode.split("\n");

            // 计算行级差异
            List<DiffLine> diffLines = computeLineDiffs(originalLines, mutatedLines);

            // 生成HTML差异
            String htmlDiff = generateHtmlDiff(diffLines);

            return new DiffResult(diffLines, htmlDiff);
        } catch (Exception e) {
            logger.warn("Error generating code difference: {}", e.getMessage());
            return new DiffResult(new ArrayList<>(), "");
        }
    }

    /**
     * 计算行级差异
     */
    private List<DiffLine> computeLineDiffs(String[] originalLines, String[] mutatedLines) {
        List<DiffLine> result = new ArrayList<>();

        // 简化的差异计算实现，使用最长公共子序列（LCS）算法
        int[][] lcs = computeLCS(originalLines, mutatedLines);
        List<DiffOperation> operations = backtrackLCS(lcs, originalLines, mutatedLines, originalLines.length, mutatedLines.length);

        // 应用差异操作
        int originalLineNumber = 1;
        int mutatedLineNumber = 1;

        for (DiffOperation op : operations) {
            switch (op.getType()) {
                case KEEP:
                    result.add(new DiffLine(DiffLine.Type.UNCHANGED, op.getLine(), originalLineNumber));
                    originalLineNumber++;
                    mutatedLineNumber++;
                    break;
                case ADD:
                    result.add(new DiffLine(DiffLine.Type.ADDED, op.getLine(), mutatedLineNumber));
                    mutatedLineNumber++;
                    break;
                case REMOVE:
                    result.add(new DiffLine(DiffLine.Type.REMOVED, op.getLine(), originalLineNumber));
                    originalLineNumber++;
                    break;
            }
        }

        return result;
    }

    /**
     * 计算最长公共子序列表
     */
    private int[][] computeLCS(String[] a, String[] b) {
        int[][] lcs = new int[a.length + 1][b.length + 1];

        for (int i = 1; i <= a.length; i++) {
            for (int j = 1; j <= b.length; j++) {
                if (a[i - 1].equals(b[j - 1])) {
                    lcs[i][j] = lcs[i - 1][j - 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i - 1][j], lcs[i][j - 1]);
                }
            }
        }

        return lcs;
    }

    /**
     * 回溯最长公共子序列表，生成差异操作
     */
    private List<DiffOperation> backtrackLCS(int[][] lcs, String[] a, String[] b, int i, int j) {
        if (i == 0 && j == 0) {
            return new ArrayList<>();
        }

        if (i > 0 && j > 0 && a[i - 1].equals(b[j - 1])) {
            List<DiffOperation> result = backtrackLCS(lcs, a, b, i - 1, j - 1);
            result.add(new DiffOperation(DiffOperation.Type.KEEP, a[i - 1]));
            return result;
        }

        if (j > 0 && (i == 0 || lcs[i][j - 1] >= lcs[i - 1][j])) {
            List<DiffOperation> result = backtrackLCS(lcs, a, b, i, j - 1);
            result.add(new DiffOperation(DiffOperation.Type.ADD, b[j - 1]));
            return result;
        }

        if (i > 0 && (j == 0 || lcs[i][j - 1] < lcs[i - 1][j])) {
            List<DiffOperation> result = backtrackLCS(lcs, a, b, i - 1, j);
            result.add(new DiffOperation(DiffOperation.Type.REMOVE, a[i - 1]));
            return result;
        }

        return new ArrayList<>();
    }

    /**
     * 生成HTML差异展示
     */
    private String generateHtmlDiff(List<DiffLine> diffLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"code-diff\">\n");
        sb.append("  <table class=\"diff-table\">\n");

        for (DiffLine line : diffLines) {
            switch (line.getType()) {
                case UNCHANGED:
                    sb.append("    <tr class=\"diff-unchanged\">\n");
                    sb.append("      <td class=\"diff-line-num\">").append(line.getLineNumber()).append("</td>\n");
                    sb.append("      <td class=\"diff-line-num\">").append(line.getLineNumber()).append("</td>\n");
                    sb.append("      <td class=\"diff-line-content\">").append(escapeHtml(line.getContent())).append("</td>\n");
                    sb.append("    </tr>\n");
                    break;
                case ADDED:
                    sb.append("    <tr class=\"diff-added\">\n");
                    sb.append("      <td class=\"diff-line-num\"></td>\n");
                    sb.append("      <td class=\"diff-line-num\">").append(line.getLineNumber()).append("</td>\n");
                    sb.append("      <td class=\"diff-line-content\">").append(escapeHtml(line.getContent())).append("</td>\n");
                    sb.append("    </tr>\n");
                    break;
                case REMOVED:
                    sb.append("    <tr class=\"diff-removed\">\n");
                    sb.append("      <td class=\"diff-line-num\">").append(line.getLineNumber()).append("</td>\n");
                    sb.append("      <td class=\"diff-line-num\"></td>\n");
                    sb.append("      <td class=\"diff-line-content\">").append(escapeHtml(line.getContent())).append("</td>\n");
                    sb.append("    </tr>\n");
                    break;
            }
        }

        sb.append("  </table>\n");
        sb.append("</div>");

        return sb.toString();
    }

    /**
     * HTML转义
     */
    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * 差异操作类
     */
    private static class DiffOperation {
        enum Type { KEEP, ADD, REMOVE }

        private final Type type;
        private final String line;

        public DiffOperation(Type type, String line) {
            this.type = type;
            this.line = line;
        }

        public Type getType() {
            return type;
        }

        public String getLine() {
            return line;
        }
    }
}