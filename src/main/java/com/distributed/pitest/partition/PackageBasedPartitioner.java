package com.distributed.pitest.partition;

import com.distributed.pitest.model.PitestConfiguration;
import com.distributed.pitest.model.TestPartition;
import com.distributed.pitest.util.ClassPathScanner;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 基于包结构的项目分区器
 */
public class PackageBasedPartitioner implements ProjectPartitioner {
    private static final Logger logger = LoggerFactory.getLogger(PackageBasedPartitioner.class);
    private final ClassPathScanner scanner;

    public PackageBasedPartitioner() {
        this.scanner = new ClassPathScanner();
    }

    @Override
    public List<TestPartition> partitionProject(MavenProject project, PitestConfiguration config) {
        logger.info("Partitioning project by package structure");

        // 1. 查找所有类和测试
        List<String> allClasses = findAllClasses(project);
        List<String> allTests = findAllTests(project);

        logger.info("Found {} classes and {} tests", allClasses.size(), allTests.size());

        // 2. 按包分组
        Map<String, List<String>> classesByPackage = groupByPackage(allClasses);
        Map<String, List<String>> testsByPackage = groupByPackage(allTests);

        // 3. 创建分区
        return createPartitions(classesByPackage, testsByPackage, config);
    }

    private List<String> findAllClasses(MavenProject project) {
        File classesDir = new File(project.getBuild().getOutputDirectory());
        return scanner.findClasses(classesDir);
    }

    private List<String> findAllTests(MavenProject project) {
        File testClassesDir = new File(project.getBuild().getTestOutputDirectory());
        return scanner.findClasses(testClassesDir);
    }

    private Map<String, List<String>> groupByPackage(List<String> classes) {
        Map<String, List<String>> classesByPackage = new HashMap<>();

        for (String className : classes) {
            String packageName = getPackageName(className);
            classesByPackage
                    .computeIfAbsent(packageName, k -> new ArrayList<>())
                    .add(className);
        }

        return classesByPackage;
    }

    private String getPackageName(String className) {
        int lastDotIndex = className.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return className.substring(0, lastDotIndex);
        }
        return "";
    }

    private List<TestPartition> createPartitions(
            Map<String, List<String>> classesByPackage,
            Map<String, List<String>> testsByPackage,
            PitestConfiguration config) {

        List<TestPartition> partitions = new ArrayList<>();
        int partitionCount = 0;

        // 为每个包创建一个分区
        for (Map.Entry<String, List<String>> entry : classesByPackage.entrySet()) {
            String packageName = entry.getKey();
            List<String> packageClasses = entry.getValue();

            // 找到测试包中相应的测试类
            List<String> packageTests = findMatchingTests(packageName, testsByPackage);

            // 如果没有找到测试类，尝试根据类名匹配
            if (packageTests.isEmpty()) {
                packageTests = findTestsByClassNames(packageClasses, testsByPackage);
            }

            // 创建分区
            TestPartition partition = createPartition(
                    "package-" + partitionCount++,
                    packageName,
                    packageClasses,
                    packageTests,
                    config
            );

            partitions.add(partition);
        }

        return partitions;
    }

    private List<String> findMatchingTests(String packageName, Map<String, List<String>> testsByPackage) {
        // 查找直接匹配的测试包
        List<String> directMatches = testsByPackage.getOrDefault(packageName, new ArrayList<>());

        // 查找.test包下的测试
        List<String> testPackageMatches = testsByPackage.getOrDefault(packageName + ".test", new ArrayList<>());

        // 查找Tests包下的测试
        List<String> testsPackageMatches = testsByPackage.getOrDefault(packageName + ".tests", new ArrayList<>());

        // 合并结果
        List<String> result = new ArrayList<>(directMatches);
        result.addAll(testPackageMatches);
        result.addAll(testsPackageMatches);

        return result;
    }

    private List<String> findTestsByClassNames(List<String> classes, Map<String, List<String>> testsByPackage) {
        List<String> matchingTests = new ArrayList<>();

        // 提取类的简单名称（不含包名）
        List<String> simpleClassNames = classes.stream()
                .map(className -> className.substring(className.lastIndexOf('.') + 1))
                .collect(Collectors.toList());

        // 查找所有测试类
        List<String> allTests = new ArrayList<>();
        testsByPackage.values().forEach(allTests::addAll);

        // 根据类名匹配测试
        for (String testClass : allTests) {
            String simpleTestName = testClass.substring(testClass.lastIndexOf('.') + 1);

            for (String simpleClassName : simpleClassNames) {
                if (simpleTestName.contains(simpleClassName) ||
                        simpleTestName.equals(simpleClassName + "Test") ||
                        simpleTestName.equals("Test" + simpleClassName)) {
                    matchingTests.add(testClass);
                    break;
                }
            }
        }

        return matchingTests;
    }

    private TestPartition createPartition(
            String id,
            String packageName,
            List<String> classes,
            List<String> tests,
            PitestConfiguration config) {

        Map<String, String> properties = new HashMap<>(config.getAdditionalProperties());

        // 添加包特定的属性
        properties.put("package", packageName);

        return TestPartition.builder()
                .id(id)
                .targetClasses(classes)
                .targetTests(tests)
                .properties(properties)
                .build();
    }
}