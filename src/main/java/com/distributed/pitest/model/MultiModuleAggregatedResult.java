package com.distributed.pitest.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多模块聚合的PITest结果
 */
public class MultiModuleAggregatedResult {
    private final List<ModuleExecutionResult> moduleResults;
    private final Map<String, List<MutationResult>> mutationsByModule;
    private final Map<String, List<MutationResult>> mutationsByPackage;
    private final Map<String, List<MutationResult>> mutationsByClass;
    private final OverallStatistics overallStatistics;
    private final LocalDateTime aggregationTime;
    private final List<String> errors;
    private final Map<String, ModuleDependency> moduleDependencies;

    private MultiModuleAggregatedResult(Builder builder) {
        this.moduleResults = new ArrayList<>(builder.moduleResults);
        this.mutationsByModule = new HashMap<>(builder.mutationsByModule);
        this.mutationsByPackage = new HashMap<>(builder.mutationsByPackage);
        this.mutationsByClass = new HashMap<>(builder.mutationsByClass);
        this.overallStatistics = builder.overallStatistics;
        this.aggregationTime = builder.aggregationTime;
        this.errors = new ArrayList<>(builder.errors);
        this.moduleDependencies = new HashMap<>(builder.moduleDependencies);
    }

    // Getters
    public List<ModuleExecutionResult> getModuleResults() {
        return new ArrayList<>(moduleResults);
    }

    public Map<String, List<MutationResult>> getMutationsByModule() {
        return new HashMap<>(mutationsByModule);
    }

    public Map<String, List<MutationResult>> getMutationsByPackage() {
        return new HashMap<>(mutationsByPackage);
    }

    public Map<String, List<MutationResult>> getMutationsByClass() {
        return new HashMap<>(mutationsByClass);
    }

    public OverallStatistics getOverallStatistics() {
        return overallStatistics;
    }

    public LocalDateTime getAggregationTime() {
        return aggregationTime;
    }

    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }

    public Map<String, ModuleDependency> getModuleDependencies() {
        return new HashMap<>(moduleDependencies);
    }

    /**
     * 获取成功执行的模块数量
     */
    public int getSuccessfulModuleCount() {
        return (int) moduleResults.stream().filter(ModuleExecutionResult::isSuccessful).count();
    }

    /**
     * 获取失败的模块数量
     */
    public int getFailedModuleCount() {
        return moduleResults.size() - getSuccessfulModuleCount();
    }

    /**
     * 检查是否有错误
     */
    public boolean hasErrors() {
        return !errors.isEmpty() || moduleResults.stream().anyMatch(r -> !r.isSuccessful());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<ModuleExecutionResult> moduleResults = new ArrayList<>();
        private Map<String, List<MutationResult>> mutationsByModule = new HashMap<>();
        private Map<String, List<MutationResult>> mutationsByPackage = new HashMap<>();
        private Map<String, List<MutationResult>> mutationsByClass = new HashMap<>();
        private OverallStatistics overallStatistics;
        private LocalDateTime aggregationTime = LocalDateTime.now();
        private List<String> errors = new ArrayList<>();
        private Map<String, ModuleDependency> moduleDependencies = new HashMap<>();

        public Builder moduleResults(List<ModuleExecutionResult> moduleResults) {
            this.moduleResults = new ArrayList<>(moduleResults);
            return this;
        }

        public Builder addModuleResult(ModuleExecutionResult moduleResult) {
            this.moduleResults.add(moduleResult);
            return this;
        }

        public Builder mutationsByModule(Map<String, List<MutationResult>> mutationsByModule) {
            this.mutationsByModule = new HashMap<>(mutationsByModule);
            return this;
        }

        public Builder mutationsByPackage(Map<String, List<MutationResult>> mutationsByPackage) {
            this.mutationsByPackage = new HashMap<>(mutationsByPackage);
            return this;
        }

        public Builder mutationsByClass(Map<String, List<MutationResult>> mutationsByClass) {
            this.mutationsByClass = new HashMap<>(mutationsByClass);
            return this;
        }

        public Builder overallStatistics(OverallStatistics overallStatistics) {
            this.overallStatistics = overallStatistics;
            return this;
        }

        public Builder aggregationTime(LocalDateTime aggregationTime) {
            this.aggregationTime = aggregationTime;
            return this;
        }

        public Builder errors(List<String> errors) {
            this.errors = new ArrayList<>(errors);
            return this;
        }

        public Builder addError(String error) {
            this.errors.add(error);
            return this;
        }

        public Builder moduleDependencies(Map<String, ModuleDependency> moduleDependencies) {
            this.moduleDependencies = new HashMap<>(moduleDependencies);
            return this;
        }

        public MultiModuleAggregatedResult build() {
            return new MultiModuleAggregatedResult(this);
        }
    }

    /**
     * 整体统计信息
     */
    public static class OverallStatistics {
        private final int totalModules;
        private final int successfulModules;
        private final int totalMutations;
        private final int totalKilledMutations;
        private final double overallMutationScore;
        private final long totalExecutionTimeMs;
        private final Map<String, Integer> mutationsByMutator;
        private final Map<String, Double> scoresByModule;

        public OverallStatistics(int totalModules, int successfulModules, int totalMutations,
                                 int totalKilledMutations, double overallMutationScore,
                                 long totalExecutionTimeMs,
                                 Map<String, Integer> mutationsByMutator,
                                 Map<String, Double> scoresByModule) {
            this.totalModules = totalModules;
            this.successfulModules = successfulModules;
            this.totalMutations = totalMutations;
            this.totalKilledMutations = totalKilledMutations;
            this.overallMutationScore = overallMutationScore;
            this.totalExecutionTimeMs = totalExecutionTimeMs;
            this.mutationsByMutator = new HashMap<>(mutationsByMutator);
            this.scoresByModule = new HashMap<>(scoresByModule);
        }

        // Getters
        public int getTotalModules() { return totalModules; }
        public int getSuccessfulModules() { return successfulModules; }
        public int getFailedModules() { return totalModules - successfulModules; }
        public int getTotalMutations() { return totalMutations; }
        public int getTotalKilledMutations() { return totalKilledMutations; }
        public int getTotalSurvivedMutations() { return totalMutations - totalKilledMutations; }
        public double getOverallMutationScore() { return overallMutationScore; }
        public long getTotalExecutionTimeMs() { return totalExecutionTimeMs; }
        public Map<String, Integer> getMutationsByMutator() { return new HashMap<>(mutationsByMutator); }
        public Map<String, Double> getScoresByModule() { return new HashMap<>(scoresByModule); }
    }

    /**
     * 模块依赖信息
     */
    public static class ModuleDependency {
        private final String moduleId;
        private final List<String> dependsOn;
        private final List<String> dependedBy;

        public ModuleDependency(String moduleId, List<String> dependsOn, List<String> dependedBy) {
            this.moduleId = moduleId;
            this.dependsOn = new ArrayList<>(dependsOn);
            this.dependedBy = new ArrayList<>(dependedBy);
        }

        public String getModuleId() { return moduleId; }
        public List<String> getDependsOn() { return new ArrayList<>(dependsOn); }
        public List<String> getDependedBy() { return new ArrayList<>(dependedBy); }
    }
}