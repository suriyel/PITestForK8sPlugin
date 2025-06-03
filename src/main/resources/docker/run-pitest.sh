#!/bin/bash

# PITest分布式执行脚本
# 此脚本在Kubernetes Pod中执行，用于运行PITest突变测试

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

# 准备项目源码
prepare_source() {
    log "Preparing project source code..."

    # 如果有源码目录，复制到工作目录
    if [ -d "$SRC_PATH" ] && [ "$(ls -A $SRC_PATH)" ]; then
        log "Copying source code from $SRC_PATH to $WORK_DIR"
        cp -r "$SRC_PATH"/* "$WORK_DIR"/ 2>/dev/null || true
    else
        log "No source code directory found or empty, working in current directory"
        cd "$WORK_DIR"
    fi

    # 检查是否有pom.xml文件
    if [ ! -f "$WORK_DIR/pom.xml" ]; then
        log "No pom.xml found in work directory, creating minimal pom.xml"
        create_minimal_pom
    fi
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

    # 如果有源码，包含源码信息
    if [ -d "$SRC_PATH" ] && [ "$(ls -A $SRC_PATH)" ]; then
        MVN_CMD="$MVN_CMD -DincludeSource=true"
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

# 清理和总结
cleanup_and_summary() {
    log "Generating execution summary..."

    # 生成执行摘要
    cat > "$RESULTS_PATH/execution-summary.txt" << EOF
PITest Distributed Execution Summary
====================================
Execution Time: $(date)
Work Directory: $WORK_DIR
Results Directory: $RESULTS_PATH
Target Classes: $TARGET_CLASSES
Target Tests: $TARGET_TESTS
Exit Code: $EXIT_CODE

Generated Files:
$(ls -la "$RESULTS_PATH" 2>/dev/null || echo "No files generated")
EOF

    log "Execution summary saved to $RESULTS_PATH/execution-summary.txt"

    # 显示结果目录内容
    if [ -d "$RESULTS_PATH" ]; then
        log "Results directory contents:"
        ls -la "$RESULTS_PATH"
    fi
}

# 主函数
main() {
    log "Starting PITest distributed execution in Kubernetes Pod"
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