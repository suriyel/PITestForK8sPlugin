package com.distributed.pitest.util;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 源代码AST解析工具类，用于获取精确的代码位置和上下文信息
 */
public class AstAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(AstAnalyzer.class);

    /**
     * 解析Java源文件并提取指定行号的代码块信息
     *
     * @param sourceFile Java源文件
     * @param className 类名
     * @param methodName 方法名
     * @param lineNumber 行号
     * @return 代码上下文信息，包括原始代码、代码块等
     */
    public CodeContext extractCodeContext(File sourceFile, String className,
                                          String methodName, int lineNumber) {
        logger.debug("Extracting code context for {}.{} at line {}",
                className, methodName, lineNumber);

        try (FileInputStream in = new FileInputStream(sourceFile)) {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(in).getResult().orElse(null);

            if (cu == null) {
                logger.warn("Failed to parse source file: {}", sourceFile);
                return new CodeContext();
            }

            // 提取包名
            String packageName = "";
            if (cu.getPackageDeclaration().isPresent()) {
                packageName = cu.getPackageDeclaration().get().getNameAsString();
            }

            // 查找类型声明
            Optional<TypeDeclaration<?>> typeDeclaration = cu.getTypes().stream()
                    .filter(type -> type.getNameAsString().equals(getSimpleClassName(className)))
                    .findFirst();

            if (!typeDeclaration.isPresent()) {
                logger.warn("Class {} not found in source file", className);
                return new CodeContext();
            }

            // 查找方法声明
            Optional<MethodDeclaration> methodDeclaration = typeDeclaration.get().getMethods().stream()
                    .filter(method -> method.getNameAsString().equals(methodName))
                    .findFirst();

            if (!methodDeclaration.isPresent()) {
                logger.warn("Method {}.{} not found in source file", className, methodName);
                return new CodeContext();
            }

            MethodDeclaration method = methodDeclaration.get();

            // 查找包含行号的代码块
            Optional<BlockStmt> methodBody = method.getBody();
            if (!methodBody.isPresent()) {
                logger.warn("Method body not found for {}.{}", className, methodName);
                return new CodeContext();
            }

            // 提取行号范围
            int methodStartLine = method.getBegin().isPresent() ? method.getBegin().get().line : -1;
            int methodEndLine = method.getEnd().isPresent() ? method.getEnd().get().line : -1;

            // 提取包含目标行的语句
            Statement targetStatement = findStatementAtLine(methodBody.get(), lineNumber);
            String statementCode = targetStatement != null ? targetStatement.toString() : "";

            // 计算代码块ID
            int blockId = calculateBlockId(methodBody.get(), targetStatement);

            // 构建返回结果
            return new CodeContext()
                    .setPackageName(packageName)
                    .setMethodStartLine(methodStartLine)
                    .setMethodEndLine(methodEndLine)
                    .setOriginalCode(statementCode)
                    .setBlockId(blockId);

        } catch (Exception e) {
            logger.error("Error analyzing source file: {}", sourceFile, e);
            return new CodeContext();
        }
    }

    /**
     * 在给定的代码块中查找指定行号的语句
     */
    private Statement findStatementAtLine(BlockStmt block, int lineNumber) {
        for (Statement stmt : block.getStatements()) {
            // 检查该语句是否包含目标行
            if (stmt.getBegin().isPresent() && stmt.getEnd().isPresent()) {
                int startLine = stmt.getBegin().get().line;
                int endLine = stmt.getEnd().get().line;

                if (lineNumber >= startLine && lineNumber <= endLine) {
                    // 如果语句内还有子块，递归查找
                    if (stmt instanceof BlockStmt) {
                        Statement innerStmt = findStatementAtLine((BlockStmt) stmt, lineNumber);
                        return innerStmt != null ? innerStmt : stmt;
                    }
                    return stmt;
                }
            }

            // 对于包含子块的语句，递归查找
            Optional<BlockStmt> childBlock = getChildBlock(stmt);
            if (childBlock.isPresent()) {
                Statement innerStmt = findStatementAtLine(childBlock.get(), lineNumber);
                if (innerStmt != null) {
                    return innerStmt;
                }
            }
        }
        return null;
    }

    /**
     * 提取语句中的子代码块（如if/else语句、循环语句等）
     */
    private Optional<BlockStmt> getChildBlock(Statement stmt) {
        for (Node child : stmt.getChildNodes()) {
            if (child instanceof BlockStmt) {
                return Optional.of((BlockStmt) child);
            }
        }
        return Optional.empty();
    }

    /**
     * 计算语句在方法中的代码块ID
     */
    private int calculateBlockId(BlockStmt methodBody, Statement targetStmt) {
        if (targetStmt == null) {
            return -1;
        }

        List<BlockStmt> allBlocks = new ArrayList<>();
        collectAllBlocks(methodBody, allBlocks);

        // 查找包含目标语句的块
        for (int i = 0; i < allBlocks.size(); i++) {
            BlockStmt block = allBlocks.get(i);
            if (block.getStatements().contains(targetStmt)) {
                return i;
            }
        }

        return -1;
    }

    /**
     * 收集方法中的所有代码块
     */
    private void collectAllBlocks(BlockStmt block, List<BlockStmt> allBlocks) {
        allBlocks.add(block);

        for (Statement stmt : block.getStatements()) {
            for (Node child : stmt.getChildNodes()) {
                if (child instanceof BlockStmt) {
                    collectAllBlocks((BlockStmt) child, allBlocks);
                }
            }
        }
    }

    /**
     * 从完全限定名中提取简单类名
     */
    private String getSimpleClassName(String fullyQualifiedName) {
        int lastDotIndex = fullyQualifiedName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fullyQualifiedName.substring(lastDotIndex + 1);
        }
        return fullyQualifiedName;
    }

    /**
     * 代码上下文信息类，包含源代码分析的结果
     */
    public static class CodeContext {
        private String packageName = "";
        private int methodStartLine = -1;
        private int methodEndLine = -1;
        private String originalCode = "";
        private int blockId = -1;

        public String getPackageName() {
            return packageName;
        }

        public CodeContext setPackageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public int getMethodStartLine() {
            return methodStartLine;
        }

        public CodeContext setMethodStartLine(int methodStartLine) {
            this.methodStartLine = methodStartLine;
            return this;
        }

        public int getMethodEndLine() {
            return methodEndLine;
        }

        public CodeContext setMethodEndLine(int methodEndLine) {
            this.methodEndLine = methodEndLine;
            return this;
        }

        public String getOriginalCode() {
            return originalCode;
        }

        public CodeContext setOriginalCode(String originalCode) {
            this.originalCode = originalCode;
            return this;
        }

        public int getBlockId() {
            return blockId;
        }

        public CodeContext setBlockId(int blockId) {
            this.blockId = blockId;
            return this;
        }
    }
}