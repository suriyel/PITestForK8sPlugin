package com.distributed.pitest.persistence;

import com.distributed.pitest.model.AggregatedResult;
import com.distributed.pitest.model.ModuleExecutionResult;
import com.distributed.pitest.model.MutationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 模块执行结果的序列化和反序列化工具
 */
public class ModuleResultSerializer {
    private static final Logger logger = LoggerFactory.getLogger(ModuleResultSerializer.class);
    private static final String RESULT_FILE_NAME = "pitest-module-result.json";
    private static final String MUTATIONS_FILE_NAME = "mutations-data.json";
    private static final String METADATA_FILE_NAME = "execution-metadata.json";

    private final ObjectMapper objectMapper;

    public ModuleResultSerializer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * 保存模块执行结果到指定目录
     *
     * @param result 模块执行结果
     * @param outputDirectory 输出目录
     * @throws IOException 如果保存失败
     */
    public void saveModuleResult(ModuleExecutionResult result, File outputDirectory) throws IOException {
        logger.info("Saving module result for: {}", result.getModuleId());

        // 确保输出目录存在
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }

        // 保存主结果文件
        File resultFile = new File(outputDirectory, RESULT_FILE_NAME);
        objectMapper.writeValue(resultFile, result);

        // 保存详细的变异数据（如果存在）
        if (result.getPitestResult() != null && !result.getPitestResult().getMutationsByClass().isEmpty()) {
            File mutationsFile = new File(outputDirectory, MUTATIONS_FILE_NAME);
            MutationDataWrapper wrapper = new MutationDataWrapper(result.getPitestResult().getMutationsByClass());
            objectMapper.writeValue(mutationsFile, wrapper);
        }

        // 保存执行元数据
        File metadataFile = new File(outputDirectory, METADATA_FILE_NAME);
        ExecutionMetadata metadata = new ExecutionMetadata(
                result.getModuleId(),
                result.getExecutionTimeMs(),
                result.isSuccessful(),
                result.getErrors(),
                result.getMetadata()
        );
        objectMapper.writeValue(metadataFile, metadata);

        logger.info("Module result saved successfully to: {}", outputDirectory);
    }

    /**
     * 从目录加载模块执行结果
     *
     * @param resultDirectory 结果目录
     * @return 模块执行结果，如果不存在则返回null
     * @throws IOException 如果加载失败
     */
    public ModuleExecutionResult loadModuleResult(File resultDirectory) throws IOException {
        File resultFile = new File(resultDirectory, RESULT_FILE_NAME);

        if (!resultFile.exists()) {
            logger.warn("Module result file not found: {}", resultFile);
            return null;
        }

        logger.info("Loading module result from: {}", resultDirectory);

        try {
            // 加载主结果
            ModuleExecutionResult result = objectMapper.readValue(resultFile, ModuleExecutionResult.class);

            // 尝试加载详细变异数据
            File mutationsFile = new File(resultDirectory, MUTATIONS_FILE_NAME);
            if (mutationsFile.exists()) {
                MutationDataWrapper wrapper = objectMapper.readValue(mutationsFile, MutationDataWrapper.class);

                // 重建AggregatedResult（如果原结果中没有）
                if (result.getPitestResult() == null || result.getPitestResult().getMutationsByClass().isEmpty()) {
                    AggregatedResult enhancedResult = rebuildAggregatedResult(wrapper.getMutationsByClass());
                    result = ModuleExecutionResult.builder()
                            .moduleId(result.getModuleId())
                            .moduleName(result.getModuleName())
                            .groupId(result.getGroupId())
                            .artifactId(result.getArtifactId())
                            .version(result.getVersion())
                            .moduleBaseDir(result.getModuleBaseDir())
                            .resultDirectory(result.getResultDirectory())
                            .pitestResult(enhancedResult)
                            .executionTimeMs(result.getExecutionTimeMs())
                            .successful(result.isSuccessful())
                            .errors(result.getErrors())
                            .metadata(result.getMetadata())
                            .build();
                }
            }

            logger.info("Module result loaded successfully: {}", result.getModuleId());
            return result;

        } catch (Exception e) {
            logger.error("Error loading module result from: {}", resultDirectory, e);
            throw new IOException("Failed to load module result", e);
        }
    }

    /**
     * 查找项目中所有的模块结果目录
     *
     * @param projectBaseDir 项目根目录
     * @return 包含PITest结果的目录列表
     */
    public List<File> findModuleResultDirectories(File projectBaseDir) {
        List<File> resultDirs = new ArrayList<>();

        try {
            Files.walk(projectBaseDir.toPath())
                    .filter(Files::isDirectory)
                    .filter(path -> path.resolve(RESULT_FILE_NAME).toFile().exists())
                    .map(Path::toFile)
                    .forEach(resultDirs::add);
        } catch (IOException e) {
            logger.error("Error scanning for module result directories", e);
        }

        logger.info("Found {} module result directories", resultDirs.size());
        return resultDirs;
    }

    /**
     * 重建AggregatedResult从变异数据
     */
    private AggregatedResult rebuildAggregatedResult(java.util.Map<String, List<MutationResult>> mutationsByClass) {
        int totalMutations = 0;
        int killedMutations = 0;

        for (List<MutationResult> mutations : mutationsByClass.values()) {
            totalMutations += mutations.size();
            killedMutations += (int) mutations.stream().filter(MutationResult::isDetected).count();
        }

        double mutationScore = totalMutations > 0 ? (double) killedMutations / totalMutations * 100.0 : 0.0;

        return AggregatedResult.builder()
                .mutationsByClass(mutationsByClass)
                .totalMutations(totalMutations)
                .killedMutations(killedMutations)
                .mutationScore(mutationScore)
                .build();
    }

    /**
     * 变异数据包装器，用于JSON序列化
     */
    public static class MutationDataWrapper {
        private java.util.Map<String, List<MutationResult>> mutationsByClass;

        public MutationDataWrapper() {}

        public MutationDataWrapper(java.util.Map<String, List<MutationResult>> mutationsByClass) {
            this.mutationsByClass = mutationsByClass;
        }

        public java.util.Map<String, List<MutationResult>> getMutationsByClass() {
            return mutationsByClass;
        }

        public void setMutationsByClass(java.util.Map<String, List<MutationResult>> mutationsByClass) {
            this.mutationsByClass = mutationsByClass;
        }
    }

    /**
     * 执行元数据
     */
    public static class ExecutionMetadata {
        private String moduleId;
        private long executionTimeMs;
        private boolean successful;
        private List<String> errors;
        private java.util.Map<String, Object> metadata;

        public ExecutionMetadata() {}

        public ExecutionMetadata(String moduleId, long executionTimeMs, boolean successful,
                                 List<String> errors, java.util.Map<String, Object> metadata) {
            this.moduleId = moduleId;
            this.executionTimeMs = executionTimeMs;
            this.successful = successful;
            this.errors = errors;
            this.metadata = metadata;
        }

        // Getters and setters
        public String getModuleId() { return moduleId; }
        public void setModuleId(String moduleId) { this.moduleId = moduleId; }

        public long getExecutionTimeMs() { return executionTimeMs; }
        public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }

        public boolean isSuccessful() { return successful; }
        public void setSuccessful(boolean successful) { this.successful = successful; }

        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }

        public java.util.Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(java.util.Map<String, Object> metadata) { this.metadata = metadata; }
    }
}