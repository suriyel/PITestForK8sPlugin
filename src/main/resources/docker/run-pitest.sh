#!/bin/bash

# PITest分布式执行脚本
# 此脚本在Kubernetes Pod中执行，用于运行PITest突变测试
# 新增：支持从ConfigMap重构源码目录结构

set -e

# 默认配置
CONFIG_PATH="${CONFIG_PATH:-/tmp/pitest-config}"
RESULTS_PATH="${RESULTS_PATH:-/tmp/pitest-results}"
SRC_PATH="${SRC_PATH:-/tmp/project-src}"
WORK_DIR="${WORK_DIR:-/tmp/project}"

# 日志函数
log() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] $1"
}

error() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] ERROR: $1" >&2
}

# 检查必要的目录和文件
check_prerequisites() {
    log "Checking prerequisites..."

    if [ ! -d "$CONFIG_PATH" ]; then
        error "Config directory not found: $CONFIG_PATH"
        exit 1
    fi

    if [ ! -f "$CONFIG_PATH/targetClasses.txt" ]; then
        error "Target classes file not found: $CONFIG_PATH/targetClasses.txt"
        exit 1
    fi

    if [ ! -f "$CONFIG_PATH/targetTests.txt" ]; then
        error "Target tests file not found: $CONFIG_PATH/targetTests.txt"
        exit 1
    fi

    # 创建结果目录
    mkdir -p "$RESULTS_PATH"

    log "Prerequisites check completed"
}

# 准备PITest配置
prepare_config() {
    log "Preparing PITest configuration..."

    # 读取目标类列表
    if [ -f "$CONFIG_PATH/targetClasses.txt" ]; then
        TARGET_CLASSES=$(cat "$CONFIG_PATH/targetClasses.txt" | tr '\n' ',' | sed 's/,$//')
        log "Target classes: $TARGET_CLASSES"
    else
        TARGET_CLASSES=""
        log "No target classes specified"
    fi

    # 读取目标测试列表
    if [ -f "$CONFIG_PATH/targetTests.txt" ]; then
        TARGET_TESTS=$(cat "$CONFIG_PATH/targetTests.txt" | tr '\n' ',' | sed 's/,$//')
        log "Target tests: $TARGET_TESTS"
    else
        TARGET_TESTS=""
        log "No target tests specified"
    fi

    # 读取其他属性
    if [ -f "$CONFIG_PATH/pitest.properties" ]; then
        log "Loading PITest properties from $CONFIG_PATH/pitest.properties"
        source "$CONFIG_PATH/pitest.properties"
    fi
}

# 从ConfigMap重构源码目录结构（新增函数）
reconstruct_source_structure() {
    log "Reconstructing source code structure from ConfigMap..."

    # 检查源码ConfigMap挂载点
    if [ ! -d "$SRC_PATH" ] || [ -z "$(ls -A $SRC_PATH 2>/dev/null)" ]; then
        log "No source ConfigMap found or empty, working without source code"
        return 0
    fi

    # 创建工作目录
    mkdir -p "$WORK_DIR"
    cd "$WORK_DIR"

    # 遍历ConfigMap中的所有文件
    for configmap_file in "$SRC_PATH"/*; do
        if [ -f "$configmap_file" ]; then
            filename=$(basename "$configmap_file")

            # 跳过路径信息文件
            if [[ "$filename" == *.path ]]; then
                continue
            fi

            # 检查是否有对应的路径信息文件
            path_file="$configmap_file.path"
            if [ -f "$path_file" ]; then
                # 读取原始路径
                original_path=$(cat "$path_file")
                log "Restoring file: $filename -> $original_path"

                # 创建目录结构
                target_dir=$(dirname "$original_path")
                mkdir -p "$target_dir"

                # 复制文件到正确位置
                cp "$configmap_file" "$original_path"
            else
                # 如果没有路径信息，尝试从文件名推断
                if [[ "$filename" == "pom.xml" ]]; then
                    log "Restoring pom.xml to project root"
                    cp "$configmap_file" "pom.xml"
                elif [[ "$filename" == *"src_main_java"* ]]; then
                    # 重构Java源文件路径
                    reconstruct_java_file "$configmap_file" "$filename" "src/main/java"
                elif [[ "$filename" == *"src_test_java"* ]]; then
                    # 重构Java测试文件路径
                    reconstruct_java_file "$configmap_file" "$filename" "src/test/java"
                elif [[ "$filename" == *"src_main_resources"* ]]; then
                    # 重构资源文件路径
                    reconstruct_resource_file "$configmap_file" "$filename" "src/main/resources"
                elif [[ "$filename" == *"src_test_resources"* ]]; then
                    # 重构测试资源文件路径
                    reconstruct_resource_file "$configmap_file" "$filename" "src/test/resources"
                else
                    log "Unknown file type: $filename, placing in root directory"
                    cp "$configmap_file" "$filename"
                fi
            fi
        fi
    done

    log "Source code structure reconstruction completed"
}

# 重构Java文件（新增函数）
reconstruct_java_file() {
    local configmap_file="$1"
    local filename="$2"
    local base_path="$3"

    # 从文件名中提取Java类路径
    # 例如：src_main_java_com_example_MyClass.java -> com/example/MyClass.java
    local java_path=$(echo "$filename" | sed "s/^${base_path//\//_}_//g")
    java_path=$(echo "$java_path" | sed 's/_/\//g')

    local target_path="$base_path/$java_path"
    local target_dir=$(dirname "$target_path")

    log "Reconstructing Java file: $filename -> $target_path"
    mkdir -p "$target_dir"
    cp "$configmap_file" "$target_path"
}

# 重构资源文件（新增函数）
reconstruct_resource_file() {
    local configmap_file="$1"
    local filename="$2"
    local base_path="$3"

    # 从文件名中提取资源路径
    local resource_path=$(echo "$filename" | sed "s/^${base_path//\//_}_//g")
    resource_path=$(echo "$resource_path" | sed 's/_/\//g')

    local target_path="$base_path/$resource_path"
    local target_dir=$(dirname "$target_path")

    log "Reconstructing resource file: $filename -> $target_path"
    mkdir -p "$target_dir"
    cp "$configmap_file" "$target_path"
}

# 准备项目源码（修改后的函数）
prepare_source() {
    log "Preparing project source code..."

    # 首先尝试从ConfigMap重构源码结构
    reconstruct_source_structure

    # 确保在工作目录中
    cd "$WORK_DIR"

    # 检查是否有pom.xml文件
    if [ ! -f "$WORK_DIR/pom.xml" ]; then
        log "No pom.xml found in work directory, creating minimal pom.xml"
        create_minimal_pom
    else
        log "Found existing pom.xml in work directory"
    fi

    # 显示重构后的目录结构
    log "Project structure after reconstruction:"
    find "$WORK_DIR" -type f -name "*.java" -o -name "*.xml" -o -name "*.properties" | head -20 | while read file; do
        log "  $file"
    done
}

# 创建最小的pom.xml文件
create_minimal_pom() {
    cat > "$WORK_DIR/pom.xml" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.distributed.pitest</groupId>
    <artifactId>mutation-testing</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
            <version>4.13.2</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.8.1</version>
                <configuration>
                    <source>1.8</source>
                    <target>1.8</target>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.pitest</groupId>
                <artifactId>pitest-maven</artifactId>
                <version>1.9.0</version>
            </plugin>
        </plugins>
    </build>
</project>
EOF
}

# 执行PITest
run_pitest() {
    log "Starting PITest mutation testing..."

    cd "$WORK_DIR"

    # 构建Maven命令
    MVN_CMD="mvn org.pitest:pitest-maven:mutationCoverage"

    # 添加目标类参数
    if [ -n "$TARGET_CLASSES" ]; then
        MVN_CMD="$MVN_CMD -DtargetClasses=\"$TARGET_CLASSES\""
    fi

    # 添加目标测试参数
    if [ -n "$TARGET_TESTS" ]; then
        MVN_CMD="$MVN_CMD -DtargetTests=\"$TARGET_TESTS\""
    fi

    # 添加输出格式和目录
    MVN_CMD="$MVN_CMD -DoutputFormats=XML,HTML"
    MVN_CMD="$MVN_CMD -DreportDir=\"$RESULTS_PATH\""
    MVN_CMD="$MVN_CMD -DtimestampedReports=false"

    # 添加其他配置
    MVN_CMD="$MVN_CMD -DthreadCount=4"
    MVN_CMD="$MVN_CMD -DtimeoutConstant=10000"
    MVN_CMD="$MVN_CMD -DtimeoutFactor=1.25"
    MVN_CMD="$MVN_CMD -DmaxMutationsPerClass=50"

    # 如果重构了源码，包含源码信息
    if [ -d "src" ]; then
        MVN_CMD="$MVN_CMD -DincludeSource=true"
        log "Source code detected, enabling source inclusion"
    fi

    # 添加环境变量中的其他参数
    if [ -n "$PITEST_EXCLUDES" ]; then
        MVN_CMD="$MVN_CMD -DexcludedClasses=\"$PITEST_EXCLUDES\""
    fi

    if [ -n "$PITEST_MUTATORS" ]; then
        MVN_CMD="$MVN_CMD -Dmutators=\"$PITEST_MUTATORS\""
    fi

    if [ -n "$PITEST_VERBOSE" ] && [ "$PITEST_VERBOSE" = "true" ]; then
        MVN_CMD="$MVN_CMD -Dverbose=true"
    fi

    log "Executing: $MVN_CMD"

    # 执行命令并捕获退出状态
    if eval "$MVN_CMD"; then
        log "PITest execution completed successfully"
        EXIT_CODE=0
    else
        error "PITest execution failed"
        EXIT_CODE=1
    fi

    # 检查结果文件
    if [ -f "$RESULTS_PATH/mutations.xml" ]; then
        MUTATION_COUNT=$(grep -c "<mutation" "$RESULTS_PATH/mutations.xml" 2>/dev/null || echo "0")
        log "Generated $MUTATION_COUNT mutations"
    else
        log "Warning: No mutations.xml file generated"
    fi

    return $EXIT_CODE
}

# 清理和总结（增强版本）
cleanup_and_summary() {
    log "Generating execution summary..."

    # 生成执行摘要
    cat > "$RESULTS_PATH/execution-summary.txt" << EOF
PITest Distributed Execution Summary
====================================
Execution Time: $(date)
Work Directory: $WORK_DIR
Results Directory: $RESULTS_PATH
Source Path: $SRC_PATH
Target Classes: $TARGET_CLASSES
Target Tests: $TARGET_TESTS
Exit Code: $EXIT_CODE

Source Structure Reconstructed:
$(find "$WORK_DIR" -type f -name "*.java" | wc -l) Java files
$(find "$WORK_DIR" -type f -name "*.xml" | wc -l) XML files
$(find "$WORK_DIR" -type f -name "*.properties" | wc -l) Properties files

Generated Files:
$(ls -la "$RESULTS_PATH" 2>/dev/null || echo "No files generated")

ConfigMap Source Files:
$(ls -la "$SRC_PATH" 2>/dev/null | wc -l) files in source ConfigMap
EOF

    log "Execution summary saved to $RESULTS_PATH/execution-summary.txt"

    # 显示结果目录内容
    if [ -d "$RESULTS_PATH" ]; then
        log "Results directory contents:"
        ls -la "$RESULTS_PATH"
    fi

    # 保存源码重构日志（用于调试）
    if [ -d "$WORK_DIR" ]; then
        find "$WORK_DIR" -type f > "$RESULTS_PATH/reconstructed-files.txt" 2>/dev/null || true
        log "Reconstructed files list saved to $RESULTS_PATH/reconstructed-files.txt"
    fi
}

# 主函数
main() {
    log "Starting PITest distributed execution in Kubernetes Pod with ConfigMap source support"
    log "Pod: ${HOSTNAME:-unknown}"
    log "Java Version: $(java -version 2>&1 | head -1)"
    log "Maven Version: $(mvn -version 2>&1 | head -1)"

    # 执行步骤
    check_prerequisites
    prepare_config
    prepare_source

    # 执行PITest
    if run_pitest; then
        log "PITest execution completed successfully"
        EXIT_CODE=0
    else
        error "PITest execution failed"
        EXIT_CODE=1
    fi

    # 清理和总结
    cleanup_and_summary

    log "PITest distributed execution finished with exit code: $EXIT_CODE"
    exit $EXIT_CODE
}

# 如果脚本被直接执行，运行主函数
if [ "${BASH_SOURCE[0]}" == "${0}" ]; then
    main "$@"
fi