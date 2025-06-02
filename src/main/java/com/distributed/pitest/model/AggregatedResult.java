package com.distributed.pitest.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 聚合后的突变测试结果
 */
public class AggregatedResult {
    private final Map<String, List<MutationResult>> mutationsByClass;
    private final int totalMutations;
    private final int killedMutations;
    private final double mutationScore;
    private final List<String> errors;

    private AggregatedResult(Builder builder) {
        this.mutationsByClass = new HashMap<>(builder.mutationsByClass);
        this.totalMutations = builder.totalMutations;
        this.killedMutations = builder.killedMutations;
        this.mutationScore = builder.mutationScore;
        this.errors = new ArrayList<>(builder.errors);
    }

    public Map<String, List<MutationResult>> getMutationsByClass() {
        Map<String, List<MutationResult>> result = new HashMap<>();
        mutationsByClass.forEach((key, value) -> result.put(key, new ArrayList<>(value)));
        return result;
    }

    public int getTotalMutations() {
        return totalMutations;
    }

    public int getKilledMutations() {
        return killedMutations;
    }

    public double getMutationScore() {
        return mutationScore;
    }

    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    @Override
    public String toString() {
        return "AggregatedResult{" +
                "totalMutations=" + totalMutations +
                ", killedMutations=" + killedMutations +
                ", mutationScore=" + mutationScore +
                ", errors=" + errors.size() +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Map<String, List<MutationResult>> mutationsByClass = new HashMap<>();
        private int totalMutations = 0;
        private int killedMutations = 0;
        private double mutationScore = 0.0;
        private List<String> errors = new ArrayList<>();

        public Builder mutationsByClass(Map<String, List<MutationResult>> mutationsByClass) {
            this.mutationsByClass = new HashMap<>();
            mutationsByClass.forEach((key, value) -> this.mutationsByClass.put(key, new ArrayList<>(value)));
            return this;
        }

        public Builder addMutationsForClass(String className, List<MutationResult> mutations) {
            this.mutationsByClass.put(className, new ArrayList<>(mutations));
            return this;
        }

        public Builder totalMutations(int totalMutations) {
            this.totalMutations = totalMutations;
            return this;
        }

        public Builder killedMutations(int killedMutations) {
            this.killedMutations = killedMutations;
            return this;
        }

        public Builder mutationScore(double mutationScore) {
            this.mutationScore = mutationScore;
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

        public AggregatedResult build() {
            return new AggregatedResult(this);
        }
    }
}