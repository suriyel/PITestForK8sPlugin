package com.distributed.pitest.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 表示一个将被单独运行的测试分区
 */
public class TestPartition {
    private final String id;
    private final List<String> targetClasses;
    private final List<String> targetTests;
    private final Map<String, String> properties;

    private TestPartition(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.targetClasses = new ArrayList<>(builder.targetClasses);
        this.targetTests = new ArrayList<>(builder.targetTests);
        this.properties = new HashMap<>(builder.properties);
    }

    public String getId() {
        return id;
    }

    public List<String> getTargetClasses() {
        return new ArrayList<>(targetClasses);
    }

    public List<String> getTargetTests() {
        return new ArrayList<>(targetTests);
    }

    public Map<String, String> getProperties() {
        return new HashMap<>(properties);
    }

    @Override
    public String toString() {
        return "TestPartition{" +
                "id='" + id + '\'' +
                ", targetClasses=" + targetClasses.size() +
                ", targetTests=" + targetTests.size() +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private List<String> targetClasses = new ArrayList<>();
        private List<String> targetTests = new ArrayList<>();
        private Map<String, String> properties = new HashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder targetClasses(List<String> targetClasses) {
            this.targetClasses = new ArrayList<>(targetClasses);
            return this;
        }

        public Builder addTargetClass(String targetClass) {
            this.targetClasses.add(targetClass);
            return this;
        }

        public Builder targetTests(List<String> targetTests) {
            this.targetTests = new ArrayList<>(targetTests);
            return this;
        }

        public Builder addTargetTest(String targetTest) {
            this.targetTests.add(targetTest);
            return this;
        }

        public Builder properties(Map<String, String> properties) {
            this.properties = new HashMap<>(properties);
            return this;
        }

        public Builder addProperty(String key, String value) {
            this.properties.put(key, value);
            return this;
        }

        public TestPartition build() {
            return new TestPartition(this);
        }
    }
}