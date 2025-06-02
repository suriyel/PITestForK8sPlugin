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

/**
 * 默认项目分区实现，基于简单的依赖分析
 */
public class DefaultProjectPartitioner implements ProjectPartitioner {
    private static final Logger logger = LoggerFactory.getLogger(DefaultProjectPartitioner.class);
    private final int maxClassesPerPartition;
    private final ClassPathScanner scanner;

    public DefaultProjectPartitioner() {
        this(10); // 默认每个分区最多10个类
    }

    public DefaultProjectPartitioner(int maxClassesPerPartition) {
        this.maxClassesPerPartition = maxClassesPerPartition;
        this.scanner = new ClassPathScanner();
    }

    @Override
    public List<TestPartition> partitionProject(MavenProject project, PitestConfiguration config) {
        logger.info("Partitioning project using DefaultProjectPartitioner");

        // 1. 分析项目结构
        List<String> allClasses = findAllClasses(project);
        List<String> allTests = findAllTests(project);

        logger.info("Found {} classes and {} tests", allClasses.size(), allTests.size());

        // 2. 分析依赖关系（简化版只基于类名匹配）
        Map<String, List<String>> testsByClass = mapTestsToClasses(allClasses, allTests);

        // 3. 创建分区
        return createPartitions(allClasses, testsByClass, config);
    }

    private List<String> findAllClasses(MavenProject project) {
        File classesDir = new File(project.getBuild().getOutputDirectory());
        return scanner.findClasses(classesDir);
    }

    private List<String> findAllTests(MavenProject project) {
        File testClassesDir = new File(project.getBuild().getTestOutputDirectory());
        return scanner.findClasses(testClassesDir);
    }

    private Map<String, List<String>> mapTestsToClasses(List<String> classes, List<String> tests) {
        Map<String, List<String>> testsByClass = new HashMap<>();

        for (String clazz : classes) {
            List<String> matchingTests = new ArrayList<>();
            String simpleClassName = clazz.substring(clazz.lastIndexOf('.') + 1);

            for (String test : tests) {
                if (test.contains(simpleClassName) || test.endsWith("Test")) {
                    matchingTests.add(test);
                }
            }

            testsByClass.put(clazz, matchingTests);
        }

        return testsByClass;
    }

    private List<TestPartition> createPartitions(List<String> classes,
                                                 Map<String, List<String>> testsByClass,
                                                 PitestConfiguration config) {
        List<TestPartition> partitions = new ArrayList<>();
        List<String> currentClasses = new ArrayList<>();
        List<String> currentTests = new ArrayList<>();
        int partitionCount = 0;

        for (String clazz : classes) {
            currentClasses.add(clazz);
            currentTests.addAll(testsByClass.get(clazz));

            if (currentClasses.size() >= maxClassesPerPartition) {
                // 创建新分区
                TestPartition partition = createPartition(
                        "partition-" + partitionCount++,
                        new ArrayList<>(currentClasses),
                        new ArrayList<>(currentTests),
                        config
                );

                partitions.add(partition);

                // 清空当前列表
                currentClasses.clear();
                currentTests.clear();
            }
        }

        // 处理剩余的类
        if (!currentClasses.isEmpty()) {
            TestPartition partition = createPartition(
                    "partition-" + partitionCount,
                    currentClasses,
                    currentTests,
                    config
            );

            partitions.add(partition);
        }

        return partitions;
    }

    private TestPartition createPartition(String id, List<String> classes, List<String> tests,
                                          PitestConfiguration config) {
        Map<String, String> properties = new HashMap<>(config.getAdditionalProperties());

        return TestPartition.builder()
                .id(id)
                .targetClasses(classes)
                .targetTests(tests)
                .properties(properties)
                .build();
    }
}