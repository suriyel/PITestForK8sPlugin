package com.distributed.pitest.kubernetes;

import com.distributed.pitest.model.ExecutionResult;
import com.distributed.pitest.model.TestPartition;
import io.fabric8.kubernetes.api.model.Pod;

import java.util.List;

/**
 * Kubernetes执行器接口，负责在Kubernetes集群上执行测试
 */
public interface KubernetesExecutor {

    /**
     * 在Kubernetes上执行测试分区
     *
     * @param partition 测试分区
     * @param config 执行配置
     * @return 执行结果
     */
    ExecutionResult executeTests(TestPartition partition, ExecutionConfig config);

    /**
     * 获取当前活动的Pod列表
     *
     * @return 活动Pod列表
     */
    List<Pod> getActivePods();

    /**
     * 清理所有创建的Kubernetes资源
     */
    void cleanupResources();
}