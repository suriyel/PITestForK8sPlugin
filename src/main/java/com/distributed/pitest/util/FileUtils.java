package com.distributed.pitest.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件操作工具类
 */
public class FileUtils {
    private static final Logger logger = LoggerFactory.getLogger(FileUtils.class);

    /**
     * 递归复制目录
     *
     * @param sourceDir 源目录
     * @param targetDir 目标目录
     * @throws IOException 如果复制过程中发生I/O错误
     */
    public static void copyDirectory(File sourceDir, File targetDir) throws IOException {
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            logger.warn("Source directory does not exist or is not a directory: {}", sourceDir);
            return;
        }

        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        File[] files = sourceDir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            File targetFile = new File(targetDir, file.getName());

            if (file.isDirectory()) {
                copyDirectory(file, targetFile);
            } else {
                Files.copy(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * 查找目录中的所有文件
     *
     * @param directory 目录
     * @param fileExtension 文件扩展名（可选）
     * @return 文件列表
     */
    public static List<File> findFiles(File directory, String fileExtension) {
        List<File> result = new ArrayList<>();

        if (!directory.exists() || !directory.isDirectory()) {
            return result;
        }

        try (Stream<Path> paths = Files.walk(directory.toPath())) {
            result = paths
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .filter(file -> fileExtension == null ||
                            file.getName().endsWith(fileExtension))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Error walking directory: {}", directory, e);
        }

        return result;
    }

    /**
     * 创建临时目录
     *
     * @param prefix 目录前缀
     * @return 临时目录
     */
    public static File createTempDirectory(String prefix) throws IOException {
        Path tempPath = Files.createTempDirectory(prefix);
        File tempDir = tempPath.toFile();
        tempDir.deleteOnExit();
        return tempDir;
    }

    /**
     * 删除目录及其内容
     *
     * @param directory 要删除的目录
     * @return 如果删除成功则返回true
     */
    public static boolean deleteDirectory(File directory) {
        if (!directory.exists()) {
            return true;
        }

        // 先删除内容
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }

        // 然后删除目录本身
        return directory.delete();
    }

    /**
     * 从文件名中提取不带扩展名的文件名
     *
     * @param file 文件
     * @return 不带扩展名的文件名
     */
    public static String getFileNameWithoutExtension(File file) {
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex);
        }
        return fileName;
    }
}