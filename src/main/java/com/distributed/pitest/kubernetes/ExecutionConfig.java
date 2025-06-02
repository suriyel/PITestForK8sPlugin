package com.distributed.pitest.kubernetes;

/**
 * Kubernetes执行配置，支持本地镜像路径
 */
public class ExecutionConfig {
    private final int timeout;
    private final String memoryLimit;
    private final String cpuLimit;
    private final String pitestVersion;
    private final String imagePullPolicy;
    private final String baseImage;
    private final boolean useLocalImage;

    private ExecutionConfig(Builder builder) {
        this.timeout = builder.timeout;
        this.memoryLimit = builder.memoryLimit;
        this.cpuLimit = builder.cpuLimit;
        this.pitestVersion = builder.pitestVersion;
        this.imagePullPolicy = builder.imagePullPolicy;
        this.baseImage = builder.baseImage;
        this.useLocalImage = builder.useLocalImage;
    }

    public int getTimeout() {
        return timeout;
    }

    public String getMemoryLimit() {
        return memoryLimit;
    }

    public String getCpuLimit() {
        return cpuLimit;
    }

    public String getPitestVersion() {
        return pitestVersion;
    }

    public String getImagePullPolicy() {
        return imagePullPolicy;
    }

    public String getBaseImage() {
        return baseImage;
    }

    public boolean isUseLocalImage() {
        return useLocalImage;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int timeout = 1800;  // 默认30分钟
        private String memoryLimit = "1Gi";
        private String cpuLimit = "1";
        private String pitestVersion = "1.9.0";
        private String imagePullPolicy = "IfNotPresent";
        private String baseImage = "maven:3.8.5-openjdk-8";
        private boolean useLocalImage = false;

        public Builder timeout(int timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder memoryLimit(String memoryLimit) {
            this.memoryLimit = memoryLimit;
            return this;
        }

        public Builder cpuLimit(String cpuLimit) {
            this.cpuLimit = cpuLimit;
            return this;
        }

        public Builder pitestVersion(String pitestVersion) {
            this.pitestVersion = pitestVersion;
            return this;
        }

        public Builder imagePullPolicy(String imagePullPolicy) {
            this.imagePullPolicy = imagePullPolicy;
            return this;
        }

        public Builder baseImage(String baseImage) {
            this.baseImage = baseImage;
            return this;
        }

        public Builder useLocalImage(boolean useLocalImage) {
            this.useLocalImage = useLocalImage;
            return this;
        }

        public ExecutionConfig build() {
            // 当使用本地镜像时，自动设置Never策略
            if (useLocalImage && !"Never".equals(imagePullPolicy)) {
                this.imagePullPolicy = "Never";
            }

            return new ExecutionConfig(this);
        }
    }
}