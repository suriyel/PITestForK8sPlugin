package com.distributed.pitest.report;

import com.distributed.pitest.model.AggregatedResult;

import java.io.File;

/**
 * 报告生成器接口，负责生成最终的突变测试报告
 */
public interface ReportGenerator {

    /**
     * 生成报告
     *
     * @param result 聚合后的结果
     * @param outputDirectory 输出目录
     */
    void generateReport(AggregatedResult result, File outputDirectory);
}