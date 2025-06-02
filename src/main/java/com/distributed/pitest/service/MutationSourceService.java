package com.distributed.pitest.service;

import com.distributed.pitest.model.MutationResult;
import com.distributed.pitest.util.AstAnalyzer;
import com.distributed.pitest.util.MutationAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 源代码和变异提取服务，用于提供更精确的代码位置和变异信息
 */
public class MutationSourceService {
    private static final Logger logger = LoggerFactory.getLogger(MutationSourceService.class);
    private final File projectBaseDir;
    private final AstAnalyzer astAnalyzer;
    private final MutationAnalyzer mutationAnalyzer;

    public MutationSourceService(File projectBaseDir) {
        this.projectBaseDir = projectBaseDir;
        this.astAnalyzer = new AstAnalyzer();
        this.mutationAnalyzer = new MutationAnalyzer();
    }

    /**
     * 增强变异结果，添加源代码和变异代码信息
     *
     * @param mutationResults 原始变异结果列表
     * @return 增强后的变异结果列表
     */
    public List<MutationResult> enhanceMutationResults(List<MutationResult> mutationResults) {
        if (mutationResults == null || mutationResults.isEmpty()) {
            return new ArrayList<>();
        }

        logger.info("Enhancing {} mutation results with source code information", mutationResults.size());

        List<MutationResult> enhancedResults = new ArrayList<>();

        for (MutationResult mutation : mutationResults) {
            try {
                MutationResult enhancedMutation = enhanceMutation(mutation);
                enhancedResults.add(enhancedMutation);
            } catch (Exception e) {
                logger.warn("Error enhancing mutation: {}", e.getMessage());
                enhancedResults.add(mutation); // 保留原始变异
            }
        }

        return enhancedResults;
    }

    /**
     * 增强单个变异结果
     */
    private MutationResult enhanceMutation(MutationResult mutation) {
        // 如果已经有完整信息，无需增强
        if (mutation.getOriginalCode() != null && !mutation.getOriginalCode().isEmpty() &&
                mutation.getMutatedCode() != null && !mutation.getMutatedCode().isEmpty()) {
            return mutation;
        }

        // 尝试定位源文件
        File sourceFile = findSourceFile(mutation.getMutatedClass(), mutation.getSourceFile());

        if (sourceFile == null || !sourceFile.exists()) {
            logger.debug("Source file not found for: {}", mutation.getMutatedClass());
            return mutation;
        }

        try {
            // 提取代码上下文
            AstAnalyzer.CodeContext context = astAnalyzer.extractCodeContext(
                    sourceFile,
                    mutation.getMutatedClass(),
                    mutation.getMutatedMethod(),
                    mutation.getLineNumber()
            );

            String originalCode = context.getOriginalCode();

            // 如果AST解析未能提取代码，尝试直接从文件读取
            if (originalCode == null || originalCode.isEmpty()) {
                originalCode = extractCodeLineFromFile(sourceFile, mutation.getLineNumber());
            }

            // 生成变异代码
            String mutatedCode = "";
            if (originalCode != null && !originalCode.isEmpty()) {
                mutatedCode = mutationAnalyzer.generateMutatedCode(
                        originalCode,
                        mutation.getDescription(),
                        mutation.getMutator()
                );
            }

            // 创建增强的变异结果
            return MutationResult.builder()
                    .mutatedClass(mutation.getMutatedClass())
                    .mutatedMethod(mutation.getMutatedMethod())
                    .lineNumber(mutation.getLineNumber())
                    .mutator(mutation.getMutator())
                    .description(mutation.getDescription())
                    .detected(mutation.isDetected())
                    .killingTest(mutation.getKillingTest())
                    .status(mutation.getStatus())
                    .sourceFile(mutation.getSourceFile())
                    .methodDescription(mutation.getMethodDescription())
                    .indexes(mutation.getIndexes())
                    .blocks(mutation.getBlocks())
                    .originalCode(originalCode)
                    .mutatedCode(mutatedCode)
                    .firstLine(context.getMethodStartLine() > 0 ? context.getMethodStartLine() : mutation.getFirstLine())
                    .lastLine(context.getMethodEndLine() > 0 ? context.getMethodEndLine() : mutation.getLastLine())
                    .filename(sourceFile.getName())
                    .packageName(context.getPackageName().isEmpty() ? extractPackageName(mutation.getMutatedClass()) : context.getPackageName())
                    .build();

        } catch (Exception e) {
            logger.warn("Error extracting source code for mutation: {}", e.getMessage());
            return mutation;
        }
    }

    /**
     * 从源文件中提取指定行的代码
     */
    private String extractCodeLineFromFile(File sourceFile, int lineNumber) {
        try (BufferedReader reader = new BufferedReader(new FileReader(sourceFile))) {
            List<String> lines = reader.lines().collect(Collectors.toList());

            if (lineNumber > 0 && lineNumber <= lines.size()) {
                // 返回指定行及其上下文（前后各一行）
                int start = Math.max(0, lineNumber - 2);
                int end = Math.min(lines.size() - 1, lineNumber);

                StringBuilder sb = new StringBuilder();
                for (int i = start; i <= end; i++) {
                    sb.append(lines.get(i)).append("\n");
                }

                return sb.toString().trim();
            }
        } catch (IOException e) {
            logger.warn("Error reading source file: {}", e.getMessage());
        }

        return "";
    }

    /**
     * 查找源文件
     */
    private File findSourceFile(String className, String sourceFileName) {
        String packagePath = extractPackageName(className).replace('.', File.separatorChar);

        // 尝试在src/main/java目录下查找
        File mainSourceDir = new File(projectBaseDir, "src/main/java");
        File sourceFile = new File(mainSourceDir, packagePath + File.separator + sourceFileName);
        if (sourceFile.exists()) {
            return sourceFile;
        }

        // 尝试在src/test/java目录下查找
        File testSourceDir = new File(projectBaseDir, "src/test/java");
        sourceFile = new File(testSourceDir, packagePath + File.separator + sourceFileName);
        if (sourceFile.exists()) {
            return sourceFile;
        }

        // 尝试在其他可能的源码目录查找
        try {
            List<File> sourceDirs = new ArrayList<>();
            java.nio.file.Files.walk(projectBaseDir.toPath())
                    .filter(path -> path.toString().endsWith("/java") || path.toString().endsWith("\\java"))
                    .filter(path -> java.nio.file.Files.isDirectory(path))
                    .forEach(path -> sourceDirs.add(path.toFile()));

            for (File dir : sourceDirs) {
                sourceFile = new File(dir, packagePath + File.separator + sourceFileName);
                if (sourceFile.exists()) {
                    return sourceFile;
                }
            }
        } catch (IOException e) {
            logger.warn("Error searching for source directories: {}", e.getMessage());
        }

        return null;
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
}