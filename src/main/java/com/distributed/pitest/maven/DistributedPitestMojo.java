package com.distributed.pitest.maven;

import com.distributed.pitest.kubernetes.ExecutionConfig;
import com.distributed.pitest.kubernetes.KubernetesExecutor;
import com.distributed.pitest.kubernetes.KubernetesExecutorImpl;
import com.distributed.pitest.model.*;
import com.distributed.pitest.partition.DefaultProjectPartitioner;
import com.distributed.pitest.partition.PackageBasedPartitioner;
import com.distributed.pitest.partition.ProjectPartitioner;
import com.distributed.pitest.persistence.ModuleResultSerializer;
import com.distributed.pitest.report.CompleteEnhancedReportGenerator;
import com.distributed.pitest.report.ReportGenerator;
import com.distributed.pitest.result.PitestResultAggregator;
import com.distributed.pitest.result.ResultAggregator;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Maven plugin for distributed PIT mutation testing using Kubernetes with enhanced reporting
 */
@Mojo(name = "distributed-mutationCoverage",
        defaultPhase = LifecyclePhase.VERIFY,
        requiresDependencyResolution = ResolutionScope.TEST)
public class DistributedPitestMojo extends AbstractMojo {

    private static final Logger logger = LoggerFactory.getLogger(DistributedPitestMojo.class);

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(property = "partitionStrategy", defaultValue = "PACKAGE")
    private String partitionStrategy;

    @Parameter(property = "k8sNamespace", defaultValue = "default")
    private String namespace;

    @Parameter(property = "maxParallelPods", defaultValue = "5")
    private int maxParallelPods;

    @Parameter(property = "targetClasses")
    private String targetClasses;

    @Parameter(property = "targetTests")
    private String targetTests;

    @Parameter(property = "outputDirectory", defaultValue = "${project.build.directory}/pit-reports")
    private File outputDirectory;

    @Parameter(property = "timeoutInSeconds", defaultValue = "1800")
    private int timeoutInSeconds;

    @Parameter(property = "podMemoryLimit", defaultValue = "1Gi")
    private String podMemoryLimit;

    @Parameter(property = "podCpuLimit", defaultValue = "1")
    private String podCpuLimit;

    @Parameter(property = "pitestVersion", defaultValue = "1.9.0")
    private String pitestVersion;

    @Parameter(property = "skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "skipTests", defaultValue = "false")
    private boolean skipTests;

    @Parameter(property = "kubeConfigPath")
    private String kubeConfigPath;

    @Parameter(property = "imagePullPolicy", defaultValue = "IfNotPresent")
    private String imagePullPolicy;

    @Parameter(property = "baseImage", defaultValue = "maven:3.8.5-openjdk-8")
    private String baseImage;

    /**
     * 是否保存模块结果供聚合使用（默认启用）
     */
    @Parameter(property = "saveModuleResult", defaultValue = "true")
    private boolean saveModuleResult;

    /**
     * 模块结果保存目录
     */
    @Parameter(property = "moduleResultDirectory",
            defaultValue = "${project.build.directory}/pitest-module-data")
    private File moduleResultDirectory;

    private final ModuleResultSerializer resultSerializer = new ModuleResultSerializer();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip || skipTests) {
            logger.info("Skipping distributed PIT execution as per configuration");
            return;
        }

        logger.info("Starting distributed PIT execution for module: {} with strategy: {}",
                project.getArtifactId(), partitionStrategy);

        long startTime = System.currentTimeMillis();

        try {
            // 1. 初始化组件
            ProjectPartitioner partitioner = createPartitioner();
            KubernetesExecutor executor = createExecutor();
            ResultAggregator aggregator = createAggregator();

            // 2. 分区项目
            List<TestPartition> partitions = partitioner.partitionProject(project, buildConfig());
            logger.info("Project partitioned into {} parts", partitions.size());

            if (partitions.isEmpty()) {
                logger.warn("No test partitions found for module: {}", project.getArtifactId());
                return;
            }

            // 3. 执行测试（并行）
            ExecutorService executorService = Executors.newFixedThreadPool(
                    Math.min(partitions.size(), maxParallelPods));
            List<Future<ExecutionResult>> futures = new ArrayList<>();

            for (TestPartition partition : partitions) {
                futures.add(executorService.submit(() ->
                        executor.executeTests(partition, buildExecutionConfig())));
            }

            executorService.shutdown();
            if (!executorService.awaitTermination(timeoutInSeconds, TimeUnit.SECONDS)) {
                logger.warn("Timeout occurred while waiting for test execution");
                executorService.shutdownNow();
            }

            // 收集结果
            List<ExecutionResult> results = futures.stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            logger.error("Error getting execution result", e);
                            return null;
                        }
                    })
                    .filter(result -> result != null)
                    .collect(Collectors.toList());

            // 4. 聚合结果
            AggregatedResult aggregatedResult = aggregator.aggregateResults(results);
            logger.info("Results aggregated. Total mutations: {}, Killed: {}, Score: {}%",
                    aggregatedResult.getTotalMutations(),
                    aggregatedResult.getKilledMutations(),
                    aggregatedResult.getMutationScore());

            // 5. 生成报告
            aggregator.generateReport(aggregatedResult, outputDirectory);
            logger.info("PIT report generated at: {}", outputDirectory.getAbsolutePath());

            // 6. 保存模块结果（用于聚合）
            if (saveModuleResult) {
                saveModuleExecutionResult(aggregatedResult, startTime);
            }

            // 7. 清理资源
            executor.cleanupResources();
            logger.info("Kubernetes resources cleaned up");

            // 8. 检查执行状态
            if (aggregatedResult.hasErrors()) {
                logger.warn("Execution completed with {} errors", aggregatedResult.getErrors().size());
                for (String error : aggregatedResult.getErrors()) {
                    logger.warn("Error: {}", error);
                }
            }

        } catch (Exception e) {
            logger.error("Error executing distributed PIT for module: {}", project.getArtifactId(), e);

            // 即使执行失败，也保存失败信息供聚合使用
            if (saveModuleResult) {
                saveFailedModuleResult(e, startTime);
            }

            throw new MojoExecutionException("Error executing distributed PIT", e);
        }
    }

    /**
     * 保存模块执行结果
     */
    private void saveModuleExecutionResult(AggregatedResult aggregatedResult, long startTime) {
        try {
            long executionTime = System.currentTimeMillis() - startTime;

            ModuleExecutionResult moduleResult = ModuleExecutionResult.builder()
                    .moduleId(generateModuleId())
                    .moduleName(project.getName())
                    .groupId(project.getGroupId())
                    .artifactId(project.getArtifactId())
                    .version(project.getVersion())
                    .moduleBaseDir(project.getBasedir())
                    .resultDirectory(outputDirectory)
                    .pitestResult(aggregatedResult)
                    .executionTimeMs(executionTime)
                    .successful(true)
                    .errors(aggregatedResult.getErrors())
                    .addMetadata("maven.plugin.version", getClass().getPackage().getImplementationVersion())
                    .addMetadata("execution.timestamp", System.currentTimeMillis())
                    .addMetadata("execution.strategy", partitionStrategy)
                    .addMetadata("k8s.namespace", namespace)
                    .addMetadata("k8s.maxParallelPods", maxParallelPods)
                    .build();

            // 确保目录存在
            if (!moduleResultDirectory.exists()) {
                moduleResultDirectory.mkdirs();
            }

            // 保存结果
            resultSerializer.saveModuleResult(moduleResult, moduleResultDirectory);
            logger.info("Module execution result saved to: {}", moduleResultDirectory);

        } catch (IOException e) {
            logger.error("Failed to save module execution result", e);
            // 不抛出异常，因为这不应该影响主要的执行流程
        }
    }

    /**
     * 保存失败的模块结果
     */
    private void saveFailedModuleResult(Exception error, long startTime) {
        try {
            long executionTime = System.currentTimeMillis() - startTime;

            ModuleExecutionResult moduleResult = ModuleExecutionResult.builder()
                    .moduleId(generateModuleId())
                    .moduleName(project.getName())
                    .groupId(project.getGroupId())
                    .artifactId(project.getArtifactId())
                    .version(project.getVersion())
                    .moduleBaseDir(project.getBasedir())
                    .resultDirectory(outputDirectory)
                    .executionTimeMs(executionTime)
                    .successful(false)
                    .addError("Execution failed: " + error.getMessage())
                    .addMetadata("maven.plugin.version", getClass().getPackage().getImplementationVersion())
                    .addMetadata("execution.timestamp", System.currentTimeMillis())
                    .addMetadata("failure.exception", error.getClass().getSimpleName())
                    .build();

            // 确保目录存在
            if (!moduleResultDirectory.exists()) {
                moduleResultDirectory.mkdirs();
            }

            // 保存失败结果
            resultSerializer.saveModuleResult(moduleResult, moduleResultDirectory);
            logger.info("Failed module execution result saved to: {}", moduleResultDirectory);

        } catch (IOException e) {
            logger.error("Failed to save failed module execution result", e);
        }
    }

    /**
     * 生成模块ID
     */
    private String generateModuleId() {
        return project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion();
    }

    // 保持原有的工厂方法
    protected ProjectPartitioner createPartitioner() {
        switch (partitionStrategy.toUpperCase()) {
            case "PACKAGE":
                return new PackageBasedPartitioner();
            case "DEFAULT":
            default:
                return new DefaultProjectPartitioner();
        }
    }

    protected KubernetesExecutor createExecutor() {
        return new KubernetesExecutorImpl(
                namespace,
                maxParallelPods,
                kubeConfigPath,
                project.getBasedir()
        );
    }

    protected ResultAggregator createAggregator() {
        ReportGenerator reportGenerator = new CompleteEnhancedReportGenerator(project.getBasedir());
        return new PitestResultAggregator(reportGenerator, project.getBasedir());
    }

    private PitestConfiguration buildConfig() {
        return PitestConfiguration.builder()
                .targetClasses(targetClasses)
                .targetTests(targetTests)
                .outputDirectory(outputDirectory.getAbsolutePath())
                .build();
    }

    private ExecutionConfig buildExecutionConfig() {
        return ExecutionConfig.builder()
                .timeout(timeoutInSeconds)
                .memoryLimit(podMemoryLimit)
                .cpuLimit(podCpuLimit)
                .pitestVersion(pitestVersion)
                .imagePullPolicy(imagePullPolicy)
                .baseImage(baseImage)
                .build();
    }
}