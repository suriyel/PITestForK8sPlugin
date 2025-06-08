package com.distributed.pitest.kubernetes;

import com.distributed.pitest.model.ExecutionResult;
import com.distributed.pitest.model.MutationResult;
import com.distributed.pitest.model.TestPartition;
import com.distributed.pitest.util.EnhancedXmlReportParser;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 增强的Kubernetes执行器实现，支持源代码分析和收集
 */
public class KubernetesExecutorImpl implements KubernetesExecutor {
    private static final Logger logger = LoggerFactory.getLogger(KubernetesExecutorImpl.class);
    private static final String POD_LABEL_KEY = "app";
    private static final String POD_LABEL_VALUE = "pitest-executor";
    private static final String RESULTS_VOLUME_NAME = "pitest-results";
    private static final String RESULTS_MOUNT_PATH = "/tmp/pitest-results";
    private static final String CONFIG_VOLUME_NAME = "pitest-config";
    private static final String CONFIG_MOUNT_PATH = "/tmp/pitest-config";
    private static final String SRC_VOLUME_NAME = "project-src";
    private static final String SRC_MOUNT_PATH = "/tmp/project-src";

    private final String namespace;
    private final int maxParallelPods;
    private final KubernetesClient kubernetesClient;
    private final Map<String, Pod> activePods = new ConcurrentHashMap<>();
    private final String executionId;
    private final File projectBaseDir;
    private final EnhancedXmlReportParser xmlParser;

    public KubernetesExecutorImpl(String namespace, int maxParallelPods,
                                  String kubeConfigPath, File projectBaseDir) {
        this.namespace = namespace;
        this.maxParallelPods = maxParallelPods;
        this.executionId = UUID.randomUUID().toString().substring(0, 8);
        this.kubernetesClient = createKubernetesClient(kubeConfigPath);
        this.projectBaseDir = projectBaseDir;
        this.xmlParser = new EnhancedXmlReportParser();

        logger.info("Initialized EnhancedKubernetesExecutor with namespace: {}, maxParallelPods: {}, executionId: {}",
                namespace, maxParallelPods, executionId);
    }

    private KubernetesClient createKubernetesClient(String kubeConfigPath) {
        try {
            if (kubeConfigPath != null && !kubeConfigPath.isEmpty()) {
                logger.info("Using kubeconfig from: {}", kubeConfigPath);
                // 从指定路径加载kubeconfig文件
                File kubeConfigFile = new File(kubeConfigPath);
                if (kubeConfigFile.exists()) {
                    return new DefaultKubernetesClient(Config.fromKubeconfig(
                            new String(Files.readAllBytes(kubeConfigFile.toPath()), StandardCharsets.UTF_8)
                    ));
                } else {
                    logger.warn("Kubeconfig file not found at: {}, using default config", kubeConfigPath);
                }
            }

            logger.info("Using default Kubernetes client configuration");
            return new DefaultKubernetesClient();
        } catch (Exception e) {
            logger.error("Error creating Kubernetes client", e);
            throw new RuntimeException("Failed to create Kubernetes client", e);
        }
    }

    @Override
    public ExecutionResult executeTests(TestPartition partition, ExecutionConfig config) {
        String podName = generatePodName(partition);
        logger.info("Starting execution of partition {} in pod {}", partition.getId(), podName);

        try {
            // 2. 创建并运行Pod
            Pod pod = createPod(partition, podName, config);
            activePods.put(podName, pod);

            // 3. 等待执行完成
            boolean completed = waitForPodCompletion(podName, config.getTimeout());

            // 4. 收集结果
            ExecutionResult result;
            if (completed) {
                result = collectResults(partition, podName);
            } else {
                logger.warn("Pod {} did not complete in time", podName);
                result = ExecutionResult.builder()
                        .partitionId(partition.getId())
                        .successful(false)
                        .errorMessage("Execution timed out")
                        .build();
            }

            // 5. 清理资源
            deleteResources(podName);
            activePods.remove(podName);

            return result;

        } catch (Exception e) {
            logger.error("Error executing tests for partition {}", partition.getId(), e);
            return ExecutionResult.builder()
                    .partitionId(partition.getId())
                    .successful(false)
                    .errorMessage("Execution failed: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public List<Pod> getActivePods() {
        return new ArrayList<>(activePods.values());
    }

    @Override
    public void cleanupResources() {
        logger.info("Cleaning up all resources created by this executor");

        try {
            // 删除所有Pod
            kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withLabel(POD_LABEL_KEY, POD_LABEL_VALUE)
                    .withLabel("executionId", executionId)
                    .delete();

            // 删除所有配置ConfigMap
            kubernetesClient.configMaps()
                    .inNamespace(namespace)
                    .withLabel(POD_LABEL_KEY, POD_LABEL_VALUE)
                    .withLabel("executionId", executionId)
                    .delete();

            logger.info("All resources cleaned up successfully");
        } catch (KubernetesClientException e) {
            logger.error("Error cleaning up resources", e);
        }
    }

    private String generatePodName(TestPartition partition) {
        return "pitest-" + partition.getId() + "-" + executionId;
    }

    private void createConfigMap(TestPartition partition, String podName) {
        String configMapName = podName + "-config";
        logger.info("Creating ConfigMap {} for partition {}", configMapName, partition.getId());

        // 准备配置数据
        Map<String, String> data = new HashMap<>();

        // 添加目标类列表
        data.put("targetClasses.txt", String.join("\n", partition.getTargetClasses()));

        // 添加目标测试列表
        data.put("targetTests.txt", String.join("\n", partition.getTargetTests()));

        // 添加其他属性
        StringBuilder propsBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : partition.getProperties().entrySet()) {
            propsBuilder.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
        }
        data.put("pitest.properties", propsBuilder.toString());

        // 创建运行脚本
        //data.put("run-pitest.sh", createRunScript(partition));

        // 创建ConfigMap
        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withName(configMapName)
                .withNamespace(namespace)
                .addToLabels(POD_LABEL_KEY, POD_LABEL_VALUE)
                .addToLabels("executionId", executionId)
                .addToLabels("partitionId", partition.getId())
                .endMetadata()
                .withData(data)
                .build();

        kubernetesClient.configMaps().inNamespace(namespace).create(configMap);
        logger.info("ConfigMap {} created successfully", configMapName);
    }

    /**
     * 创建源码ConfigMap（新增方法）
     */
    private void createSourceCodeConfigMap(TestPartition partition, String podName) {
        String sourceConfigMapName = podName + "-source";
        logger.info("Creating source ConfigMap {} for partition {}", sourceConfigMapName, partition.getId());

        Map<String, String> sourceData = new HashMap<>();

        try {
            // 收集项目源码文件
            collectSourceFiles(projectBaseDir, sourceData);

            // 如果没有收集到源码文件，创建基本的项目结构信息
            if (sourceData.isEmpty()) {
                logger.warn("No source files found, creating basic project structure");
                sourceData.put("project-info.txt", createProjectInfo(partition));
            } else {
                logger.info("Collected {} source files for ConfigMap", sourceData.size());
            }

            // 创建源码ConfigMap
            ConfigMap sourceConfigMap = new ConfigMapBuilder()
                    .withNewMetadata()
                    .withName(sourceConfigMapName)
                    .withNamespace(namespace)
                    .addToLabels(POD_LABEL_KEY, POD_LABEL_VALUE)
                    .addToLabels("executionId", executionId)
                    .addToLabels("partitionId", partition.getId())
                    .addToLabels("type", "source-code")
                    .endMetadata()
                    .withData(sourceData)
                    .build();

            kubernetesClient.configMaps().inNamespace(namespace).create(sourceConfigMap);
            logger.info("Source ConfigMap {} created successfully with {} files",
                    sourceConfigMapName, sourceData.size());

        } catch (Exception e) {
            logger.error("Error creating source ConfigMap for partition {}", partition.getId(), e);
            // 不抛出异常，因为源码ConfigMap是可选的
        }
    }

    /**
     * 收集项目源码文件（新增方法）
     */
    private void collectSourceFiles(File projectBaseDir, Map<String, String> sourceData) {
        if (projectBaseDir == null || !projectBaseDir.exists()) {
            logger.warn("Project base directory not found: {}", projectBaseDir);
            return;
        }

        try {
            // 收集Java源文件
            collectJavaSourceFiles(new File(projectBaseDir, "src/main/java"), "src/main/java", sourceData);
            collectJavaSourceFiles(new File(projectBaseDir, "src/test/java"), "src/test/java", sourceData);

            // 收集资源文件
            collectResourceFiles(new File(projectBaseDir, "src/main/resources"), "src/main/resources", sourceData);
            collectResourceFiles(new File(projectBaseDir, "src/test/resources"), "src/test/resources", sourceData);

            // 收集pom.xml
            File pomFile = new File(projectBaseDir, "pom.xml");
            if (pomFile.exists()) {
                try {
                    String pomContent = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
                    sourceData.put("pom.xml", pomContent);
                    logger.debug("Added pom.xml to source ConfigMap");
                } catch (IOException e) {
                    logger.warn("Error reading pom.xml: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("Error collecting source files", e);
        }
    }

    /**
     * 收集Java源文件（新增方法）
     */
    private void collectJavaSourceFiles(File sourceDir, String basePath, Map<String, String> sourceData) {
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            return;
        }

        try {
            Files.walk(sourceDir.toPath())
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String relativePath = basePath + "/" + sourceDir.toPath().relativize(path).toString();
                            String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);

                            // ConfigMap的key不能包含某些字符，需要转换
                            String configMapKey = relativePath.replace("/", "_").replace("\\", "_");
                            sourceData.put(configMapKey, content);

                            // 同时保存原始路径信息
                            sourceData.put(configMapKey + ".path", relativePath);

                            logger.debug("Added source file: {} -> {}", relativePath, configMapKey);
                        } catch (IOException e) {
                            logger.warn("Error reading source file: {}", path, e);
                        }
                    });
        } catch (IOException e) {
            logger.warn("Error walking source directory: {}", sourceDir, e);
        }
    }

    /**
     * 收集资源文件（新增方法）
     */
    private void collectResourceFiles(File resourceDir, String basePath, Map<String, String> sourceData) {
        if (!resourceDir.exists() || !resourceDir.isDirectory()) {
            return;
        }

        try {
            Files.walk(resourceDir.toPath())
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        // 只收集文本类型的资源文件
                        return fileName.endsWith(".xml") || fileName.endsWith(".properties") ||
                                fileName.endsWith(".yml") || fileName.endsWith(".yaml") ||
                                fileName.endsWith(".txt") || fileName.endsWith(".json");
                    })
                    .forEach(path -> {
                        try {
                            String relativePath = basePath + "/" + resourceDir.toPath().relativize(path).toString();
                            String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);

                            String configMapKey = relativePath.replace("/", "_").replace("\\", "_");
                            sourceData.put(configMapKey, content);
                            sourceData.put(configMapKey + ".path", relativePath);

                            logger.debug("Added resource file: {} -> {}", relativePath, configMapKey);
                        } catch (IOException e) {
                            logger.warn("Error reading resource file: {}", path, e);
                        }
                    });
        } catch (IOException e) {
            logger.warn("Error walking resource directory: {}", resourceDir, e);
        }
    }

    /**
     * 创建项目信息（新增方法）
     */
    private String createProjectInfo(TestPartition partition) {
        StringBuilder info = new StringBuilder();
        info.append("# Project Information\n");
        info.append("Generated for partition: ").append(partition.getId()).append("\n");
        info.append("Execution ID: ").append(executionId).append("\n");
        info.append("Timestamp: ").append(LocalDateTime.now()).append("\n");
        info.append("Project base directory: ").append(projectBaseDir != null ? projectBaseDir.getAbsolutePath() : "unknown").append("\n");
        info.append("\n# Target Classes:\n");
        for (String targetClass : partition.getTargetClasses()) {
            info.append("- ").append(targetClass).append("\n");
        }
        info.append("\n# Target Tests:\n");
        for (String targetTest : partition.getTargetTests()) {
            info.append("- ").append(targetTest).append("\n");
        }
        return info.toString();
    }

    private Pod createPod(TestPartition partition, String podName, ExecutionConfig config) {
        String configMapName = podName + "-config";
        String sourceConfigMapName = podName + "-source";

        logger.info("Creating Pod {} for partition {} using image: {}",
                podName, partition.getId(),
                config.getBaseImage());

        // 1. 创建配置ConfigMap
        createConfigMap(partition, podName);

        // 2. 创建源码ConfigMap（新增）
        createSourceCodeConfigMap(partition, podName);

        // 创建Pod构建器
        PodBuilder builder = new PodBuilder();

        // 设置元数据
        builder.withNewMetadata()
                .withName(podName)
                .withNamespace(namespace)
                .addToLabels(POD_LABEL_KEY, POD_LABEL_VALUE)
                .addToLabels("executionId", executionId)
                .addToLabels("partitionId", partition.getId())
                .endMetadata();

        // 创建卷挂载
        List<io.fabric8.kubernetes.api.model.VolumeMount> volumeMounts = new ArrayList<>();

        // 配置卷挂载
        volumeMounts.add(new io.fabric8.kubernetes.api.model.VolumeMountBuilder()
                .withName(CONFIG_VOLUME_NAME)
                .withMountPath(CONFIG_MOUNT_PATH)
                .build());

        // 结果卷挂载
        volumeMounts.add(new io.fabric8.kubernetes.api.model.VolumeMountBuilder()
                .withName(RESULTS_VOLUME_NAME)
                .withMountPath(RESULTS_MOUNT_PATH)
                .build());

        // 源码卷挂载（使用ConfigMap）
        volumeMounts.add(new io.fabric8.kubernetes.api.model.VolumeMountBuilder()
                .withName(SRC_VOLUME_NAME)
                .withMountPath(SRC_MOUNT_PATH)
                .withReadOnly(true)
                .build());

        // 设置资源限制
        Map<String, Quantity> limits = new HashMap<>();
        limits.put("memory", new Quantity(config.getMemoryLimit()));
        limits.put("cpu", new Quantity(config.getCpuLimit()));

        Map<String, Quantity> requests = new HashMap<>();
        requests.put("memory", new Quantity(config.getMemoryLimit()));
        requests.put("cpu", new Quantity(config.getCpuLimit()));

        // 创建容器
        io.fabric8.kubernetes.api.model.Container container = new io.fabric8.kubernetes.api.model.ContainerBuilder()
                .withName("pitest")
                .withImage(config.getBaseImage())
                .withImagePullPolicy(config.getImagePullPolicy())
                //.withCommand("sh", CONFIG_MOUNT_PATH + "/run-pitest.sh")
                .withCommand("bash", "/usr/local/bin/run-pitest.sh")
                .withResources(new io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder()
                        .withLimits(limits)
                        .withRequests(requests)
                        .build())
                .withVolumeMounts(volumeMounts)
                .build();

        // 创建卷列表
        List<io.fabric8.kubernetes.api.model.Volume> volumes = new ArrayList<>();

        // 配置卷（ConfigMap）
        volumes.add(new io.fabric8.kubernetes.api.model.VolumeBuilder()
                .withName(CONFIG_VOLUME_NAME)
                .withConfigMap(new io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder()
                        .withName(configMapName)
                        .withDefaultMode(0755) // 确保脚本可执行
                        .build())
                .build());

        // 结果卷（EmptyDir - 用于Pod内部文件传输）
        volumes.add(new io.fabric8.kubernetes.api.model.VolumeBuilder()
                .withName(RESULTS_VOLUME_NAME)
                .withEmptyDir(new io.fabric8.kubernetes.api.model.EmptyDirVolumeSourceBuilder().build())
                .build());

        // 源码卷（ConfigMap - 替代HostPath）
        volumes.add(new io.fabric8.kubernetes.api.model.VolumeBuilder()
                .withName(SRC_VOLUME_NAME)
                .withConfigMap(new io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder()
                        .withName(sourceConfigMapName)
                        .withOptional(true) // 源码ConfigMap是可选的
                        .build())
                .build());

        // 设置Pod规范
        builder.withNewSpec()
                .withContainers(container)
                .withVolumes(volumes)
                .withRestartPolicy("Never")
                .endSpec();

        // 创建Pod
        Pod createdPod = kubernetesClient.pods().inNamespace(namespace).create(builder.build());
        logger.info("Pod {} created successfully with ConfigMap-based source mounting", podName);

        return createdPod;
    }

    private boolean waitForPodCompletion(String podName, int timeoutSeconds) {
        logger.info("Waiting for pod {} to complete (timeout: {} seconds)", podName, timeoutSeconds);

        PodResource<Pod> podResource = kubernetesClient.pods()
                .inNamespace(namespace)
                .withName(podName);

        long startTime = System.currentTimeMillis();
        long endTime = startTime + TimeUnit.SECONDS.toMillis(timeoutSeconds);

        // 添加进度监控
        long lastLogTime = startTime;
        long progressInterval = 60000; // 每分钟输出一次进度

        while (System.currentTimeMillis() < endTime) {
            Pod pod = podResource.get();
            if (pod == null) {
                logger.warn("Pod {} not found", podName);
                return false;
            }

            String phase = pod.getStatus().getPhase();

            // 定期输出进度和Pod信息
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastLogTime > progressInterval) {
                long elapsed = (currentTime - startTime) / 1000;
                long remaining = (endTime - currentTime) / 1000;
                logger.info("Pod {} status: phase={}, elapsed={}s, remaining={}s",
                        podName, phase, elapsed, remaining);

                // 输出Pod资源使用情况
                logPodResourceUsage(pod);
                lastLogTime = currentTime;
            }

            if ("Succeeded".equals(phase)) {
                logger.info("Pod {} completed successfully", podName);
                return true;
            } else if ("Failed".equals(phase)) {
                logger.warn("Pod {} failed", podName);
                // 输出失败原因
                logPodFailureReason(pod);
                return false;
            }

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while waiting for pod completion", e);
                return false;
            }
        }

        logger.warn("Timeout waiting for pod {} to complete after {} seconds", podName, timeoutSeconds);
        // 超时时输出Pod状态用于诊断
        Pod pod = podResource.get();
        if (pod != null) {
            logPodTimeoutDiagnostic(pod);
        }
        return false;
    }

    private void logPodResourceUsage(Pod pod) {
        try {
            // 这里可以添加资源使用情况的查询
            logger.info("Pod {} containers status: {}",
                    pod.getMetadata().getName(),
                    pod.getStatus().getContainerStatuses());
        } catch (Exception e) {
            logger.debug("Could not get pod resource usage", e);
        }
    }

    private void logPodFailureReason(Pod pod) {
        try {
            if (pod.getStatus().getContainerStatuses() != null) {
                pod.getStatus().getContainerStatuses().forEach(status -> {
                    if (status.getState() != null && status.getState().getTerminated() != null) {
                        logger.error("Container {} terminated: reason={}, message={}",
                                status.getName(),
                                status.getState().getTerminated().getReason(),
                                status.getState().getTerminated().getMessage());
                    }
                });
            }
        } catch (Exception e) {
            logger.debug("Could not get pod failure reason", e);
        }
    }

    private void logPodTimeoutDiagnostic(Pod pod) {
        logger.warn("Pod timeout diagnostic for {}: phase={}, conditions={}",
                pod.getMetadata().getName(),
                pod.getStatus().getPhase(),
                pod.getStatus().getConditions());
    }

    private ExecutionResult collectResults(TestPartition partition, String podName) {
        logger.info("Collecting results from pod {}", podName);

        try {
            // 创建临时目录存储结果
            Path tempDir = Files.createTempDirectory("pitest-" + partition.getId());

            // 获取Pod中的结果文件列表
            List<String> resultFiles = listResultFiles(podName);

            // 下载每个结果文件
            for (String fileName : resultFiles) {
                downloadFileFromPod(podName, RESULTS_MOUNT_PATH + "/" + fileName, tempDir.resolve(fileName));
            }

            // 解析突变测试结果，包括源代码信息
            List<MutationResult> mutations = new ArrayList<>();
            File mutationsXmlFile = tempDir.resolve("mutations.xml").toFile();

            if (mutationsXmlFile.exists()) {
                mutations = xmlParser.parseMutationXml(mutationsXmlFile, projectBaseDir);
                logger.info("Parsed {} mutations with source code information", mutations.size());
            } else {
                logger.warn("mutations.xml not found in pod {}", podName);
            }

            logger.info("Collected results from pod {}", podName);

            return ExecutionResult.builder()
                    .partitionId(partition.getId())
                    .resultDirectory(tempDir.toFile())
                    .successful(true)
                    .mutations(mutations)
                    .build();

        } catch (Exception e) {
            logger.error("Error collecting results from pod {}", podName, e);
            return ExecutionResult.builder()
                    .partitionId(partition.getId())
                    .successful(false)
                    .errorMessage("Failed to collect results: " + e.getMessage())
                    .build();
        }
    }

    private List<String> listResultFiles(String podName) throws IOException, InterruptedException {
        logger.debug("Listing result files in pod {}", podName);

        String[] command = {
                "kubectl", "exec", "-n", namespace, podName, "--", "ls", "-la", RESULTS_MOUNT_PATH
        };

        Process process = new ProcessBuilder(command).start();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

        List<String> fileNames = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            // 简单解析ls输出
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 8) {
                String fileName = parts[parts.length - 1];
                if (!fileName.equals(".") && !fileName.equals("..")) {
                    fileNames.add(fileName);
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            logger.warn("Error listing files in pod {}, exit code: {}", podName, exitCode);
            return Collections.emptyList();
        }

        logger.debug("Found {} result files in pod {}", fileNames.size(), podName);
        return fileNames;
    }

    private void downloadFileFromPod(String podName, String sourcePath, Path targetPath) throws IOException, InterruptedException {
        logger.debug("Downloading file from pod {} path {} to {}", podName, sourcePath, targetPath);

        String[] command = {
                "kubectl", "cp", namespace + "/" + podName + ":" + sourcePath, targetPath.toString()
        };

        Process process = new ProcessBuilder(command).start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            logger.warn("Error downloading file from pod {}, exit code: {}", podName, exitCode);
            throw new IOException("Failed to download file from pod: " + podName);
        }

        logger.debug("File downloaded successfully");
    }

    private void deleteResources(String podName) {
        String configMapName = podName + "-config";
        String sourceConfigMapName = podName + "-source";

        try {
            logger.info("Deleting pod {}", podName);
            kubernetesClient.pods().inNamespace(namespace).withName(podName).delete();

            logger.info("Deleting configmap {}", configMapName);
            kubernetesClient.configMaps().inNamespace(namespace).withName(configMapName).delete();

            logger.info("Deleting source configmap {}", sourceConfigMapName);
            kubernetesClient.configMaps().inNamespace(namespace).withName(sourceConfigMapName).delete();

        } catch (KubernetesClientException e) {
            logger.warn("Error deleting resources for pod {}", podName, e);
        }
    }
}