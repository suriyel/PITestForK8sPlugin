package com.distributed.pitest.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用于扫描类路径的工具类
 */
public class ClassPathScanner {

    /**
     * 查找目录下的所有Java类
     *
     * @param directory 要扫描的目录
     * @return 类名列表（包含完整的包名）
     */
    public List<String> findClasses(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            return new ArrayList<>();
        }

        try {
            return Files.walk(directory.toPath())
                    .filter(path -> path.toString().endsWith(".class"))
                    .map(path -> convertToClassName(directory.toPath(), path))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("Error scanning directory: " + directory, e);
        }
    }

    /**
     * 将class文件路径转换为完整的类名
     *
     * @param baseDir 基础目录
     * @param classFilePath class文件路径
     * @return 完整的类名
     */
    private String convertToClassName(Path baseDir, Path classFilePath) {
        // 相对路径
        Path relativePath = baseDir.relativize(classFilePath);
        String pathStr = relativePath.toString();

        // 转换分隔符
        pathStr = pathStr.replace(File.separatorChar, '.');

        // 去掉.class后缀
        if (pathStr.endsWith(".class")) {
            pathStr = pathStr.substring(0, pathStr.length() - 6);
        }

        // 排除内部类
        if (pathStr.contains("$")) {
            return null;
        }

        return pathStr;
    }

    /**
     * 判断类是否为测试类
     *
     * @param className 类名
     * @return 如果是测试类则返回true
     */
    public boolean isTestClass(String className) {
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        return simpleName.startsWith("Test") || simpleName.endsWith("Test") ||
                simpleName.endsWith("Tests") || simpleName.endsWith("TestCase");
    }
}