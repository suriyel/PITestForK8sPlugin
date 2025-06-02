package com.distributed.pitest.report;

import com.distributed.pitest.model.AggregatedResult;
import com.distributed.pitest.model.MutationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 标准摘要报告生成器
 */
public class StandardSummaryReportGenerator {
    private static final Logger logger = LoggerFactory.getLogger(StandardSummaryReportGenerator.class);

    /**
     * 生成文本格式的摘要报告
     *
     * @param result 聚合的测试结果
     * @param outputDirectory 输出目录
     * @throws IOException 如果写入报告时发生错误
     */
    public void generateReport(AggregatedResult result, File outputDirectory) throws IOException {
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

            // 类摘要
            writer.println("Classes:");

            Map<String, ClassSummary> classSummaries = calculateClassSummaries(result);
            for (Map.Entry<String, ClassSummary> entry : classSummaries.entrySet()) {
                String className = entry.getKey();
                ClassSummary summary = entry.getValue();

                writer.println(className + ": " + summary.killed + "/" + summary.total +
                        " (" + String.format("%.2f%%", summary.score) + ")");
            }

            writer.println();

            // 变异器摘要
            writer.println("Mutators:");

            Map<String, MutatorSummary> mutatorSummaries = calculateMutatorSummaries(result);
            for (Map.Entry<String, MutatorSummary> entry : mutatorSummaries.entrySet()) {
                String mutator = entry.getKey();
                MutatorSummary summary = entry.getValue();

                writer.println(simplifyMutatorName(mutator) + ": " + summary.killed + "/" + summary.total +
                        " (" + String.format("%.2f%%", summary.score) + ")");
            }
        }

        logger.info("Summary report generated successfully");
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