package com.distributed.pitest.model;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个模块的PITest执行结果
 */
public class ModuleExecutionResult {
    private final String moduleId;
    private final String moduleName;
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final File moduleBaseDir;
    private final File resultDirectory;
    private final AggregatedResult pitestResult;
    private final long executionTimeMs;
    private final boolean successful;
    private final List<String> errors;
    private final Map<String, Object> metadata;

    private ModuleExecutionResult(Builder builder) {
        this.moduleId = builder.moduleId;
        this.moduleName = builder.moduleName;
        this.groupId = builder.groupId;
        this.artifactId = builder.artifactId;
        this.version = builder.version;
        this.moduleBaseDir = builder.moduleBaseDir;
        this.resultDirectory = builder.resultDirectory;
        this.pitestResult = builder.pitestResult;
        this.executionTimeMs = builder.executionTimeMs;
        this.successful = builder.successful;
        this.errors = new ArrayList<>(builder.errors);
        this.metadata = new HashMap<>(builder.metadata);
    }

    // Getters
    public String getModuleId() { return moduleId; }
    public String getModuleName() { return moduleName; }
    public String getGroupId() { return groupId; }
    public String getArtifactId() { return artifactId; }
    public String getVersion() { return version; }
    public File getModuleBaseDir() { return moduleBaseDir; }
    public File getResultDirectory() { return resultDirectory; }
    public AggregatedResult getPitestResult() { return pitestResult; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public boolean isSuccessful() { return successful; }
    public List<String> getErrors() { return new ArrayList<>(errors); }
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }

    /**
     * 获取模块的变异测试统计信息
     */
    public ModuleStatistics getStatistics() {
        if (pitestResult == null) {
            return new ModuleStatistics(0, 0, 0.0);
        }

        return new ModuleStatistics(
                pitestResult.getTotalMutations(),
                pitestResult.getKilledMutations(),
                pitestResult.getMutationScore()
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String moduleId;
        private String moduleName;
        private String groupId;
        private String artifactId;
        private String version;
        private File moduleBaseDir;
        private File resultDirectory;
        private AggregatedResult pitestResult;
        private long executionTimeMs;
        private boolean successful = true;
        private List<String> errors = new ArrayList<>();
        private Map<String, Object> metadata = new HashMap<>();

        public Builder moduleId(String moduleId) {
            this.moduleId = moduleId;
            return this;
        }

        public Builder moduleName(String moduleName) {
            this.moduleName = moduleName;
            return this;
        }

        public Builder groupId(String groupId) {
            this.groupId = groupId;
            return this;
        }

        public Builder artifactId(String artifactId) {
            this.artifactId = artifactId;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder moduleBaseDir(File moduleBaseDir) {
            this.moduleBaseDir = moduleBaseDir;
            return this;
        }

        public Builder resultDirectory(File resultDirectory) {
            this.resultDirectory = resultDirectory;
            return this;
        }

        public Builder pitestResult(AggregatedResult pitestResult) {
            this.pitestResult = pitestResult;
            return this;
        }

        public Builder executionTimeMs(long executionTimeMs) {
            this.executionTimeMs = executionTimeMs;
            return this;
        }

        public Builder successful(boolean successful) {
            this.successful = successful;
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

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = new HashMap<>(metadata);
            return this;
        }

        public Builder addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public ModuleExecutionResult build() {
            return new ModuleExecutionResult(this);
        }
    }

    /**
     * 模块统计信息
     */
    public static class ModuleStatistics {
        private final int totalMutations;
        private final int killedMutations;
        private final double mutationScore;

        public ModuleStatistics(int totalMutations, int killedMutations, double mutationScore) {
            this.totalMutations = totalMutations;
            this.killedMutations = killedMutations;
            this.mutationScore = mutationScore;
        }

        public int getTotalMutations() { return totalMutations; }
        public int getKilledMutations() { return killedMutations; }
        public int getSurvivedMutations() { return totalMutations - killedMutations; }
        public double getMutationScore() { return mutationScore; }
    }
}