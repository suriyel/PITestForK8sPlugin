package com.distributed.pitest.result;

import com.distributed.pitest.model.AggregatedResult;
import com.distributed.pitest.model.ExecutionResult;

import java.io.File;
import java.util.List;

/**
 * 结果聚合器接口，负责聚合多个执行结果并生成报告
 */
public interface ResultAggregator {

    /**
     * 聚合多个执行结果
     *
     * @param results 执行结果列表
     * @return 聚合后的结果
     */
    AggregatedResult aggregateResults(List<ExecutionResult> results);

    /**
     * 生成报告
     *
     * @param result 聚合后的结果
     * @param outputDirectory 输出目录
     */
    void generateReport(AggregatedResult result, File outputDirectory);
}