#!/bin/bash

# 分布式PITest Docker镜像构建脚本
# 此脚本用于CI/CD环境中构建PITest执行镜像

set -e

# 默认配置
DEFAULT_REGISTRY="localhost:5000"
DEFAULT_IMAGE_NAME="distributed-pitest"
DEFAULT_IMAGE_TAG="latest"
DEFAULT_DOCKERFILE="Dockerfile"
DEFAULT_CONTEXT="."

# 脚本参数
DOCKER_REGISTRY="${DOCKER_REGISTRY:-$DEFAULT_REGISTRY}"
IMAGE_NAME="${IMAGE_NAME:-$DEFAULT_IMAGE_NAME}"
IMAGE_TAG="${IMAGE_TAG:-$DEFAULT_IMAGE_TAG}"
DOCKERFILE="${DOCKERFILE:-$DEFAULT_DOCKERFILE}"
BUILD_CONTEXT="${BUILD_CONTEXT:-$DEFAULT_CONTEXT}"
PUSH_IMAGE="${PUSH_IMAGE:-true}"
NO_CACHE="${NO_CACHE:-false}"
MAVEN_VERSION="${MAVEN_VERSION:-3.8.5}"
PITEST_VERSION="${PITEST_VERSION:-1.9.0}"

# 完整镜像名称
FULL_IMAGE_NAME="${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"

# 日志函数
log() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] $1"
}

error() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] ERROR: $1" >&2
}

# 显示使用说明
show_usage() {
    cat << EOF
Usage: $0 [OPTIONS]

构建分布式PITest Docker镜像的脚本

Options:
  -r, --registry REGISTRY     Docker镜像仓库地址 (默认: $DEFAULT_REGISTRY)
  -n, --name NAME             镜像名称 (默认: $DEFAULT_IMAGE_NAME)
  -t, --tag TAG               镜像标签 (默认: $DEFAULT_IMAGE_TAG)
  -f, --dockerfile FILE       Dockerfile路径 (默认: $DEFAULT_DOCKERFILE)
  -c, --context PATH          构建上下文路径 (默认: $DEFAULT_CONTEXT)
  --no-push                   不推送镜像到仓库
  --no-cache                  构建时不使用缓存
  --maven-version VERSION     Maven版本 (默认: $MAVEN_VERSION)
  --pitest-version VERSION    PITest版本 (默认: $PITEST_VERSION)
  -h, --help                  显示此帮助信息

环境变量:
  DOCKER_REGISTRY             镜像仓库地址
  IMAGE_NAME                  镜像名称
  IMAGE_TAG                   镜像标签
  PUSH_IMAGE                  是否推送镜像 (true/false)
  NO_CACHE                    是否使用缓存 (true/false)
  MAVEN_VERSION               Maven版本
  PITEST_VERSION              PITest版本

Examples:
  $0                                           # 使用默认配置构建
  $0 -r myregistry.com -t v1.0.0              # 指定仓库和标签
  $0 --no-push --no-cache                     # 本地构建，不使用缓存
EOF
}

# 解析命令行参数
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -r|--registry)
                DOCKER_REGISTRY="$2"
                shift 2
                ;;
            -n|--name)
                IMAGE_NAME="$2"
                shift 2
                ;;
            -t|--tag)
                IMAGE_TAG="$2"
                shift 2
                ;;
            -f|--dockerfile)
                DOCKERFILE="$2"
                shift 2
                ;;
            -c|--context)
                BUILD_CONTEXT="$2"
                shift 2
                ;;
            --no-push)
                PUSH_IMAGE="false"
                shift
                ;;
            --no-cache)
                NO_CACHE="true"
                shift
                ;;
            --maven-version)
                MAVEN_VERSION="$2"
                shift 2
                ;;
            --pitest-version)
                PITEST_VERSION="$2"
                shift 2
                ;;
            -h|--help)
                show_usage
                exit 0
                ;;
            *)
                error "未知参数: $1"
                show_usage
                exit 1
                ;;
        esac
    done

    # 更新完整镜像名称
    FULL_IMAGE_NAME="${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"
}

# 检查依赖
check_dependencies() {
    log "检查构建依赖..."

    # 检查Docker
    if ! command -v docker &> /dev/null; then
        error "Docker未安装或不在PATH中"
        exit 1
    fi

    # 检查Docker Compose
    if ! command -v docker-compose &> /dev/null; then
        error "Docker Compose未安装或不在PATH中"
        exit 1
    fi

    # 检查Dockerfile
    if [ ! -f "$BUILD_CONTEXT/$DOCKERFILE" ]; then
        error "Dockerfile未找到: $BUILD_CONTEXT/$DOCKERFILE"
        exit 1
    fi

    # 检查必要的资源文件
    local required_files=(
        "$BUILD_CONTEXT/run-pitest.sh"
        "$BUILD_CONTEXT/maven-settings.xml"
    )

    for file in "${required_files[@]}"; do
        if [ ! -f "$file" ]; then
            error "必需的文件未找到: $file"
            exit 1
        fi
    done

    log "依赖检查完成"
}

# 显示构建信息
show_build_info() {
    log "构建信息:"
    log "  镜像仓库: $DOCKER_REGISTRY"
    log "  镜像名称: $IMAGE_NAME"
    log "  镜像标签: $IMAGE_TAG"
    log "  完整镜像名: $FULL_IMAGE_NAME"
    log "  Dockerfile: $DOCKERFILE"
    log "  构建上下文: $BUILD_CONTEXT"
    log "  是否推送: $PUSH_IMAGE"
    log "  使用缓存: $([ "$NO_CACHE" = "true" ] && echo "否" || echo "是")"
    log "  Maven版本: $MAVEN_VERSION"
    log "  PITest版本: $PITEST_VERSION"
}

# 构建Docker镜像
build_image() {
    log "开始构建Docker镜像..."

    # 构建Docker命令
    local build_cmd="docker build"

    # 添加构建参数
    build_cmd="$build_cmd --build-arg MAVEN_VERSION=$MAVEN_VERSION"
    build_cmd="$build_cmd --build-arg PITEST_VERSION=$PITEST_VERSION"

    # 添加标签
    build_cmd="$build_cmd -t $FULL_IMAGE_NAME"

    # 如果不使用缓存
    if [ "$NO_CACHE" = "true" ]; then
        build_cmd="$build_cmd --no-cache"
    fi

    # 添加Dockerfile和上下文
    build_cmd="$build_cmd -f $DOCKERFILE $BUILD_CONTEXT"

    log "执行构建命令: $build_cmd"

    # 执行构建
    if eval "$build_cmd"; then
        log "镜像构建成功: $FULL_IMAGE_NAME"
    else
        error "镜像构建失败"
        exit 1
    fi
}

# 测试镜像
test_image() {
    log "测试构建的镜像..."

    # 运行基本测试
    if docker run --rm "$FULL_IMAGE_NAME" java -version > /dev/null 2>&1; then
        log "Java版本检查通过"
    else
        error "Java版本检查失败"
        exit 1
    fi

    if docker run --rm "$FULL_IMAGE_NAME" mvn -version > /dev/null 2>&1; then
        log "Maven版本检查通过"
    else
        error "Maven版本检查失败"
        exit 1
    fi

    if docker run --rm "$FULL_IMAGE_NAME" which run-pitest.sh > /dev/null 2>&1; then
        log "PITest脚本检查通过"
    else
        error "PITest脚本检查失败"
        exit 1
    fi

    log "镜像测试完成"
}

# 推送镜像
push_image() {
    if [ "$PUSH_IMAGE" != "true" ]; then
        log "跳过镜像推送"
        return 0
    fi

    log "推送镜像到仓库..."

    if docker push "$FULL_IMAGE_NAME"; then
        log "镜像推送成功: $FULL_IMAGE_NAME"
    else
        error "镜像推送失败"
        exit 1
    fi
}

# 清理临时资源
cleanup() {
    log "清理临时资源..."

    # 清理无用的镜像和容器（可选）
    if [ "${CLEANUP_AFTER_BUILD:-false}" = "true" ]; then
        docker system prune -f > /dev/null 2>&1 || true
        log "临时资源清理完成"
    fi
}

# 主函数
main() {
    log "开始分布式PITest Docker镜像构建过程"

    # 解析参数
    parse_args "$@"

    # 显示构建信息
    show_build_info

    # 检查依赖
    check_dependencies

    # 构建镜像
    build_image

    # 测试镜像
    test_image

    # 推送镜像
    push_image

    # 清理
    cleanup

    log "Docker镜像构建过程完成"
    log "镜像已准备就绪: $FULL_IMAGE_NAME"
}

# 错误处理
trap 'error "构建过程中发生错误，退出状态: $?"' ERR

# 如果脚本被直接执行，运行主函数
if [ "${BASH_SOURCE[0]}" == "${0}" ]; then
    main "$@"
fi