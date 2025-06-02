# 使用本地镜像运行集成测试

为了避免从远程仓库拉取镜像，您可以配置测试使用本地镜像。这在网络受限环境或使用自定义镜像时特别有用。

## 使用本地镜像的方法

### 1. 使用已存在于节点上的镜像

如果您的Kubernetes节点上已经有了所需的镜像，您可以通过以下方式使用它：

```bash
# 在minikube或kind环境中使用本地镜像
mvn verify -P integration-test -Dkubernetes.local.image=maven:3.8.5-openjdk-8
```

系统会自动设置`imagePullPolicy`为`Never`，这样Kubernetes将使用节点上已存在的镜像，而不会尝试从远程仓库拉取。

### 2. 使用本地镜像仓库

如果您有本地镜像仓库，可以指定完整的镜像路径：

```bash
# 使用本地仓库中的镜像
mvn verify -P integration-test -Dkubernetes.local.image=localhost:5000/maven:3.8.5-openjdk-8
```

### 3. 加载镜像到本地Kubernetes环境

对于Minikube或Kind环境，您可以先将Docker镜像加载到环境中，然后运行测试：

```bash
# 对于Minikube
minikube image load maven:3.8.5-openjdk-8

# 对于Kind
kind load docker-image maven:3.8.5-openjdk-8 --name my-cluster

# 然后运行测试，指定使用本地镜像
mvn verify -P integration-test -Dkubernetes.local.image=maven:3.8.5-openjdk-8
```

## 创建自定义测试镜像

您可以创建包含所有所需依赖的自定义Docker镜像，以加速测试执行：

```dockerfile
FROM maven:3.8.5-openjdk-8

# 预安装Pitest依赖
RUN mvn org.pitest:pitest-maven:1.9.0:help -Ddetail=true

# 预安装常用依赖
RUN mkdir -p /tmp/deps && \
    cd /tmp/deps && \
    echo "<project><modelVersion>4.0.0</modelVersion><groupId>com.example</groupId><artifactId>deps</artifactId><version>1.0</version><dependencies><dependency><groupId>junit</groupId><artifactId>junit</artifactId><version>4.13.2</version></dependency></dependencies></project>" > pom.xml && \
    mvn dependency:go-offline && \
    rm -rf /tmp/deps

WORKDIR /app
```

构建并推送到本地仓库：

```bash
docker build -t localhost:5000/maven-pitest:latest .
docker push localhost:5000/maven-pitest:latest

# 使用自定义镜像运行测试
mvn verify -P integration-test -Dkubernetes.local.image=localhost:5000/maven-pitest:latest
```

## 注意事项

1. 确保镜像包含运行Pitest所需的所有工具，特别是Maven和Java 8
2. 使用本地镜像时，imagePullPolicy会自动设置为"Never"
3. 如果使用私有仓库，确保Kubernetes集群已配置了访问权限
4. 本地镜像必须与远程镜像有相同的功能，特别是包含Maven和运行pitest所需的工具