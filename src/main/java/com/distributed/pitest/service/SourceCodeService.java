package com.distributed.pitest.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 源代码提取服务，负责从项目中查找和读取源文件
 */
public class SourceCodeService {
    private static final Logger logger = LoggerFactory.getLogger(SourceCodeService.class);
    private final File projectBaseDir;
    private final Map<String, String> sourceCodeCache = new HashMap<>();
    private List<File> sourceDirs = null;

    public SourceCodeService(File projectBaseDir) {
        this.projectBaseDir = projectBaseDir;
    }

    /**
     * 获取指定类的源代码
     *
     * @param className 完全限定的类名
     * @param sourceFileName 源文件名（可选）
     * @return 源代码文本，如果未找到则返回null
     */
    public String getSourceCode(String className, String sourceFileName) {
        // 首先尝试从缓存获取
        if (sourceCodeCache.containsKey(className)) {
            return sourceCodeCache.get(className);
        }

        // 如果没有提供源文件名，从类名推断
        if (sourceFileName == null || sourceFileName.isEmpty()) {
            sourceFileName = className.substring(className.lastIndexOf('.') + 1) + ".java";
        }

        // 查找源文件
        File sourceFile = findSourceFile(className, sourceFileName);
        if (sourceFile == null || !sourceFile.exists()) {
            logger.warn("Source file not found for class: {}", className);
            return null;
        }

        try {
            // 读取源文件内容
            String sourceCode = readFileContent(sourceFile);

            // 缓存结果
            sourceCodeCache.put(className, sourceCode);

            return sourceCode;
        } catch (IOException e) {
            logger.error("Error reading source file for class: {}", className, e);
            return null;
        }
    }

    /**
     * 查找源文件
     */
    public File findSourceFile(String className, String sourceFileName) {
        String packagePath = extractPackageName(className).replace('.', File.separatorChar);

        // 尝试在常见源码目录查找
        File sourceFile = tryFindInCommonSourceDirs(packagePath, sourceFileName);
        if (sourceFile != null && sourceFile.exists()) {
            return sourceFile;
        }

        // 如果未找到，搜索整个项目
        sourceFile = searchInAllSourceDirs(packagePath, sourceFileName);
        if (sourceFile != null && sourceFile.exists()) {
            return sourceFile;
        }

        return null;
    }

    /**
     * 在常见源码目录查找文件
     */
    private File tryFindInCommonSourceDirs(String packagePath, String sourceFileName) {
        // src/main/java
        File mainSourceDir = new File(projectBaseDir, "src/main/java");
        File sourceFile = new File(mainSourceDir, packagePath + File.separator + sourceFileName);
        if (sourceFile.exists()) {
            return sourceFile;
        }

        // src/test/java
        File testSourceDir = new File(projectBaseDir, "src/test/java");
        sourceFile = new File(testSourceDir, packagePath + File.separator + sourceFileName);
        if (sourceFile.exists()) {
            return sourceFile;
        }

        return null;
    }

    /**
     * 在所有源码目录查找文件
     */
    private File searchInAllSourceDirs(String packagePath, String sourceFileName) {
        if (sourceDirs == null) {
            sourceDirs = findAllSourceDirs();
        }

        for (File dir : sourceDirs) {
            File sourceFile = new File(dir, packagePath + File.separator + sourceFileName);
            if (sourceFile.exists()) {
                return sourceFile;
            }
        }

        return null;
    }

    /**
     * 查找项目中所有可能的源码目录
     */
    private List<File> findAllSourceDirs() {
        List<File> dirs = new ArrayList<>();

        try {
            // 搜索所有java目录
            Files.walk(projectBaseDir.toPath())
                    .filter(path -> path.toString().endsWith("/java") || path.toString().endsWith("\\java"))
                    .filter(Files::isDirectory)
                    .forEach(path -> dirs.add(path.toFile()));

            logger.info("Found {} potential source directories", dirs.size());
        } catch (IOException e) {
            logger.warn("Error searching for source directories", e);
        }

        return dirs;
    }

    /**
     * 读取文件内容
     */
    private String readFileContent(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    /**
     * 从类名中提取包名
     */
    private String extractPackageName(String className) {
        int lastDotIndex = className.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return className.substring(0, lastDotIndex);
        }
        return "";
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        sourceCodeCache.clear();
    }
}