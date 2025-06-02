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
import java.util.List;

/**
 * 用于解析Pitest XML报告的工具类
 */
public class XmlReportParser {
    private static final Logger logger = LoggerFactory.getLogger(XmlReportParser.class);

    /**
     * 解析Pitest突变测试结果XML文件
     *
     * @param xmlFile XML文件
     * @return 突变测试结果列表
     * @throws IOException 如果文件读取错误
     */
    public List<MutationResult> parseMutationXml(File xmlFile) throws IOException {
        logger.info("Parsing mutations XML file: {}", xmlFile.getAbsolutePath());
        List<MutationResult> results = new ArrayList<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(xmlFile);
            document.getDocumentElement().normalize();

            NodeList mutationNodes = document.getElementsByTagName("mutation");
            logger.info("Found {} mutation elements in XML", mutationNodes.getLength());

            for (int i = 0; i < mutationNodes.getLength(); i++) {
                Node node = mutationNodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;

                    // 解析突变属性
                    boolean detected = Boolean.parseBoolean(element.getAttribute("detected"));
                    String status = element.getAttribute("status");

                    // 解析子元素
                    String mutatedClass = getElementTextContent(element, "mutatedClass");
                    String mutatedMethod = getElementTextContent(element, "mutatedMethod");
                    int lineNumber = Integer.parseInt(getElementTextContent(element, "lineNumber"));
                    String mutator = getElementTextContent(element, "mutator");
                    String description = getElementTextContent(element, "description");
                    String killingTest = getElementTextContent(element, "killingTest");

                    // 创建突变结果对象
                    MutationResult result = MutationResult.builder()
                            .mutatedClass(mutatedClass)
                            .mutatedMethod(mutatedMethod)
                            .lineNumber(lineNumber)
                            .mutator(mutator)
                            .description(description)
                            .detected(detected)
                            .killingTest(killingTest)
                            .status(status)
                            .build();

                    results.add(result);
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
     * 获取元素中指定标签的文本内容
     *
     * @param element 父元素
     * @param tagName 标签名
     * @return 文本内容，如果不存在则返回空字符串
     */
    private String getElementTextContent(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return "";
    }
}