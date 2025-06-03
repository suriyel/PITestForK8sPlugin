package com.distributed.pitest.maven;

import com.distributed.pitest.aggregation.MultiModuleAggregator;
import com.distributed.pitest.model.ModuleExecutionResult;
import com.distributed.pitest.model.MultiModuleAggregatedResult;
import com.distributed.pitest.persistence.ModuleResultSerializer;
import com.distributed.pitest.report.MultiModuleReportGenerator;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * PITest聚合报告生成器 - 类似Jacoco的report-aggregate
 * 收集所有子模块的PITest结果并生成聚合报告
 */
@Mojo(name = "report-aggregate",
        defaultPhase = LifecyclePhase.VERIFY,
        aggregator = true)  // 关键：设置为聚合器模式
public class PitestAggregateReportMojo extends AbstractMojo {

    private static final Logger logger = LoggerFactory.getLogger(PitestAggregateReportMojo.class);

    /**
     * 当前项目（通常是父项目）
     */
    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    /**
     * Reactor中的所有项目（包括子模块）
     */
    @Parameter(defaultValue = "${reactorProjects}", readonly = true)
    private List<MavenProject> reactorProjects;

    /**
     * 聚合报告输出目录
     */
    @Parameter(property = "pitest.aggregateReportDirectory",
            defaultValue = "${project.build.directory}/pit-reports-aggregate")
    private File aggregateReportDirectory;

    /**
     * 是否跳过聚合报告生成
     */
    @Parameter(property = "pitest.skip", defaultValue = "false")
    private boolean skip;

    /**
     * 是否跳过没有PITest结果的模块
     */
    @Parameter(property = "pitest.skipModulesWithoutResults", defaultValue = "true")
    private boolean skipModulesWithoutResults;

    /**
     * 模块结果搜索路径模式
     */
    @Parameter(property = "pitest.moduleResultPattern",
            defaultValue = "**/target/pitest-module-data")
    private String moduleResultPattern;

    /**
     * 包含的模块（如果为空则包含所有模块）
     */
    @Parameter(property = "pitest.includeModules")
    private List<String> includeModules;

    /**
     * 排除的模块
     */
    @Parameter(property = "pitest.excludeModules")
    private List<String> excludeModules;

    /**
     * 最小变异分数阈值（用于构建失败判断）
     */
    @Parameter(property = "pitest.minMutationScore", defaultValue = "0")
    private double minMutationScore;

    /**
     * 当变异分数低于阈值时是否失败构建
     */
    @Parameter(property = "pitest.failOnLowCoverage", defaultValue = "false")
    private boolean failOnLowCoverage;

    private final ModuleResultSerializer resultSerializer = new ModuleResultSerializer();
    private final MultiModuleAggregator aggregator = new MultiModuleAggregator();
    private final MultiModuleReportGenerator reportGenerator = new MultiModuleReportGenerator();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            logger.info("Skipping PITest aggregate report generation");
            return;
        }

        logger.info("Generating PITest aggregate report for {} modules", reactorProjects.size());
        logger.info("Aggregate report will be generated in: {}", aggregateReportDirectory);

        try {
            // 1. 收集所有模块的执行结果
            List<ModuleExecutionResult> moduleResults = collectModuleResults();

            if (moduleResults.isEmpty()) {
                logger.warn("No PITest module results found. Make sure distributed-mutationCoverage " +
                        "has been executed on the sub-modules.");
                return;
            }

            logger.info("Found {} module results to aggregate", moduleResults.size());

            // 2. 验证模块结果的完整性
            validateModuleResults(moduleResults);

            // 3. 聚合所有结果
            MultiModuleAggregatedResult aggregatedResult = aggregator.aggregateModuleResults(
                    moduleResults, project.getBasedir());

            // 4. 生成聚合报告
            if (!aggregateReportDirectory.exists()) {
                aggregateReportDirectory.mkdirs();
            }

            reportGenerator.generateAggregatedReport(aggregatedResult, aggregateReportDirectory);

            // 5. 输出摘要信息
            logAggregationSummary(aggregatedResult);

            // 6. 检查是否需要失败构建
            checkMutationScoreThreshold(aggregatedResult);

            logger.info("PITest aggregate report generation completed successfully");

        } catch (Exception e) {
            logger.error("Error generating PITest aggregate report", e);
            throw new MojoExecutionException("Failed to generate aggregate report", e);
        }
    }

    /**
     * 收集所有模块的执行结果
     */
    private List<ModuleExecutionResult> collectModuleResults() {
        List<ModuleExecutionResult> results = new ArrayList<>();

        for (MavenProject reactorProject : reactorProjects) {
            // 跳过父项目本身（通常不包含源代码）
            if (reactorProject.getPackaging().equals("pom") &&
                    reactorProject.equals(project)) {
                continue;
            }

            // 检查包含/排除规则
            if (!shouldIncludeModule(reactorProject)) {
                logger.debug("Skipping module due to include/exclude rules: {}",
                        reactorProject.getArtifactId());
                continue;
            }

            // 查找模块的PITest结果
            ModuleExecutionResult moduleResult = loadModuleResult(reactorProject);
            if (moduleResult != null) {
                results.add(moduleResult);
                logger.info("Loaded result for module: {} ({})",
                        moduleResult.getArtifactId(),
                        moduleResult.isSuccessful() ? "SUCCESS" : "FAILED");
            } else if (!skipModulesWithoutResults) {
                logger.warn("No PITest result found for module: {}", reactorProject.getArtifactId());
            }
        }

        return results;
    }

    /**
     * 从模块加载执行结果
     */
    private ModuleExecutionResult loadModuleResult(MavenProject reactorProject) {
        // 构建可能的结果目录路径
        File moduleResultDir = new File(reactorProject.getBuild().getDirectory(), "pitest-module-data");

        try {
            if (moduleResultDir.exists()) {
                return resultSerializer.loadModuleResult(moduleResultDir);
            }

            // 如果标准路径不存在，尝试搜索
            List<File> resultDirs = resultSerializer.findModuleResultDirectories(reactorProject.getBasedir());
            if (!resultDirs.isEmpty()) {
                return resultSerializer.loadModuleResult(resultDirs.get(0));
            }

        } catch (Exception e) {
            logger.warn("Error loading module result for {}: {}",
                    reactorProject.getArtifactId(), e.getMessage());
        }

        return null;
    }

    /**
     * 检查模块是否应该被包含
     */
    private boolean shouldIncludeModule(MavenProject reactorProject) {
        String moduleId = reactorProject.getGroupId() + ":" + reactorProject.getArtifactId();

        // 检查排除列表
        if (excludeModules != null) {
            for (String excludePattern : excludeModules) {
                if (moduleId.matches(excludePattern.replace("*", ".*"))) {
                    return false;
                }
            }
        }

        // 检查包含列表（如果指定了的话）
        if (includeModules != null && !includeModules.isEmpty()) {
            for (String includePattern : includeModules) {
                if (moduleId.matches(includePattern.replace("*", ".*"))) {
                    return true;
                }
            }
            return false; // 指定了包含列表但不匹配
        }

        return true; // 默认包含
    }

    /**
     * 验证模块结果的完整性
     */
    private void validateModuleResults(List<ModuleExecutionResult> moduleResults) {
        int successfulModules = 0;
        int failedModules = 0;

        for (ModuleExecutionResult result : moduleResults) {
            if (result.isSuccessful()) {
                successfulModules++;
            } else {
                failedModules++;
                logger.warn("Module {} failed with errors: {}",
                        result.getArtifactId(), result.getErrors());
            }
        }

        logger.info("Module execution summary: {} successful, {} failed",
                successfulModules, failedModules);

        if (failedModules > 0) {
            logger.warn("Some modules failed PITest execution. " +
                    "The aggregate report will include available results only.");
        }
    }

    /**
     * 输出聚合摘要信息
     */
    private void logAggregationSummary(MultiModuleAggregatedResult result) {
        MultiModuleAggregatedResult.OverallStatistics stats = result.getOverallStatistics();

        logger.info("=== PITest Aggregate Report Summary ===");
        logger.info("Total modules: {}", stats.getTotalModules());
        logger.info("Successful modules: {}", stats.getSuccessfulModules());
        logger.info("Failed modules: {}", stats.getFailedModules());
        logger.info("Total mutations: {}", stats.getTotalMutations());
        logger.info("Killed mutations: {}", stats.getTotalKilledMutations());
        logger.info("Survived mutations: {}", stats.getTotalSurvivedMutations());
        logger.info("Overall mutation score: {:.2f}%", stats.getOverallMutationScore());
        logger.info("Total execution time: {:.2f} seconds", stats.getTotalExecutionTimeMs() / 1000.0);
        logger.info("=====================================");

        // 按模块输出详细信息
        logger.info("Module breakdown:");
        for (ModuleExecutionResult moduleResult : result.getModuleResults()) {
            if (moduleResult.isSuccessful()) {
                ModuleExecutionResult.ModuleStatistics moduleStats = moduleResult.getStatistics();
                logger.info("  {}: {}/{} mutations killed ({:.2f}%)",
                        moduleResult.getArtifactId(),
                        moduleStats.getKilledMutations(),
                        moduleStats.getTotalMutations(),
                        moduleStats.getMutationScore());
            } else {
                logger.info("  {}: FAILED", moduleResult.getArtifactId());
            }
        }
    }

    /**
     * 检查变异分数阈值
     */
    private void checkMutationScoreThreshold(MultiModuleAggregatedResult result)
            throws MojoFailureException {
        if (!failOnLowCoverage) {
            return;
        }

        double overallScore = result.getOverallStatistics().getOverallMutationScore();
        if (overallScore < minMutationScore) {
            String message = String.format(
                    "Overall mutation score %.2f%% is below the threshold of %.2f%%",
                    overallScore, minMutationScore);
            logger.error(message);
            throw new MojoFailureException(message);
        }
    }
}