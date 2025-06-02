package com.distributed.pitest.util;

import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maven项目扫描工具，用于分析项目结构和依赖
 */
public class MavenProjectScanner {
    private static final Logger logger = LoggerFactory.getLogger(MavenProjectScanner.class);
    private final ClassPathScanner classPathScanner;

    public MavenProjectScanner() {
        this.classPathScanner = new ClassPathScanner();
    }

    /**
     * 扫描项目中的所有Java类
     *
     * @param project Maven项目
     * @return 项目中的所有Java类列表
     */
    public List<String> scanProjectClasses(MavenProject project) {
        logger.info("Scanning classes in project: {}", project.getName());
        File classesDir = new File(project.getBuild().getOutputDirectory());
        return classPathScanner.findClasses(classesDir);
    }

    /**
     * 扫描项目中的所有测试类
     *
     * @param project Maven项目
     * @return 项目中的所有测试类列表
     */
    public List<String> scanProjectTestClasses(MavenProject project) {
        logger.info("Scanning test classes in project: {}", project.getName());
        File testClassesDir = new File(project.getBuild().getTestOutputDirectory());
        return classPathScanner.findClasses(testClassesDir);
    }

    /**
     * 将类按包分组
     *
     * @param classes 类列表
     * @return 按包分组的类列表
     */
    public Map<String, List<String>> groupClassesByPackage(List<String> classes) {
        Map<String, List<String>> packageMap = new HashMap<>();

        for (String className : classes) {
            String packageName = extractPackageName(className);
            packageMap.computeIfAbsent(packageName, k -> new ArrayList<>()).add(className);
        }

        return packageMap;
    }

    /**
     * 尝试匹配测试类与生产类
     *
     * @param classes 生产类列表
     * @param testClasses 测试类列表
     * @return 每个生产类对应的测试类列表
     */
    public Map<String, List<String>> mapTestsToClasses(List<String> classes, List<String> testClasses) {
        Map<String, List<String>> mapping = new HashMap<>();

        for (String className : classes) {
            List<String> matchedTests = new ArrayList<>();
            String simpleClassName = extractSimpleClassName(className);

            for (String testClass : testClasses) {
                String simpleTestName = extractSimpleClassName(testClass);

                // 匹配测试类（例如：MyClass -> MyClassTest）
                if (simpleTestName.equals(simpleClassName + "Test") ||
                        simpleTestName.equals("Test" + simpleClassName) ||
                        simpleTestName.contains(simpleClassName)) {
                    matchedTests.add(testClass);
                }
            }

            mapping.put(className, matchedTests);
        }

        return mapping;
    }

    /**
     * 获取类的包名
     *
     * @param className 完整类名
     * @return 包名
     */
    public String extractPackageName(String className) {
        int lastDotIndex = className.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return className.substring(0, lastDotIndex);
        }
        return "";
    }

    /**
     * 获取类的简单名称（不含包名）
     *
     * @param className 完整类名
     * @return 简单类名
     */
    public String extractSimpleClassName(String className) {
        int lastDotIndex = className.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return className.substring(lastDotIndex + 1);
        }
        return className;
    }
}