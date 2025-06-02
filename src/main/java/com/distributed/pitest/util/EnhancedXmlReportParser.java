package com.distributed.pitest.util;

import com.distributed.pitest.model.MutationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 增强的XML解析器，提取更完整的变异测试结果细节
 */
public class EnhancedXmlReportParser {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedXmlReportParser.class);
    private final AstAnalyzer astAnalyzer;
    private final SourceFileLocator sourceFileLocator;

    public EnhancedXmlReportParser() {
        this.astAnalyzer = new AstAnalyzer();
        this.sourceFileLocator = new SourceFileLocator();
    }

    /**
     * 解析Pitest突变测试结果XML文件，并附加源代码信息
     *
     * @param xmlFile XML文件
     * @param projectBaseDir 项目基础目录，用于定位源文件
     * @return 突变测试结果列表
     * @throws IOException 如果文件读取错误
     */
    public List<MutationResult> parseMutationXml(File xmlFile, File projectBaseDir) throws IOException {
        logger.info("Parsing mutations XML file: {}", xmlFile.getAbsolutePath());
        List<MutationResult> results = new ArrayList<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(xmlFile);
            document.getDocumentElement().normalize();

            NodeList mutationNodes = document.getElementsByTagName("mutation");
            logger.info("Found {} mutation elements in XML", mutationNodes.getLength());

            // 缓存已解析的源文件信息
            Map<String, File> sourceFileCache = new HashMap<>();

            for (int i = 0; i < mutationNodes.getLength(); i++) {
                Node node = mutationNodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;

                    MutationResult result = parseMutationElement(element, projectBaseDir, sourceFileCache);
                    if (result != null) {
                        results.add(result);
                    }
                }
            }

            logger.info("Successfully parsed {} mutation results", results.size());

        } catch (ParserConfigurationException | SAXException e) {
            logger.error("Error parsing XML file: {}", xmlFile, e);
            throw new IOException("Error parsing XML file: " + e.getMessage(), e);
        }

        return results;
    }

    /**
     * 解析单个变异元素
     */
    private MutationResult parseMutationElement(Element element, File projectBaseDir,
                                                Map<String, File> sourceFileCache) {
        try {
            // 解析基本属性
            boolean detected = Boolean.parseBoolean(element.getAttribute("detected"));
            String status = element.getAttribute("status");

            // 解析子元素
            String sourceFileName = getElementTextContent(element, "sourceFile");
            String mutatedClass = getElementTextContent(element, "mutatedClass");
            String mutatedMethod = getElementTextContent(element, "mutatedMethod");
            String methodDescription = getElementTextContent(element, "methodDescription");
            int lineNumber = Integer.parseInt(getElementTextContent(element, "lineNumber"));
            String mutator = getElementTextContent(element, "mutator");
            String description = getElementTextContent(element, "description");
            String killingTest = getElementTextContent(element, "killingTest");

            // 解析索引和代码块
            int[] indexes = parseIntArray(getElementTextContent(element, "indexes"));
            int[] blocks = parseIntArray(getElementTextContent(element, "blocks"));

            // 尝试定位源文件并提取代码上下文
            String packageName = extractPackageName(mutatedClass);
            File sourceFile = locateSourceFile(projectBaseDir, sourceFileName, mutatedClass, sourceFileCache);

            String originalCode = "";
            String mutatedCode = "";
            int firstLine = lineNumber;
            int lastLine = lineNumber;

            if (sourceFile != null && sourceFile.exists()) {
                AstAnalyzer.CodeContext codeContext = astAnalyzer.extractCodeContext(
                        sourceFile, mutatedClass, mutatedMethod, lineNumber);

                originalCode = codeContext.getOriginalCode();
                // 尝试生成变异后的代码 (简化版，实际需要根据变异类型和描述进行更精确的分析)
                mutatedCode = generateMutatedCode(originalCode, description);

                firstLine = codeContext.getMethodStartLine();
                lastLine = codeContext.getMethodEndLine();
                packageName = codeContext.getPackageName();
            }

            // 创建完整的变异结果
            return MutationResult.builder()
                    .mutatedClass(mutatedClass)
                    .mutatedMethod(mutatedMethod)
                    .lineNumber(lineNumber)
                    .mutator(mutator)
                    .description(description)
                    .detected(detected)
                    .killingTest(killingTest)
                    .status(status)
                    .sourceFile(sourceFileName)
                    .methodDescription(methodDescription)
                    .indexes(indexes)
                    .blocks(blocks)
                    .originalCode(originalCode)
                    .mutatedCode(mutatedCode)
                    .firstLine(firstLine)
                    .lastLine(lastLine)
                    .filename(sourceFile != null ? sourceFile.getName() : sourceFileName)
                    .packageName(packageName)
                    .build();

        } catch (Exception e) {
            logger.warn("Error parsing mutation element", e);
            return null;
        }
    }

    /**
     * 尝试生成变异后的代码（简化实现，仅作示例）
     */
    private String generateMutatedCode(String originalCode, String description) {
        if (originalCode.isEmpty() || description.isEmpty()) {
            return "";
        }

        // 简化的变异代码生成逻辑，实际需根据变异类型进行更精确的转换
        if (description.contains("changed conditional") && description.contains("boundary")) {
            return originalCode
                    .replace("<", "<=")
                    .replace(">", ">=")
                    .replace("<=", "<")
                    .replace(">=", ">");
        } else if (description.contains("negated conditional")) {
            return originalCode
                    .replace("==", "!=")
                    .replace("!=", "==")
                    .replace("<", ">=")
                    .replace(">", "<=")
                    .replace("<=", ">")
                    .replace(">=", "<");
        } else if (description.contains("removed call")) {
            // 尝试找到方法调用并删除
            int openParenIndex = originalCode.indexOf('(');
            int closeParenIndex = findMatchingCloseParen(originalCode, openParenIndex);
            if (openParenIndex > 0 && closeParenIndex > openParenIndex) {
                int methodStart = findMethodStart(originalCode, openParenIndex);
                if (methodStart >= 0) {
                    // 删除整个方法调用，替换为默认值或空
                    String beforeCall = originalCode.substring(0, methodStart);
                    String afterCall = originalCode.substring(closeParenIndex + 1);

                    // 如果是赋值语句，保留变量名并赋默认值
                    if (beforeCall.contains("=")) {
                        return beforeCall + "null" + afterCall;
                    } else {
                        return beforeCall + afterCall;
                    }
                }
            }
        }

        // 默认情况下，添加注释表明变异
        return "/* MUTATED: " + description + " */ " + originalCode;
    }

    /**
     * 查找匹配的右括号
     */
    private int findMatchingCloseParen(String code, int openParenIndex) {
        if (openParenIndex < 0 || openParenIndex >= code.length() || code.charAt(openParenIndex) != '(') {
            return -1;
        }

        int level = 1;
        for (int i = openParenIndex + 1; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '(') {
                level++;
            } else if (c == ')') {
                level--;
                if (level == 0) {
                    return i;
                }
            }
        }

        return -1;
    }

    /**
     * 查找方法调用的起始位置
     */
    private int findMethodStart(String code, int openParenIndex) {
        for (int i = openParenIndex - 1; i >= 0; i--) {
            char c = code.charAt(i);
            if (!Character.isJavaIdentifierPart(c) && c != '.') {
                return i + 1;
            }
        }
        return 0;
    }

    /**
     * 从完全限定名中提取包名
     */
    private String extractPackageName(String className) {
        int lastDotIndex = className.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return className.substring(0, lastDotIndex);
        }
        return "";
    }

    /**
     * 定位源文件
     */
    private File locateSourceFile(File projectBaseDir, String sourceFileName, String className,
                                  Map<String, File> sourceFileCache) {
        // 检查缓存
        if (sourceFileCache.containsKey(className)) {
            return sourceFileCache.get(className);
        }

        // 尝试定位源文件
        File sourceFile = sourceFileLocator.locateSourceFile(projectBaseDir, sourceFileName, className);
        sourceFileCache.put(className, sourceFile);
        return sourceFile;
    }

    /**
     * 解析整数数组，例如 "<index>1</index><index>2</index>"
     */
    private int[] parseIntArray(String content) {
        if (content == null || content.isEmpty()) {
            return new int[0];
        }

        // 简单实现，使用空格分隔
        String[] parts = content.split("\\s+");
        return IntStream.range(0, parts.length)
                .filter(i -> parts[i].matches("\\d+"))
                .map(i -> Integer.parseInt(parts[i]))
                .toArray();
    }

    /**
     * 获取元素中指定标签的文本内容
     */
    private String getElementTextContent(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return "";
    }

    /**
     * 源文件定位器，用于根据类名和源文件名查找源文件
     */
    private static class SourceFileLocator {
        private static final Logger logger = LoggerFactory.getLogger(SourceFileLocator.class);

        /**
         * 定位源文件
         */
        public File locateSourceFile(File projectBaseDir, String sourceFileName, String className) {
            if (projectBaseDir == null || !projectBaseDir.exists() || !projectBaseDir.isDirectory()) {
                logger.warn("Invalid project base directory: {}", projectBaseDir);
                return null;
            }

            // 从类名构建可能的源文件路径
            String packagePath = className.substring(0, Math.max(0, className.lastIndexOf('.')))
                    .replace('.', File.separatorChar);

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

            // 如果未找到，尝试搜索整个项目
            List<File> foundFiles = searchFile(projectBaseDir, sourceFileName);
            if (!foundFiles.isEmpty()) {
                // 返回路径最匹配的文件
                for (File file : foundFiles) {
                    String path = file.getAbsolutePath();
                    if (path.contains(packagePath.replace(File.separatorChar, '/'))) {
                        return file;
                    }
                }
                return foundFiles.get(0);
            }

            logger.warn("Source file not found for class: {}", className);
            return null;
        }

        /**
         * 在目录中搜索文件
         */
        private List<File> searchFile(File directory, String fileName) {
            List<File> result = new ArrayList<>();
            searchFileRecursive(directory, fileName, result);
            return result;
        }

        /**
         * 递归搜索文件
         */
        private void searchFileRecursive(File directory, String fileName, List<File> result) {
            if (!directory.exists() || !directory.isDirectory()) {
                return;
            }

            File[] files = directory.listFiles();
            if (files == null) {
                return;
            }

            for (File file : files) {
                if (file.isDirectory()) {
                    searchFileRecursive(file, fileName, result);
                } else if (file.getName().equals(fileName)) {
                    result.add(file);
                }
            }
        }
    }
}