package com.distributed.pitest.service;

import com.distributed.pitest.image.ImageBuilder;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 镜像构建服务，负责管理Docker镜像的构建生命周期
 */
public class ImageBuildService {
    private static final Logger logger = LoggerFactory.getLogger(ImageBuildService.class);

    // 缓存已构建的镜像，避免重复构建
    private final Map<String, String> builtImageCache = new ConcurrentHashMap<>();

    private final String dockerRegistry;
    private final boolean pushImages;
    private final int buildTimeoutMinutes;

    public ImageBuildService(String dockerRegistry, boolean pushImages, int buildTimeoutMinutes) {
        this.dockerRegistry = dockerRegistry;
        this.pushImages = pushImages;
        this.buildTimeoutMinutes = buildTimeoutMinutes;
    }

    /**
     * 为项目构建Docker镜像
     *
     * @param project Maven项目
     * @param imageName 镜像名称
     * @param imageTag 镜像标签
     * @param outputDirectory 输出目录
     * @return 构建的镜像完整名称
     */
    public String buildImageForProject(MavenProject project, String imageName, String imageTag,
                                       File outputDirectory) throws ImageBuildException {

        String cacheKey = generateCacheKey(project, imageName, imageTag);

        // 检查缓存
        if (builtImageCache.containsKey(cacheKey)) {
            String cachedImage = builtImageCache.get(cacheKey);
            logger.info("Using cached Docker image: {}", cachedImage);
            return cachedImage;
        }

        logger.info("Building Docker image for project: {} ({}:{})",
                project.getArtifactId(), imageName, imageTag);

        try {
            // 1. 准备项目特定的构建环境
            BuildContext buildContext = prepareBuildContext(project, outputDirectory);

            // 2. 创建ImageBuilder实例
            ImageBuilder imageBuilder = createImageBuilder(imageName, imageTag, buildContext);

            // 3. 执行镜像构建
            String builtImageName = imageBuilder.buildImage(project, outputDirectory);

            // 4. 缓存构建结果
            builtImageCache.put(cacheKey, builtImageName);

            logger.info("Docker image built successfully: {}", builtImageName);
            return builtImageName;

        } catch (Exception e) {
            logger.error("Failed to build Docker image for project: {}", project.getArtifactId(), e);
            throw new ImageBuildException("Docker image build failed for project: " + project.getArtifactId(), e);
        }
    }

    /**
     * 准备构建上下文
     */
    private BuildContext prepareBuildContext(MavenProject project, File outputDirectory) throws IOException {
        logger.info("Preparing build context for project: {}", project.getArtifactId());

        BuildContext context = new BuildContext();

        // 1. 分析项目依赖
        context.dependencies = analyzeDependencies(project);

        // 2. 检查测试存在
        context.hasTests = hasTestClasses(project);

        // 3. 检查源码结构
        context.sourceStructure = analyzeSourceStructure(project);

        // 4. 准备项目文件
        context.projectFiles = prepareProjectFiles(project, outputDirectory);

        logger.info("Build context prepared: dependencies={}, hasTests={}, sourceStructure={}",
                context.dependencies.size(), context.hasTests, context.sourceStructure);

        return context;
    }

    /**
     * 分析项目依赖
     */
    private Map<String, String> analyzeDependencies(MavenProject project) {
        Map<String, String> dependencies = new HashMap<>();

        if (project.getDependencies() != null) {
            project.getDependencies().forEach(dep -> {
                String key = dep.getGroupId() + ":" + dep.getArtifactId();
                dependencies.put(key, dep.getVersion());
            });
        }

        return dependencies;
    }

    /**
     * 检查是否有测试类
     */
    private boolean hasTestClasses(MavenProject project) {
        File testClassesDir = new File(project.getBuild().getTestOutputDirectory());
        return testClassesDir.exists() && testClassesDir.isDirectory() &&
                testClassesDir.listFiles() != null && testClassesDir.listFiles().length > 0;
    }

    /**
     * 分析源码结构
     */
    private String analyzeSourceStructure(MavenProject project) {
        StringBuilder structure = new StringBuilder();

        // 检查主源码目录
        File mainSourceDir = new File(project.getBasedir(), "src/main/java");
        if (mainSourceDir.exists()) {
            structure.append("main-java,");
        }

        // 检查测试源码目录
        File testSourceDir = new File(project.getBasedir(), "src/test/java");
        if (testSourceDir.exists()) {
            structure.append("test-java,");
        }

        // 检查资源目录
        File resourcesDir = new File(project.getBasedir(), "src/main/resources");
        if (resourcesDir.exists()) {
            structure.append("resources,");
        }

        return structure.toString().replaceAll(",$", "");
    }

    /**
     * 准备项目文件
     */
    private ProjectFiles prepareProjectFiles(MavenProject project, File outputDirectory) throws IOException {
        ProjectFiles files = new ProjectFiles();

        // 复制关键项目文件到构建目录
        File buildContextDir = new File(outputDirectory, "docker-build/project-context");
        buildContextDir.mkdirs();

        // 复制pom.xml
        if (project.getFile() != null && project.getFile().exists()) {
            files.pomFile = new File(buildContextDir, "pom.xml");
            Files.copy(project.getFile().toPath(), files.pomFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        // 复制源码目录结构信息
        files.sourceInfo = createSourceInfoFile(project, buildContextDir);

        return files;
    }

    /**
     * 创建源码信息文件
     */
    private File createSourceInfoFile(MavenProject project, File buildContextDir) throws IOException {
        File sourceInfoFile = new File(buildContextDir, "source-info.properties");

        StringBuilder info = new StringBuilder();
        info.append("# Project Source Information\n");
        info.append("project.groupId=").append(project.getGroupId()).append("\n");
        info.append("project.artifactId=").append(project.getArtifactId()).append("\n");
        info.append("project.version=").append(project.getVersion()).append("\n");
        info.append("project.baseDir=").append(project.getBasedir().getAbsolutePath()).append("\n");
        info.append("build.sourceDirectory=").append(project.getBuild().getSourceDirectory()).append("\n");
        info.append("build.testSourceDirectory=").append(project.getBuild().getTestSourceDirectory()).append("\n");
        info.append("build.outputDirectory=").append(project.getBuild().getOutputDirectory()).append("\n");
        info.append("build.testOutputDirectory=").append(project.getBuild().getTestOutputDirectory()).append("\n");

        Files.write(sourceInfoFile.toPath(), info.toString().getBytes());
        return sourceInfoFile;
    }

    /**
     * 创建ImageBuilder实例
     */
    private ImageBuilder createImageBuilder(String imageName, String imageTag, BuildContext context) {
        ImageBuilder.Builder builder = new ImageBuilder.Builder()
                .dockerRegistry(dockerRegistry)
                .imageName(imageName)
                .imageTag(imageTag)
                .pushImage(pushImages)
                .buildTimeoutMinutes(buildTimeoutMinutes);

        // 添加构建参数
        builder.addBuildArg("MAVEN_VERSION", "3.8.5");
        builder.addBuildArg("PITEST_VERSION", "1.9.0");
        builder.addBuildArg("PROJECT_HAS_TESTS", String.valueOf(context.hasTests));
        builder.addBuildArg("SOURCE_STRUCTURE", context.sourceStructure);
        builder.addBuildArg("DEPENDENCY_COUNT", String.valueOf(context.dependencies.size()));

        // 是否包含项目源码
        if (context.hasTests) {
            builder.addBuildArg("INCLUDE_PROJECT_SOURCES", "true");
        }

        return builder.build();
    }

    /**
     * 生成缓存键
     */
    private String generateCacheKey(MavenProject project, String imageName, String imageTag) {
        return String.format("%s:%s:%s:%s",
                project.getGroupId(),
                project.getArtifactId(),
                imageName,
                imageTag);
    }

    /**
     * 清理构建缓存
     */
    public void clearCache() {
        builtImageCache.clear();
        logger.info("Build cache cleared");
    }

    /**
     * 获取缓存状态
     */
    public Map<String, String> getCacheStatus() {
        return new HashMap<>(builtImageCache);
    }

    /**
     * 构建上下文信息
     */
    private static class BuildContext {
        Map<String, String> dependencies = new HashMap<>();
        boolean hasTests = false;
        String sourceStructure = "";
        ProjectFiles projectFiles;
    }

    /**
     * 项目文件信息
     */
    private static class ProjectFiles {
        File pomFile;
        File sourceInfo;
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