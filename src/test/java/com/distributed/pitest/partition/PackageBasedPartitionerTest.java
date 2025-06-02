package com.distributed.pitest.partition;

import com.distributed.pitest.model.PitestConfiguration;
import com.distributed.pitest.model.TestPartition;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PackageBasedPartitionerTest {

    private PackageBasedPartitioner partitioner;

    @Mock
    private MavenProject project;

    @Mock
    private Build build;

    private File tempClassesDir;
    private File tempTestClassesDir;

    @Before
    public void setUp() throws IOException {
        partitioner = new PackageBasedPartitioner();

        // 创建临时目录模拟类路径
        Path tempDir = Files.createTempDirectory("pitest-test");
        tempClassesDir = new File(tempDir.toFile(), "classes");
        tempTestClassesDir = new File(tempDir.toFile(), "test-classes");

        tempClassesDir.mkdirs();
        tempTestClassesDir.mkdirs();

        // 创建包结构
        createPackageStructure();

        // 设置Mock
        when(project.getBuild()).thenReturn(build);
        when(build.getOutputDirectory()).thenReturn(tempClassesDir.getAbsolutePath());
        when(build.getTestOutputDirectory()).thenReturn(tempTestClassesDir.getAbsolutePath());
    }

    @Test
    public void testPartitionProject() {
        // 创建配置
        PitestConfiguration config = PitestConfiguration.builder()
                .outputDirectory("target/pit-reports")
                .build();

        // 执行分区
        List<TestPartition> partitions = partitioner.partitionProject(project, config);

        // 验证结果
        assertNotNull(partitions);
        assertFalse(partitions.isEmpty());

        // 验证每个分区包含正确的类和测试
        for (TestPartition partition : partitions) {
            String packageName = partition.getProperties().get("package");
            assertNotNull("Package name should be in properties", packageName);

            // 验证目标类属于同一个包
            for (String targetClass : partition.getTargetClasses()) {
                assertTrue("Class should belong to the package",
                        targetClass.startsWith(packageName + "."));
            }

            // 验证测试类匹配模式
            for (String targetTest : partition.getTargetTests()) {
                boolean isMatchingTest = false;

                // 同包测试
                if (targetTest.startsWith(packageName + ".")) {
                    isMatchingTest = true;
                }

                // 测试包测试
                if (targetTest.startsWith(packageName + ".test.") ||
                        targetTest.startsWith(packageName + ".tests.")) {
                    isMatchingTest = true;
                }

                // 检查类名匹配
                for (String targetClass : partition.getTargetClasses()) {
                    String simpleClassName = targetClass.substring(targetClass.lastIndexOf('.') + 1);
                    String simpleTestName = targetTest.substring(targetTest.lastIndexOf('.') + 1);

                    if (simpleTestName.contains(simpleClassName) ||
                            simpleTestName.equals(simpleClassName + "Test") ||
                            simpleTestName.equals("Test" + simpleClassName)) {
                        isMatchingTest = true;
                        break;
                    }
                }

                assertTrue("Test should match the package or class pattern: " + targetTest, isMatchingTest);
            }
        }
    }

    private void createPackageStructure() throws IOException {
        // 创建生产类
        createClassFile(tempClassesDir, "com/example/a/ClassA.class");
        createClassFile(tempClassesDir, "com/example/a/OtherA.class");
        createClassFile(tempClassesDir, "com/example/b/ClassB.class");

        // 创建测试类
        createClassFile(tempTestClassesDir, "com/example/a/ClassATest.class");
        createClassFile(tempTestClassesDir, "com/example/a/test/OtherATest.class");
        createClassFile(tempTestClassesDir, "com/example/b/TestClassB.class");
    }

    private void createClassFile(File baseDir, String classPath) throws IOException {
        File classFile = new File(baseDir, classPath);
        classFile.getParentFile().mkdirs();
        classFile.createNewFile();
    }
}