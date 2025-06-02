package com.distributed.pitest.model;

/**
 * 表示单个突变测试的详细结果，包含代码行和变异细节
 */
public class MutationResult {
    private final String mutatedClass;
    private final String mutatedMethod;
    private final int lineNumber;
    private final String mutator;
    private final String description;
    private final boolean detected;
    private final String killingTest;
    private final String status;

    // 新增字段，用于更详细地描述变异
    private final String sourceFile;
    private final String methodDescription;
    private final int[] indexes;
    private final int[] blocks;
    private final String originalCode;
    private final String mutatedCode;
    private final int firstLine;
    private final int lastLine;
    private final String filename;
    private final String packageName;

    private MutationResult(Builder builder) {
        this.mutatedClass = builder.mutatedClass;
        this.mutatedMethod = builder.mutatedMethod;
        this.lineNumber = builder.lineNumber;
        this.mutator = builder.mutator;
        this.description = builder.description;
        this.detected = builder.detected;
        this.killingTest = builder.killingTest;
        this.status = builder.status;
        this.sourceFile = builder.sourceFile;
        this.methodDescription = builder.methodDescription;
        this.indexes = builder.indexes;
        this.blocks = builder.blocks;
        this.originalCode = builder.originalCode;
        this.mutatedCode = builder.mutatedCode;
        this.firstLine = builder.firstLine;
        this.lastLine = builder.lastLine;
        this.filename = builder.filename;
        this.packageName = builder.packageName;
    }

    public String getMutatedClass() {
        return mutatedClass;
    }

    public String getMutatedMethod() {
        return mutatedMethod;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getMutator() {
        return mutator;
    }

    public String getDescription() {
        return description;
    }

    public boolean isDetected() {
        return detected;
    }

    public String getKillingTest() {
        return killingTest;
    }

    public String getStatus() {
        return status;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public String getMethodDescription() {
        return methodDescription;
    }

    public int[] getIndexes() {
        return indexes != null ? indexes.clone() : new int[0];
    }

    public int[] getBlocks() {
        return blocks != null ? blocks.clone() : new int[0];
    }

    public String getOriginalCode() {
        return originalCode;
    }

    public String getMutatedCode() {
        return mutatedCode;
    }

    public int getFirstLine() {
        return firstLine;
    }

    public int getLastLine() {
        return lastLine;
    }

    public String getFilename() {
        return filename;
    }

    public String getPackageName() {
        return packageName;
    }

    // 生成唯一标识该变异的键
    public String getUniqueKey() {
        return mutatedClass + ":" + mutatedMethod + ":" + lineNumber + ":" + mutator;
    }

    @Override
    public String toString() {
        return "MutationResult{" +
                "mutatedClass='" + mutatedClass + '\'' +
                ", mutatedMethod='" + mutatedMethod + '\'' +
                ", lineNumber=" + lineNumber +
                ", mutator='" + mutator + '\'' +
                ", detected=" + detected +
                ", status='" + status + '\'' +
                ", sourceFile='" + sourceFile + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String mutatedClass;
        private String mutatedMethod;
        private int lineNumber;
        private String mutator;
        private String description;
        private boolean detected;
        private String killingTest;
        private String status;
        private String sourceFile;
        private String methodDescription;
        private int[] indexes;
        private int[] blocks;
        private String originalCode;
        private String mutatedCode;
        private int firstLine;
        private int lastLine;
        private String filename;
        private String packageName;

        public Builder mutatedClass(String mutatedClass) {
            this.mutatedClass = mutatedClass;
            return this;
        }

        public Builder mutatedMethod(String mutatedMethod) {
            this.mutatedMethod = mutatedMethod;
            return this;
        }

        public Builder lineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
            return this;
        }

        public Builder mutator(String mutator) {
            this.mutator = mutator;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder detected(boolean detected) {
            this.detected = detected;
            return this;
        }

        public Builder killingTest(String killingTest) {
            this.killingTest = killingTest;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder sourceFile(String sourceFile) {
            this.sourceFile = sourceFile;
            return this;
        }

        public Builder methodDescription(String methodDescription) {
            this.methodDescription = methodDescription;
            return this;
        }

        public Builder indexes(int[] indexes) {
            this.indexes = indexes != null ? indexes.clone() : null;
            return this;
        }

        public Builder blocks(int[] blocks) {
            this.blocks = blocks != null ? blocks.clone() : null;
            return this;
        }

        public Builder originalCode(String originalCode) {
            this.originalCode = originalCode;
            return this;
        }

        public Builder mutatedCode(String mutatedCode) {
            this.mutatedCode = mutatedCode;
            return this;
        }

        public Builder firstLine(int firstLine) {
            this.firstLine = firstLine;
            return this;
        }

        public Builder lastLine(int lastLine) {
            this.lastLine = lastLine;
            return this;
        }

        public Builder filename(String filename) {
            this.filename = filename;
            return this;
        }

        public Builder packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public MutationResult build() {
            return new MutationResult(this);
        }
    }
}