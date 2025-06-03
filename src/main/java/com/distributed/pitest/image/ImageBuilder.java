package com.distributed.pitest.image;

import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Docker镜像构建器，用于构建分布式PITest执行镜像
 */
public class ImageBuilder {
    private static final Logger logger = LoggerFactory.getLogger(ImageBuilder.class);

    private static final String DOCKERFILE_RESOURCE = "/docker/Dockerfile";
    private static final String DOCKER_COMPOSE_RESOURCE = "/docker/docker-compose.yml";
    private static final String RUN_SCRIPT_RESOURCE = "/docker/run-pitest.sh";
    private static final String MAVEN_SETTINGS_RESOURCE = "/docker/maven-settings.xml";
    private static final String BUILD_SCRIPT_RESOURCE = "/docker/build-image.sh";

    private final String dockerRegistry;
    private final String imageName;
    private final String imageTag;
    private final boolean pushImage;
    private final int buildTimeoutMinutes;
    private final Map<String, String> buildArgs;

    private ImageBuilder(Builder builder) {
        this.dockerRegistry = builder.dockerRegistry;
        this.imageName = builder.imageName;
        this.imageTag = builder.imageTag;
        this.pushImage = builder.pushImage;
        this.buildTimeoutMinutes = builder.buildTimeoutMinutes;
        this.buildArgs = new HashMap<>(builder.buildArgs);
    }

    /**
     * 构建Docker镜像
     *
     * @param project Maven项目
     * @param outputDirectory 构建输出目录
     * @return 构建的镜像完整名称
     * @throws ImageBuildException 如果构建失败
     */
    public String buildImage(MavenProject project, File outputDirectory) throws ImageBuildException {
        logger.info("开始构建分布式PITest Docker镜像");
        logger.info("镜像: {}/{}:{}", dockerRegistry, imageName, imageTag);

        try {
            // 1. 准备构建目录
            File buildDir = prepareBuildDirectory(outputDirectory);

            // 2. 复制Docker资源文件
            copyDockerResources(buildDir);

            // 3. 复制项目源码（如果需要）
            copyProjectSources(project, buildDir);

            // 4. 执行Docker构建
            String fullImageName = executeDockerBuild(buildDir);

            logger.info("Docker镜像构建成功: {}", fullImageName);
            return fullImageName;

        } catch (Exception e) {
            logger.error("Docker镜像构建失败", e);
            throw new ImageBuildException("Failed to build Docker image", e);
        }
    }

    /**
     * 准备构建目录
     */
    private File prepareBuildDirectory(File outputDirectory) throws IOException {
        File buildDir = new File(outputDirectory, "docker-build");

        // 如果目录存在，先清理
        if (buildDir.exists()) {
            deleteDirectory(buildDir);
        }

        buildDir.mkdirs();
        logger.info("准备构建目录: {}", buildDir.getAbsolutePath());

        return buildDir;
    }

    /**
     * 复制Docker资源文件
     */
    private void copyDockerResources(File buildDir) throws IOException {
        logger.info("复制Docker资源文件到构建目录");

        // 复制Dockerfile
        copyResourceToFile(DOCKERFILE_RESOURCE, new File(buildDir, "Dockerfile"));

        // 复制docker-compose.yml
        copyResourceToFile(DOCKER_COMPOSE_RESOURCE, new File(buildDir, "docker-compose.yml"));

        // 复制运行脚本
        copyResourceToFile(RUN_SCRIPT_RESOURCE, new File(buildDir, "run-pitest.sh"));

        // 复制Maven设置文件
        copyResourceToFile(MAVEN_SETTINGS_RESOURCE, new File(buildDir, "maven-settings.xml"));

        // 复制构建脚本
        File buildScript = new File(buildDir, "build-image.sh");
        copyResourceToFile(BUILD_SCRIPT_RESOURCE, buildScript);
        buildScript.setExecutable(true);

        logger.info("Docker资源文件复制完成");
    }

    /**
     * 从资源文件复制到目标文件
     */
    private void copyResourceToFile(String resourcePath, File targetFile) throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }

            Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.debug("复制资源文件: {} -> {}", resourcePath, targetFile.getName());
        }
    }

    /**
     * 复制项目源码（可选）
     */
    private void copyProjectSources(MavenProject project, File buildDir) throws IOException {
        // 检查是否需要包含源码
        if (!shouldIncludeProjectSources()) {
            logger.info("跳过项目源码复制");
            return;
        }

        logger.info("复制项目源码到Docker构建上下文");

        File sourceDir = new File(buildDir, "project-src");
        sourceDir.mkdirs();

        // 复制pom.xml
        File pomFile = project.getFile();
        if (pomFile != null && pomFile.exists()) {
            Files.copy(pomFile.toPath(),
                    new File(sourceDir, "pom.xml").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }

        // 复制源码目录
        copyDirectoryIfExists(new File(project.getBasedir(), "src"),
                new File(sourceDir, "src"));

        logger.info("项目源码复制完成");
    }

    /**
     * 执行Docker构建
     */
    private String executeDockerBuild(File buildDir) throws IOException, InterruptedException {
        logger.info("开始执行Docker镜像构建");

        String fullImageName = dockerRegistry + "/" + imageName + ":" + imageTag;

        // 准备环境变量
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("DOCKER_REGISTRY", dockerRegistry);
        env.put("IMAGE_NAME", imageName);
        env.put("IMAGE_TAG", imageTag);
        env.put("PUSH_IMAGE", String.valueOf(pushImage));

        // 添加构建参数
        buildArgs.forEach(env::put);

        // 构建命令
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.directory(buildDir);
        processBuilder.environment().putAll(env);

        // 使用构建脚本
        processBuilder.command("./build-image.sh");

        logger.info("执行构建命令: ./build-image.sh");
        logger.info("工作目录: {}", buildDir.getAbsolutePath());
        logger.info("环境变量: DOCKER_REGISTRY={}, IMAGE_NAME={}, IMAGE_TAG={}",
                dockerRegistry, imageName, imageTag);

        // 执行构建过程
        Process process = processBuilder.start();

        // 处理输出
        StreamHandler outputHandler = new StreamHandler(process.getInputStream(), "BUILD-OUT");
        StreamHandler errorHandler = new StreamHandler(process.getErrorStream(), "BUILD-ERR");

        outputHandler.start();
        errorHandler.start();

        // 等待构建完成
        boolean finished = process.waitFor(buildTimeoutMinutes, TimeUnit.MINUTES);

        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Docker build timeout after " + buildTimeoutMinutes + " minutes");
        }

        int exitCode = process.exitValue();

        // 等待输出处理完成
        outputHandler.join(5000);
        errorHandler.join(5000);

        if (exitCode != 0) {
            throw new IOException("Docker build failed with exit code: " + exitCode);
        }

        logger.info("Docker镜像构建完成: {}", fullImageName);
        return fullImageName;
    }

    /**
     * 检查是否应该包含项目源码
     */
    private boolean shouldIncludeProjectSources() {
        return buildArgs.getOrDefault("INCLUDE_PROJECT_SOURCES", "false").equals("true");
    }

    /**
     * 复制目录（如果存在）
     */
    private void copyDirectoryIfExists(File sourceDir, File targetDir) throws IOException {
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            return;
        }

        copyDirectory(sourceDir, targetDir);
    }

    /**
     * 递归复制目录
     */
    private void copyDirectory(File sourceDir, File targetDir) throws IOException {
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        File[] files = sourceDir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            File targetFile = new File(targetDir, file.getName());

            if (file.isDirectory()) {
                copyDirectory(file, targetFile);
            } else {
                Files.copy(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * 删除目录及其内容
     */
    private void deleteDirectory(File directory) {
        if (!directory.exists()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }

        directory.delete();
    }

    /**
     * 流处理器，用于处理进程输出
     */
    private static class StreamHandler extends Thread {
        private final InputStream inputStream;
        private final String prefix;

        public StreamHandler(InputStream inputStream, String prefix) {
            this.inputStream = inputStream;
            this.prefix = prefix;
            setDaemon(true);
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("[{}] {}", prefix, line);
                }
            } catch (IOException e) {
                logger.warn("Error reading stream", e);
            }
        }
    }

    /**
     * 构建器类
     */
    public static class Builder {
        private String dockerRegistry = "localhost:5000";
        private String imageName = "distributed-pitest";
        private String imageTag = "latest";
        private boolean pushImage = false;
        private int buildTimeoutMinutes = 30;
        private Map<String, String> buildArgs = new HashMap<>();

        public Builder dockerRegistry(String dockerRegistry) {
            this.dockerRegistry = dockerRegistry;
            return this;
        }

        public Builder imageName(String imageName) {
            this.imageName = imageName;
            return this;
        }

        public Builder imageTag(String imageTag) {
            this.imageTag = imageTag;
            return this;
        }

        public Builder pushImage(boolean pushImage) {
            this.pushImage = pushImage;
            return this;
        }

        public Builder buildTimeoutMinutes(int buildTimeoutMinutes) {
            this.buildTimeoutMinutes = buildTimeoutMinutes;
            return this;
        }

        public Builder addBuildArg(String key, String value) {
            this.buildArgs.put(key, value);
            return this;
        }

        public Builder buildArgs(Map<String, String> buildArgs) {
            this.buildArgs = new HashMap<>(buildArgs);
            return this;
        }

        public ImageBuilder build() {
            return new ImageBuilder(this);
        }
    }

    /**
     * 镜像构建异常
     */
    public static class ImageBuildException extends Exception {
        public ImageBuildException(String message) {
            super(message);
        }

        public ImageBuildException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}