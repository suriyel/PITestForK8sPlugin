package com.distributed.pitest.result;

import com.distributed.pitest.model.AggregatedResult;
import com.distributed.pitest.model.ExecutionResult;
import com.distributed.pitest.model.MutationResult;
import com.distributed.pitest.report.ReportGenerator;
import com.distributed.pitest.util.EnhancedXmlReportParserWithAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 完全增强的Pitest结果聚合器，使用代码分析和变异分析生成详细报告
 */
public class PitestResultAggregator implements ResultAggregator {
    private static final Logger logger = LoggerFactory.getLogger(PitestResultAggregator.class);
    private final ReportGenerator reportGenerator;
    private final EnhancedXmlReportParserWithAnalyzer xmlParser;
    private final File projectBaseDir;

    public PitestResultAggregator(ReportGenerator reportGenerator, File projectBaseDir) {
        this.reportGenerator = reportGenerator;
        this.xmlParser = new EnhancedXmlReportParserWithAnalyzer();
        this.projectBaseDir = projectBaseDir;

        logger.info("Initialized CompleteEnhancedPitestResultAggregator with project dir: {}",
                projectBaseDir.getAbsolutePath());
    }

    @Override
    public AggregatedResult aggregateResults(List<ExecutionResult> results) {
        logger.info("Aggregating results from {} partitions with full mutation details", results.size());

        // 跟踪错误
        List<String> errors = new ArrayList<>();

        // 按类聚合突变结果，使用Map确保每个唯一变异只保留一个实例
        Map<String, List<MutationResult>> mutationsByClass = new HashMap<>();
        Map<String, MutationResult> uniqueMutations = new HashMap<>();

        // 统计数据
        int totalMutations = 0;
        int killedMutations = 0;

        // 分区ID追踪
        Set<String> processedPartitions = new HashSet<>();

        for (ExecutionResult result : results) {
            String partitionId = result.getPartitionId();

            if (!result.isSuccessful()) {
                errors.add("Partition " + partitionId + " failed: " + result.getErrorMessage());
                continue;
            }

            if (processedPartitions.contains(partitionId)) {
                logger.warn("Duplicate partition ID found: {}, skipping", partitionId);
                continue;
            }

            processedPartitions.add(partitionId);

            // 处理结果中的变异
            List<MutationResult> mutations = getEnhancedMutationsWithAnalysis(result);
            logger.info("Processing {} mutations with analysis from partition {}", mutations.size(), partitionId);

            for (MutationResult mutation : mutations) {
                String className = mutation.getMutatedClass();
                String uniqueKey = mutation.getUniqueKey();

                // 如果发现重复的变异，选择策略是保留已杀死的
                if (uniqueMutations.containsKey(uniqueKey)) {
                    MutationResult existingMutation = uniqueMutations.get(uniqueKey);
                    if (!existingMutation.isDetected() && mutation.isDetected()) {
                        // 新的变异被杀死，旧的没有，替换它
                        uniqueMutations.put(uniqueKey, mutation);
                        updateMutationsByClass(mutationsByClass, className, uniqueKey, mutation);
                    }
                } else {
                    // 新的唯一变异
                    uniqueMutations.put(uniqueKey, mutation);

                    // 添加到按类分组的Map
                    if (!mutationsByClass.containsKey(className)) {
                        mutationsByClass.put(className, new ArrayList<>());
                    }
                    mutationsByClass.get(className).add(mutation);
                }
            }
        }

        // 汇总统计
        for (MutationResult mutation : uniqueMutations.values()) {
            totalMutations++;
            if (mutation.isDetected()) {
                killedMutations++;
            }
        }

        // 计算突变分数
        double mutationScore = totalMutations > 0
                ? (double) killedMutations / totalMutations * 100.0
                : 0.0;

        logger.info("Fully enhanced aggregation complete. Total mutations: {}, Killed: {}, Score: {}%, " +
                        "Unique mutations: {}",
                totalMutations, killedMutations, mutationScore, uniqueMutations.size());

        if (!errors.isEmpty()) {
            logger.warn("Errors occurred during execution: {}", errors.size());
            for (String error : errors) {
                logger.warn(error);
            }
        }

        return AggregatedResult.builder()
                .mutationsByClass(mutationsByClass)
                .totalMutations(totalMutations)
                .killedMutations(killedMutations)
                .mutationScore(mutationScore)
                .errors(errors)
                .build();
    }

    /**
     * 更新按类分组的变异映射，替换特定key的变异
     */
    private void updateMutationsByClass(Map<String, List<MutationResult>> mutationsByClass,
                                        String className, String uniqueKey, MutationResult newMutation) {
        if (!mutationsByClass.containsKey(className)) {
            mutationsByClass.put(className, new ArrayList<>());
            mutationsByClass.get(className).add(newMutation);
            return;
        }

        List<MutationResult> classMutations = mutationsByClass.get(className);
        for (int i = 0; i < classMutations.size(); i++) {
            if (classMutations.get(i).getUniqueKey().equals(uniqueKey)) {
                classMutations.set(i, newMutation);
                return;
            }
        }

        // 如果没找到，添加新的
        classMutations.add(newMutation);
    }

    /**
     * 从执行结果中获取增强的变异详情，包括代码分析
     */
    private List<MutationResult> getEnhancedMutationsWithAnalysis(ExecutionResult result) {
        // 首先尝试从结果中直接获取
        List<MutationResult> mutations = result.getMutations();
        if (!mutations.isEmpty()) {
            return mutations;
        }

        // 如果结果不包含变异，尝试解析结果目录中的XML文件
        File resultDir = result.getResultDirectory();
        if (resultDir != null && resultDir.exists()) {
            File mutationsXml = new File(resultDir, "mutations.xml");
            if (mutationsXml.exists()) {
                try {
                    return xmlParser.parseMutationXml(mutationsXml, projectBaseDir);
                } catch (IOException e) {
                    logger.error("Error parsing mutations XML from {}", mutationsXml, e);
                }
            }
        }

        return new ArrayList<>();
    }

    @Override
    public void generateReport(AggregatedResult result, File outputDirectory) {
        logger.info("Generating fully enhanced report in directory: {}", outputDirectory);

        try {
            // 确保输出目录存在
            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs();
            }

            // 复制聚合结果中的源文件信息到目标目录，以便报告生成
            prepareSupportingFiles(result, outputDirectory);

            // 使用报告生成器生成聚合报告
            reportGenerator.generateReport(result, outputDirectory);

            logger.info("Fully enhanced report generation complete");

        } catch (Exception e) {
            logger.error("Error generating fully enhanced report", e);
        }
    }

    /**
     * 准备支持文件，如源代码等
     */
    private void prepareSupportingFiles(AggregatedResult result, File outputDirectory) throws IOException {
        // 创建源代码目录
        File sourceDir = new File(outputDirectory, "sources");
        if (!sourceDir.exists()) {
            sourceDir.mkdirs();
        }

        // 复制相关的源文件
        Set<String> processedClasses = new HashSet<>();
        for (Map.Entry<String, List<MutationResult>> entry : result.getMutationsByClass().entrySet()) {
            String className = entry.getKey();

            if (processedClasses.contains(className)) {
                continue;
            }

            processedClasses.add(className);

            // 检查是否有任何变异包含源文件信息
            for (MutationResult mutation : entry.getValue()) {
                String packagePath = mutation.getPackageName().replace('.', File.separatorChar);
                String sourceFileName = mutation.getSourceFile();

                if (sourceFileName != null && !sourceFileName.isEmpty()) {
                    // 尝试在项目源码目录中查找源文件
                    File sourceFile = findSourceFile(packagePath, sourceFileName);

                    if (sourceFile != null && sourceFile.exists()) {
                        // 创建目标目录
                        File targetPackageDir = new File(sourceDir, packagePath);
                        if (!targetPackageDir.exists()) {
                            targetPackageDir.mkdirs();
                        }

                        // 复制源文件
                        File targetFile = new File(targetPackageDir, sourceFileName);
                        Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        logger.debug("Copied source file: {} to {}", sourceFile, targetFile);
                    }
                }
            }
        }
    }

    /**
     * 查找源文件
     */
    private File findSourceFile(String packagePath, String sourceFileName) {
        // 先尝试src/main/java
        File mainSourceDir = new File(projectBaseDir, "src/main/java");
        File sourceFile = new File(mainSourceDir, packagePath + File.separator + sourceFileName);
        if (sourceFile.exists()) {
            return sourceFile;
        }

        // 再尝试src/test/java
        File testSourceDir = new File(projectBaseDir, "src/test/java");
        sourceFile = new File(testSourceDir, packagePath + File.separator + sourceFileName);
        if (sourceFile.exists()) {
            return sourceFile;
        }

        // 最后尝试在其他可能的源码目录查找
        List<File> candidateDirs = findSourceDirs(projectBaseDir);
        for (File dir : candidateDirs) {
            sourceFile = new File(dir, packagePath + File.separator + sourceFileName);
            if (sourceFile.exists()) {
                return sourceFile;
            }
        }

        return null;
    }

    /**
     * 查找项目中所有可能的源码目录
     */
    private List<File> findSourceDirs(File projectBaseDir) {
        List<File> sourceDirs = new ArrayList<>();

        // 查找所有**/java目录
        try {
            Files.walk(projectBaseDir.toPath())
                    .filter(path -> path.toString().endsWith("/java") || path.toString().endsWith("\\java"))
                    .filter(path -> Files.isDirectory(path))
                    .forEach(path -> sourceDirs.add(path.toFile()));
        } catch (IOException e) {
            logger.warn("Error searching for source directories", e);
        }

        return sourceDirs;
    }
}