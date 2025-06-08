package com.distributed.pitest.maven;

import com.distributed.pitest.service.ImageBuildService;
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
 * Maven plugin for distributed PIT mutation testing using Kubernetes with Docker image building
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

    /**
     * 是否构建Docker镜像
     */
    @Parameter(property = "buildDockerImage", defaultValue = "false")
    private boolean buildDockerImage;

    /**
     * Docker镜像仓库地址
     */
    @Parameter(property = "dockerRegistry", defaultValue = "localhost:5000")
    private String dockerRegistry;

    /**
     * Docker镜像名称
     */
    @Parameter(property = "dockerImageName", defaultValue = "distributed-pitest")
    private String dockerImageName;

    /**
     * Docker镜像标签
     */
    @Parameter(property = "dockerImageTag", defaultValue = "latest")
    private String dockerImageTag;

    /**
     * 是否推送镜像到仓库
     */
    @Parameter(property = "pushDockerImage", defaultValue = "false")
    private boolean pushDockerImage;

    /**
     * Docker构建超时时间（分钟）
     */
    @Parameter(property = "dockerBuildTimeoutMinutes", defaultValue = "30")
    private int dockerBuildTimeoutMinutes;

    /**
     * 强制重新构建镜像，忽略缓存
     */
    @Parameter(property = "forceImageRebuild", defaultValue = "false")
    private boolean forceImageRebuild;

    /**
     * 镜像构建前的预检查
     */
    @Parameter(property = "preBuildImageCheck", defaultValue = "true")
    private boolean preBuildImageCheck;

    private final ModuleResultSerializer resultSerializer = new ModuleResultSerializer();
    private ImageBuildService imageBuildService;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip || skipTests) {
            logger.info("Skipping distributed PIT execution as per configuration");
            return;
        }

        logger.info("Starting distributed PIT execution for module: {} with strategy: {}",
                project.getArtifactId(), partitionStrategy);

        long startTime = System.currentTimeMillis();
        String builtImageName = null;

        try {
            // 1. 初始化镜像构建服务
            initializeImageBuildService();

            // 2. 构建Docker镜像（如果需要）
            if (buildDockerImage) {
                builtImageName = buildDockerImageWithPreChecks();
            }

            // 3. 初始化组件
            ProjectPartitioner partitioner = createPartitioner();
            KubernetesExecutor executor = createExecutor();
            ResultAggregator aggregator = createAggregator();

            // 4. 分区项目
            List<TestPartition> partitions = partitioner.partitionProject(project, buildConfig());
            logger.info("Project partitioned into {} parts", partitions.size());

            if (partitions.isEmpty()) {
                logger.warn("No test partitions found for module: {}", project.getArtifactId());
                return;
            }

            // 5. 验证执行环境
            validateExecutionEnvironment(executor, builtImageName);

            // 6. 执行测试（并行）
            ExecutorService executorService = Executors.newFixedThreadPool(
                    Math.min(partitions.size(), maxParallelPods));
            List<Future<ExecutionResult>> futures = new ArrayList<>();

            for (TestPartition partition : partitions) {
                String finalBuiltImageName = builtImageName;
                futures.add(executorService.submit(() ->
                        executor.executeTests(partition, buildExecutionConfig(finalBuiltImageName))));
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

            // 7. 聚合结果
            AggregatedResult aggregatedResult = aggregator.aggregateResults(results);
            logger.info("Results aggregated. Total mutations: {}, Killed: {}, Score: {}%",
                    aggregatedResult.getTotalMutations(),
                    aggregatedResult.getKilledMutations(),
                    aggregatedResult.getMutationScore());

            // 8. 生成报告
            aggregator.generateReport(aggregatedResult, outputDirectory);
            logger.info("PIT report generated at: {}", outputDirectory.getAbsolutePath());

            // 9. 保存模块结果（用于聚合）
            if (saveModuleResult) {
                saveModuleExecutionResult(aggregatedResult, startTime, builtImageName);
            }

            // 10. 清理资源
            executor.cleanupResources();
            logger.info("Kubernetes resources cleaned up");

            // 11. 检查执行状态
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
     * 初始化镜像构建服务
     */
    private void initializeImageBuildService() {
        logger.info("Initializing image build service");

        this.imageBuildService = new ImageBuildService(
                dockerRegistry,
                pushDockerImage,
                dockerBuildTimeoutMinutes
        );

        // 如果强制重建，清理缓存
        if (forceImageRebuild) {
            imageBuildService.clearCache();
            logger.info("Forced image rebuild enabled, cache cleared");
        }
    }

    /**
     * 带预检查的Docker镜像构建
     */
    private String buildDockerImageWithPreChecks() throws MojoExecutionException {
        logger.info("Building Docker image for distributed PITest execution");

        try {
            // 1. 预检查
            if (preBuildImageCheck) {
                performPreBuildChecks();
            }

            // 2. 构建镜像
            String imageTag = resolveImageTag();
            String builtImageName = imageBuildService.buildImageForProject(
                    project, dockerImageName, imageTag, outputDirectory);

            // 3. 后检查
            performPostBuildChecks(builtImageName);

            logger.info("Docker image built and verified successfully: {}", builtImageName);
            return builtImageName;

        } catch (ImageBuildService.ImageBuildException e) {
            logger.error("Failed to build Docker image", e);
            throw new MojoExecutionException("Docker image build failed", e);
        }
    }

    /**
     * 构建前检查
     */
    private void performPreBuildChecks() throws MojoExecutionException {
        logger.info("Performing pre-build checks");

        // 检查Docker是否可用
        if (!isDockerAvailable()) {
            throw new MojoExecutionException("Docker is not available or not running");
        }

        // 检查项目是否有必要的文件
        if (!hasNecessaryProjectFiles()) {
            throw new MojoExecutionException("Project is missing necessary files for Docker build");
        }

        // 检查磁盘空间
        if (!hasSufficientDiskSpace()) {
            logger.warn("Low disk space detected, image build may fail");
        }

        logger.info("Pre-build checks completed successfully");
    }

    /**
     * 构建后检查
     */
    private void performPostBuildChecks(String imageName) throws MojoExecutionException {
        logger.info("Performing post-build checks for image: {}", imageName);

        // 验证镜像是否成功构建
        if (!isImageAvailable(imageName)) {
            throw new MojoExecutionException("Built image is not available: " + imageName);
        }

        // 测试镜像基本功能
        if (!testImageBasicFunctionality(imageName)) {
            throw new MojoExecutionException("Built image failed basic functionality test: " + imageName);
        }

        logger.info("Post-build checks completed successfully");
    }

    /**
     * 解析镜像标签
     */
    private String resolveImageTag() {
        String tag = dockerImageTag;

        // 如果标签包含变量，进行替换
        if (tag.contains("${project.version}")) {
            tag = tag.replace("${project.version}", project.getVersion());
        }

        if (tag.contains("${build.number}")) {
            String buildNumber = System.getProperty("build.number", System.getenv("BUILD_NUMBER"));
            if (buildNumber != null) {
                tag = tag.replace("${build.number}", buildNumber);
            }
        }

        // 如果是SNAPSHOT版本，添加时间戳
        if (project.getVersion().endsWith("-SNAPSHOT")) {
            //tag = tag + "-" + System.currentTimeMillis();
        }

        return tag;
    }

    /**
     * 验证执行环境
     */
    private void validateExecutionEnvironment(KubernetesExecutor executor, String imageName)
            throws MojoExecutionException {
        logger.info("Validating execution environment");

        try {
            // 检查Kubernetes连接
            List<io.fabric8.kubernetes.api.model.Pod> pods = executor.getActivePods();
            logger.info("Kubernetes connection verified, found {} active pods", pods.size());

            // 如果使用了自定义镜像，验证镜像可访问性
            if (imageName != null) {
                logger.info("Custom image will be used: {}", imageName);
                // 这里可以添加镜像可访问性检查
            }

            // 验证镜像可访问性
            if (imageName != null && !pushDockerImage) {
                logger.warn("Warning: Using built image without pushing to registry. " +
                        "This may cause Pod creation failures in multi-node clusters.");
                logger.warn("Consider setting pushDockerImage=true or ensuring the image " +
                        "is available on all cluster nodes.");
            }

        } catch (Exception e) {
            throw new MojoExecutionException("Execution environment validation failed", e);
        }
    }

    /**
     * 检查Docker是否可用
     */
    private boolean isDockerAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            logger.debug("Docker availability check failed", e);
            return false;
        }
    }

    /**
     * 检查项目是否有必要的文件
     */
    private boolean hasNecessaryProjectFiles() {
        // 检查pom.xml存在
        if (project.getFile() == null || !project.getFile().exists()) {
            return false;
        }

        // 检查源码目录存在
        File srcDir = new File(project.getBasedir(), "src");
        return srcDir.exists() && srcDir.isDirectory();
    }

    /**
     * 检查磁盘空间是否足够
     */
    private boolean hasSufficientDiskSpace() {
        try {
            long freeSpace = outputDirectory.getFreeSpace();
            long requiredSpace = 2L * 1024 * 1024 * 1024; // 2GB
            return freeSpace > requiredSpace;
        } catch (Exception e) {
            logger.debug("Disk space check failed", e);
            return true; // 如果检查失败，假设空间足够
        }
    }

    /**
     * 检查镜像是否可用
     */
    private boolean isImageAvailable(String imageName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "inspect", imageName);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            logger.debug("Image availability check failed for: " + imageName, e);
            return false;
        }
    }

    /**
     * 测试镜像基本功能
     */
    private boolean testImageBasicFunctionality(String imageName) {
        try {
            // 测试Java版本
            ProcessBuilder pb = new ProcessBuilder("docker", "run", "--rm", imageName, "java", "-version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return false;
            }

            // 测试Maven版本
            pb = new ProcessBuilder("docker", "run", "--rm", imageName, "mvn", "-version");
            process = pb.start();
            exitCode = process.waitFor();
            return exitCode == 0;

        } catch (Exception e) {
            logger.debug("Image functionality test failed for: " + imageName, e);
            return false;
        }
    }

    /**
     * 保存模块执行结果（扩展版本，包含镜像信息）
     */
    private void saveModuleExecutionResult(AggregatedResult aggregatedResult, long startTime, String imageName) {
        try {
            long executionTime = System.currentTimeMillis() - startTime;

            ModuleExecutionResult.Builder builder = ModuleExecutionResult.builder()
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
                    .addMetadata("k8s.maxParallelPods", maxParallelPods);

            // 添加镜像相关元数据
            if (buildDockerImage) {
                builder.addMetadata("docker.imageBuilt", true)
                        .addMetadata("docker.registry", dockerRegistry)
                        .addMetadata("docker.imageName", dockerImageName)
                        .addMetadata("docker.imageTag", dockerImageTag);

                if (imageName != null) {
                    builder.addMetadata("docker.builtImageName", imageName);
                }
            }

            ModuleExecutionResult moduleResult = builder.build();

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
        return buildExecutionConfig(null);
    }

    private ExecutionConfig buildExecutionConfig(String customImageName) {
        String effectiveBaseImage = baseImage;

        // 如果构建了镜像并且设置为使用构建的镜像
        if (customImageName != null) {
            effectiveBaseImage = customImageName;
            logger.info("Using built Docker image: {}", effectiveBaseImage);
        }

        return ExecutionConfig.builder()
                .timeout(timeoutInSeconds)
                .memoryLimit(podMemoryLimit)
                .cpuLimit(podCpuLimit)
                .pitestVersion(pitestVersion)
                .imagePullPolicy(imagePullPolicy)
                .baseImage(effectiveBaseImage)
                .build();
    }
}