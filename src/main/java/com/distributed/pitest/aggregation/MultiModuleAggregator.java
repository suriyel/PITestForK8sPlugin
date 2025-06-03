package com.distributed.pitest.aggregation;

import com.distributed.pitest.model.ModuleExecutionResult;
import com.distributed.pitest.model.MultiModuleAggregatedResult;
import com.distributed.pitest.model.MutationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 多模块PITest结果聚合器
 */
public class MultiModuleAggregator {
    private static final Logger logger = LoggerFactory.getLogger(MultiModuleAggregator.class);

    /**
     * 聚合多个模块的执行结果
     *
     * @param moduleResults 模块执行结果列表
     * @param projectBaseDir 项目根目录
     * @return 聚合后的多模块结果
     */
    public MultiModuleAggregatedResult aggregateModuleResults(
            List<ModuleExecutionResult> moduleResults, File projectBaseDir) {

        logger.info("Aggregating results from {} modules", moduleResults.size());

        // 初始化聚合数据结构
        Map<String, List<MutationResult>> mutationsByModule = new HashMap<>();
        Map<String, List<MutationResult>> mutationsByPackage = new HashMap<>();
        Map<String, List<MutationResult>> mutationsByClass = new HashMap<>();
        List<String> allErrors = new ArrayList<>();

        // 统计数据
        int totalModules = moduleResults.size();
        int successfulModules = 0;
        int totalMutations = 0;
        int totalKilledMutations = 0;
        long totalExecutionTime = 0;
        Map<String, Integer> mutationsByMutator = new HashMap<>();
        Map<String, Double> scoresByModule = new HashMap<>();

        // 处理每个模块的结果
        for (ModuleExecutionResult moduleResult : moduleResults) {
            String moduleId = moduleResult.getModuleId();

            if (moduleResult.isSuccessful()) {
                successfulModules++;

                // 处理变异数据
                if (moduleResult.getPitestResult() != null) {
                    processModuleMutations(moduleResult, mutationsByModule,
                            mutationsByPackage, mutationsByClass);

                    // 更新统计数据
                    ModuleExecutionResult.ModuleStatistics stats = moduleResult.getStatistics();
                    totalMutations += stats.getTotalMutations();
                    totalKilledMutations += stats.getKilledMutations();
                    scoresByModule.put(moduleId, stats.getMutationScore());

                    // 统计变异器使用情况
                    updateMutatorStatistics(moduleResult, mutationsByMutator);
                }
            } else {
                allErrors.addAll(moduleResult.getErrors());
            }

            totalExecutionTime += moduleResult.getExecutionTimeMs();
        }

        // 计算整体变异分数
        double overallMutationScore = totalMutations > 0
                ? (double) totalKilledMutations / totalMutations * 100.0
                : 0.0;

        // 创建整体统计信息
        MultiModuleAggregatedResult.OverallStatistics overallStatistics =
                new MultiModuleAggregatedResult.OverallStatistics(
                        totalModules, successfulModules, totalMutations, totalKilledMutations,
                        overallMutationScore, totalExecutionTime, mutationsByMutator, scoresByModule
                );

        // 分析模块依赖关系
        Map<String, MultiModuleAggregatedResult.ModuleDependency> moduleDependencies =
                analyzeModuleDependencies(moduleResults);

        // 构建最终结果
        MultiModuleAggregatedResult result = MultiModuleAggregatedResult.builder()
                .moduleResults(moduleResults)
                .mutationsByModule(mutationsByModule)
                .mutationsByPackage(mutationsByPackage)
                .mutationsByClass(mutationsByClass)
                .overallStatistics(overallStatistics)
                .aggregationTime(LocalDateTime.now())
                .errors(allErrors)
                .moduleDependencies(moduleDependencies)
                .build();

        logger.info("Aggregation completed. Overall mutation score: {:.2f}%, Total mutations: {}",
                overallMutationScore, totalMutations);

        return result;
    }

    /**
     * 处理单个模块的变异数据
     */
    private void processModuleMutations(
            ModuleExecutionResult moduleResult,
            Map<String, List<MutationResult>> mutationsByModule,
            Map<String, List<MutationResult>> mutationsByPackage,
            Map<String, List<MutationResult>> mutationsByClass) {

        String moduleId = moduleResult.getModuleId();
        List<MutationResult> moduleMutations = new ArrayList<>();

        // 收集模块中的所有变异
        Map<String, List<MutationResult>> classMutations =
                moduleResult.getPitestResult().getMutationsByClass();

        for (Map.Entry<String, List<MutationResult>> entry : classMutations.entrySet()) {
            String className = entry.getKey();
            List<MutationResult> mutations = entry.getValue();

            // 为每个变异添加模块信息
            List<MutationResult> enhancedMutations = mutations.stream()
                    .map(mutation -> enhanceMutationWithModuleInfo(mutation, moduleResult))
                    .collect(Collectors.toList());

            // 按模块分组
            moduleMutations.addAll(enhancedMutations);

            // 按类分组（全局）
            mutationsByClass.put(className, enhancedMutations);

            // 按包分组
            String packageName = extractPackageName(className);
            mutationsByPackage.computeIfAbsent(packageName, k -> new ArrayList<>())
                    .addAll(enhancedMutations);
        }

        mutationsByModule.put(moduleId, moduleMutations);
    }

    /**
     * 为变异结果添加模块信息
     */
    private MutationResult enhanceMutationWithModuleInfo(
            MutationResult originalMutation, ModuleExecutionResult moduleResult) {

        return MutationResult.builder()
                .mutatedClass(originalMutation.getMutatedClass())
                .mutatedMethod(originalMutation.getMutatedMethod())
                .lineNumber(originalMutation.getLineNumber())
                .mutator(originalMutation.getMutator())
                .description(originalMutation.getDescription())
                .detected(originalMutation.isDetected())
                .killingTest(originalMutation.getKillingTest())
                .status(originalMutation.getStatus())
                .sourceFile(originalMutation.getSourceFile())
                .methodDescription(originalMutation.getMethodDescription())
                .indexes(originalMutation.getIndexes())
                .blocks(originalMutation.getBlocks())
                .originalCode(originalMutation.getOriginalCode())
                .mutatedCode(originalMutation.getMutatedCode())
                .firstLine(originalMutation.getFirstLine())
                .lastLine(originalMutation.getLastLine())
                .filename(originalMutation.getFilename())
                .packageName(originalMutation.getPackageName())
                // 添加模块信息作为扩展属性，可以通过description或其他字段传递
                .build();
    }

    /**
     * 更新变异器统计信息
     */
    private void updateMutatorStatistics(ModuleExecutionResult moduleResult,
                                         Map<String, Integer> mutationsByMutator) {

        if (moduleResult.getPitestResult() == null) {
            return;
        }

        for (List<MutationResult> mutations :
                moduleResult.getPitestResult().getMutationsByClass().values()) {

            for (MutationResult mutation : mutations) {
                String mutator = mutation.getMutator();
                mutationsByMutator.merge(mutator, 1, Integer::sum);
            }
        }
    }

    /**
     * 分析模块依赖关系
     */
    private Map<String, MultiModuleAggregatedResult.ModuleDependency> analyzeModuleDependencies(
            List<ModuleExecutionResult> moduleResults) {

        Map<String, MultiModuleAggregatedResult.ModuleDependency> dependencies = new HashMap<>();

        for (ModuleExecutionResult moduleResult : moduleResults) {
            String moduleId = moduleResult.getModuleId();

            // 这里简化实现，实际应该分析Maven依赖关系
            // 可以通过解析pom.xml或使用Maven API来获取真实的依赖关系
            List<String> dependsOn = analyzeDependsOn(moduleResult);
            List<String> dependedBy = analyzeDependedBy(moduleResult, moduleResults);

            MultiModuleAggregatedResult.ModuleDependency dependency =
                    new MultiModuleAggregatedResult.ModuleDependency(moduleId, dependsOn, dependedBy);

            dependencies.put(moduleId, dependency);
        }

        return dependencies;
    }

    /**
     * 分析模块依赖的其他模块
     */
    private List<String> analyzeDependsOn(ModuleExecutionResult moduleResult) {
        // 简化实现 - 实际应该解析Maven依赖
        List<String> dependsOn = new ArrayList<>();

        // 这里可以通过以下方式实现：
        // 1. 解析模块的pom.xml文件
        // 2. 检查dependencies中的其他项目模块
        // 3. 或者通过Maven API获取依赖信息

        return dependsOn;
    }

    /**
     * 分析依赖当前模块的其他模块
     */
    private List<String> analyzeDependedBy(ModuleExecutionResult currentModule,
                                           List<ModuleExecutionResult> allModules) {
        List<String> dependedBy = new ArrayList<>();

        // 简化实现 - 检查其他模块是否依赖当前模块
        String currentModuleId = currentModule.getModuleId();

        for (ModuleExecutionResult otherModule : allModules) {
            if (!otherModule.getModuleId().equals(currentModuleId)) {
                // 检查otherModule是否依赖currentModule
                List<String> otherDependencies = analyzeDependsOn(otherModule);
                if (otherDependencies.contains(currentModuleId)) {
                    dependedBy.add(otherModule.getModuleId());
                }
            }
        }

        return dependedBy;
    }

    /**
     * 从类名中提取包名
     */
    private String extractPackageName(String className) {
        int lastDotIndex = className.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return className.substring(0, lastDotIndex);
        }
        return "";
    }

    /**
     * 检测并去除重复的变异
     */
    private List<MutationResult> deduplicateMutations(List<MutationResult> mutations) {
        Set<String> seen = new HashSet<>();
        List<MutationResult> deduplicated = new ArrayList<>();

        for (MutationResult mutation : mutations) {
            String key = mutation.getUniqueKey();
            if (!seen.contains(key)) {
                seen.add(key);
                deduplicated.add(mutation);
            }
        }

        if (deduplicated.size() != mutations.size()) {
            logger.warn("Removed {} duplicate mutations during aggregation",
                    mutations.size() - deduplicated.size());
        }

        return deduplicated;
    }
}