package com.distributed.pitest.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Pitest配置信息
 */
public class PitestConfiguration {
    private final String targetClasses;
    private final String targetTests;
    private final String outputDirectory;
    private final Map<String, String> additionalProperties;

    private PitestConfiguration(Builder builder) {
        this.targetClasses = builder.targetClasses;
        this.targetTests = builder.targetTests;
        this.outputDirectory = builder.outputDirectory;
        this.additionalProperties = new HashMap<>(builder.additionalProperties);
    }

    public String getTargetClasses() {
        return targetClasses;
    }

    public String getTargetTests() {
        return targetTests;
    }

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public Map<String, String> getAdditionalProperties() {
        return new HashMap<>(additionalProperties);
    }

    @Override
    public String toString() {
        return "PitestConfiguration{" +
                "targetClasses='" + targetClasses + '\'' +
                ", targetTests='" + targetTests + '\'' +
                ", outputDirectory='" + outputDirectory + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String targetClasses;
        private String targetTests;
        private String outputDirectory;
        private Map<String, String> additionalProperties = new HashMap<>();

        public Builder targetClasses(String targetClasses) {
            this.targetClasses = targetClasses;
            return this;
        }

        public Builder targetTests(String targetTests) {
            this.targetTests = targetTests;
            return this;
        }

        public Builder outputDirectory(String outputDirectory) {
            this.outputDirectory = outputDirectory;
            return this;
        }

        public Builder additionalProperties(Map<String, String> additionalProperties) {
            this.additionalProperties = new HashMap<>(additionalProperties);
            return this;
        }

        public Builder addProperty(String key, String value) {
            this.additionalProperties.put(key, value);
            return this;
        }

        public PitestConfiguration build() {
            return new PitestConfiguration(this);
        }
    }
}