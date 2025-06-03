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
            // 1. 创建配置映射
            createConfigMap(partition, podName);

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

            // 删除所有ConfigMap
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
        data.put("run-pitest.sh", createRunScript(partition));

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

    private String createRunScript(TestPartition partition) {
        StringBuilder script = new StringBuilder();
        script.append("#!/bin/bash\n");
        script.append("set -e\n");
        script.append("\n");
        script.append("echo \"Starting PIT mutation testing for partition: ").append(partition.getId()).append("\"\n");
        script.append("\n");

        // 添加源码复制逻辑
        script.append("# Prepare working directory\n");
        script.append("mkdir -p /tmp/project\n");
        script.append("cd /tmp/project\n");
        script.append("\n");

        script.append("# Copy source code if available\n");
        script.append("if [ -d \"/tmp/project-src\" ] && [ \"$(ls -A /tmp/project-src 2>/dev/null)\" ]; then\n");
        script.append("  echo \"Source code directory found, copying to work directory\"\n");
        script.append("  cp -r /tmp/project-src/* /tmp/project/ 2>/dev/null || {\n");
        script.append("    echo \"Failed to copy with cp -r, trying find method\"\n");
        script.append("    find /tmp/project-src -type f -exec cp {} /tmp/project/ \\; 2>/dev/null || true\n");
        script.append("  }\n");
        script.append("  echo \"Source copy completed\"\n");
        script.append("  SRC_OPT=\"-DincludeSource=true\"\n");
        script.append("else\n");
        script.append("  echo \"No source code directory found or empty\"\n");
        script.append("  SRC_OPT=\"\"\n");
        script.append("  # Create minimal pom.xml if none exists\n");
        script.append("  if [ ! -f \"/tmp/project/pom.xml\" ]; then\n");
        script.append("    echo \"Creating minimal pom.xml\"\n");
        script.append("    cat > /tmp/project/pom.xml << 'EOF'\n");
        script.append(createMinimalPomContent(partition));
        script.append("EOF\n");
        script.append("  fi\n");
        script.append("fi\n");
        script.append("\n");

        // 添加调试信息
        script.append("# Debug information\n");
        script.append("echo \"Current directory: $(pwd)\"\n");
        script.append("echo \"Directory contents:\"\n");
        script.append("ls -la /tmp/project/ || true\n");
        script.append("echo \"POM file check:\"\n");
        script.append("if [ -f \"/tmp/project/pom.xml\" ]; then\n");
        script.append("  echo \"pom.xml found\"\n");
        script.append("else\n");
        script.append("  echo \"ERROR: pom.xml not found!\"\n");
        script.append("  exit 1\n");
        script.append("fi\n");
        script.append("\n");

        script.append("# Create target classes string\n");
        script.append("TARGET_CLASSES=$(cat ").append("/tmp/pitest-config").append("/targetClasses.txt | tr '\\n' ',' | sed 's/,$//')\n");
        script.append("\n");
        script.append("# Create target tests string\n");
        script.append("TARGET_TESTS=$(cat ").append("/tmp/pitest-config").append("/targetTests.txt | tr '\\n' ',' | sed 's/,$//')\n");
        script.append("\n");

        // 运行PIT测试
        script.append("# Run PIT testing\n");
        script.append("mvn org.pitest:pitest-maven:mutationCoverage \\\n");
        script.append("  -DtargetClasses=\"${TARGET_CLASSES}\" \\\n");
        script.append("  -DtargetTests=\"${TARGET_TESTS}\" \\\n");
        script.append("  -DoutputFormats=XML,HTML \\\n");
        script.append("  -DreportDir=").append("/tmp/pitest-results").append(" \\\n");
        script.append("  -DtimestampedReports=false \\\n");
        script.append("  ${SRC_OPT} \\\n");
        script.append("  -DexcludedClasses=\"\" \\\n");
        script.append("  -DthreadCount=4\n");
        script.append("\n");
        script.append("echo \"PIT testing completed\"\n");

        return script.toString();
    }

    // 新增方法：创建最小POM内容
    private String createMinimalPomContent(TestPartition partition) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0\n" +
                "                             http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                "    <modelVersion>4.0.0</modelVersion>\n" +
                "    <groupId>com.distributed.pitest</groupId>\n" +
                "    <artifactId>mutation-testing-" + partition.getId() + "</artifactId>\n" +
                "    <version>1.0.0</version>\n" +
                "    <packaging>jar</packaging>\n" +
                "    <properties>\n" +
                "        <maven.compiler.source>8</maven.compiler.source>\n" +
                "        <maven.compiler.target>8</maven.compiler.target>\n" +
                "        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n" +
                "    </properties>\n" +
                "    <dependencies>\n" +
                "        <dependency>\n" +
                "            <groupId>junit</groupId>\n" +
                "            <artifactId>junit</artifactId>\n" +
                "            <version>4.13.2</version>\n" +
                "            <scope>test</scope>\n" +
                "        </dependency>\n" +
                "    </dependencies>\n" +
                "    <build>\n" +
                "        <plugins>\n" +
                "            <plugin>\n" +
                "                <groupId>org.apache.maven.plugins</groupId>\n" +
                "                <artifactId>maven-compiler-plugin</artifactId>\n" +
                "                <version>3.8.1</version>\n" +
                "                <configuration>\n" +
                "                    <source>8</source>\n" +
                "                    <target>8</target>\n" +
                "                </configuration>\n" +
                "            </plugin>\n" +
                "            <plugin>\n" +
                "                <groupId>org.pitest</groupId>\n" +
                "                <artifactId>pitest-maven</artifactId>\n" +
                "                <version>1.9.0</version>\n" +
                "            </plugin>\n" +
                "        </plugins>\n" +
                "    </build>\n" +
                "</project>\n";
    }

    private Pod createPod(TestPartition partition, String podName, ExecutionConfig config) {
        String configMapName = podName + "-config";
        logger.info("Creating Pod {} for partition {} using {}image: {}",
                podName, partition.getId(),
                config.isUseLocalImage() ? "local " : "",
                config.getBaseImage());

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

        // 创建标准卷挂载
        List<io.fabric8.kubernetes.api.model.VolumeMount> volumeMounts = new ArrayList<>();
        volumeMounts.add(new io.fabric8.kubernetes.api.model.VolumeMountBuilder()
                .withName(CONFIG_VOLUME_NAME)
                .withMountPath(CONFIG_MOUNT_PATH)
                .build());
        volumeMounts.add(new io.fabric8.kubernetes.api.model.VolumeMountBuilder()
                .withName(RESULTS_VOLUME_NAME)
                .withMountPath(RESULTS_MOUNT_PATH)
                .build());

        // 如果需要源代码，添加源代码卷挂载
        if (projectBaseDir != null && projectBaseDir.exists()) {
            volumeMounts.add(new io.fabric8.kubernetes.api.model.VolumeMountBuilder()
                    .withName(SRC_VOLUME_NAME)
                    .withMountPath(SRC_MOUNT_PATH)
                    .withReadOnly(true)
                    .build());
        }

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
                .withImagePullPolicy(config.getImagePullPolicy()) // 使用本地镜像时应为"Never"
                //.withCommand("sh", "-c", "chmod +x " + CONFIG_MOUNT_PATH + "/run-pitest.sh && " + CONFIG_MOUNT_PATH + "/run-pitest.sh")
                .withCommand("sh", CONFIG_MOUNT_PATH + "/run-pitest.sh")
                .withResources(new io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder()
                        .withLimits(limits)
                        .withRequests(requests)
                        .build())
                .withVolumeMounts(volumeMounts)
                .build();

        // 创建卷列表
        List<io.fabric8.kubernetes.api.model.Volume> volumes = new ArrayList<>();

        // 添加配置卷
        volumes.add(new io.fabric8.kubernetes.api.model.VolumeBuilder()
                .withName(CONFIG_VOLUME_NAME)
                .withConfigMap(new io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder()
                        .withName(configMapName)
                        .build())
                .build());

        // 添加结果卷
        volumes.add(new io.fabric8.kubernetes.api.model.VolumeBuilder()
                .withName(RESULTS_VOLUME_NAME)
                .withEmptyDir(new io.fabric8.kubernetes.api.model.EmptyDirVolumeSourceBuilder().build())
                .build());

        // 如果需要源代码，添加源代码卷
        if (projectBaseDir != null && projectBaseDir.exists()) {
            volumes.add(new io.fabric8.kubernetes.api.model.VolumeBuilder()
                    .withName(SRC_VOLUME_NAME)
                    .withHostPath(new io.fabric8.kubernetes.api.model.HostPathVolumeSourceBuilder()
                            .withPath(projectBaseDir.getAbsolutePath())
                            .build())
                    .build());
        }

        // 设置Pod规范
        builder.withNewSpec()
                .withContainers(container)
                .withVolumes(volumes)
                .withRestartPolicy("Never")
                .endSpec();

        // 创建Pod
        Pod createdPod = kubernetesClient.pods().inNamespace(namespace).create(builder.build());
        logger.info("Pod {} created successfully", podName);

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

        try {
            logger.info("Deleting pod {}", podName);
            kubernetesClient.pods().inNamespace(namespace).withName(podName).delete();

            logger.info("Deleting configmap {}", configMapName);
            kubernetesClient.configMaps().inNamespace(namespace).withName(configMapName).delete();

        } catch (KubernetesClientException e) {
            logger.warn("Error deleting resources for pod {}", podName, e);
        }
    }
}