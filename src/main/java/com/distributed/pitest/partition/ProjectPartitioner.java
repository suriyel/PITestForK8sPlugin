package com.distributed.pitest.partition;

import com.distributed.pitest.model.PitestConfiguration;
import com.distributed.pitest.model.TestPartition;
import org.apache.maven.project.MavenProject;

import java.util.List;

/**
 * 项目分区器接口，负责将项目分解为可独立执行的测试分区
 */
public interface ProjectPartitioner {
    /**
     * 将Maven项目分区为多个测试分区
     *
     * @param project Maven项目
     * @param config Pitest配置
     * @return 测试分区列表
     */
    List<TestPartition> partitionProject(MavenProject project, PitestConfiguration config);
}