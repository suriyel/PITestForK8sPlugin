package com.distributed.pitest.model;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 表示单个测试分区的执行结果
 */
public class ExecutionResult {
    private final String partitionId;
    private final File resultDirectory;
    private final boolean successful;
    private final List<MutationResult> mutations;
    private final String errorMessage;

    private ExecutionResult(Builder builder) {
        this.partitionId = builder.partitionId;
        this.resultDirectory = builder.resultDirectory;
        this.successful = builder.successful;
        this.mutations = new ArrayList<>(builder.mutations);
        this.errorMessage = builder.errorMessage;
    }

    public String getPartitionId() {
        return partitionId;
    }

    public File getResultDirectory() {
        return resultDirectory;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public List<MutationResult> getMutations() {
        return new ArrayList<>(mutations);
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "ExecutionResult{" +
                "partitionId='" + partitionId + '\'' +
                ", successful=" + successful +
                ", mutations=" + mutations.size() +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String partitionId;
        private File resultDirectory;
        private boolean successful = true;
        private List<MutationResult> mutations = new ArrayList<>();
        private String errorMessage;

        public Builder partitionId(String partitionId) {
            this.partitionId = partitionId;
            return this;
        }

        public Builder resultDirectory(File resultDirectory) {
            this.resultDirectory = resultDirectory;
            return this;
        }

        public Builder successful(boolean successful) {
            this.successful = successful;
            return this;
        }

        public Builder mutations(List<MutationResult> mutations) {
            this.mutations = new ArrayList<>(mutations);
            return this;
        }

        public Builder addMutation(MutationResult mutation) {
            this.mutations.add(mutation);
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public ExecutionResult build() {
            return new ExecutionResult(this);
        }
    }
}