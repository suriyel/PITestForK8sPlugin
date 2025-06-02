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
 * 使用变异分析器的增强XML解析器
 */
public class EnhancedXmlReportParserWithAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedXmlReportParserWithAnalyzer.class);
    private final AstAnalyzer astAnalyzer;
    private final SourceFileLocator sourceFileLocator;
    private final MutationAnalyzer mutationAnalyzer;

    public EnhancedXmlReportParserWithAnalyzer() {
        this.astAnalyzer = new AstAnalyzer();
        this.sourceFileLocator = new SourceFileLocator();
        this.mutationAnalyzer = new MutationAnalyzer();
    }

    /**
     * 解析Pitest突变测试结果XML文件，并附加源代码和变异分析信息
     *
     * @param xmlFile XML文件
     * @param projectBaseDir 项目基础目录，用于定位源文件
     * @return 突变测试结果列表
     * @throws IOException 如果文件读取错误
     */
    public List<MutationResult> parseMutationXml(File xmlFile, File projectBaseDir) throws IOException {
        logger.info("Parsing mutations XML file with analyzer: {}", xmlFile.getAbsolutePath());
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

            logger.info("Successfully parsed {} mutation results with analyzer", results.size());

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

                // 使用变异分析器生成变异后的代码
                if (originalCode != null && !originalCode.isEmpty()) {
                    mutatedCode = mutationAnalyzer.generateMutatedCode(originalCode, description, mutator);
                }

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