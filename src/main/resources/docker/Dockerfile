FROM openjdk:8-jdk-slim

# 设置工作目录
WORKDIR /app

# 安装必要的系统工具
RUN apt-get update && apt-get install -y \
    wget \
    curl \
    unzip \
    git \
    && rm -rf /var/lib/apt/lists/*

# 安装Maven
ENV MAVEN_VERSION=3.8.5
ENV MAVEN_HOME=/opt/maven
ENV PATH=$MAVEN_HOME/bin:$PATH

RUN wget https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz \
    && tar -xzf apache-maven-$MAVEN_VERSION-bin.tar.gz -C /opt \
    && mv /opt/apache-maven-$MAVEN_VERSION $MAVEN_HOME \
    && rm apache-maven-$MAVEN_VERSION-bin.tar.gz

# 创建Maven本地仓库目录
RUN mkdir -p /root/.m2

# 复制Maven设置文件
COPY maven-settings.xml /root/.m2/settings.xml

# 预下载PITest相关依赖
RUN mkdir -p /tmp/pitest-deps && cd /tmp/pitest-deps && \
    echo '<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">' > pom.xml && \
    echo '  <modelVersion>4.0.0</modelVersion>' >> pom.xml && \
    echo '  <groupId>com.example</groupId>' >> pom.xml && \
    echo '  <artifactId>pitest-deps</artifactId>' >> pom.xml && \
    echo '  <version>1.0.0</version>' >> pom.xml && \
    echo '  <properties>' >> pom.xml && \
    echo '    <maven.compiler.source>1.8</maven.compiler.source>' >> pom.xml && \
    echo '    <maven.compiler.target>1.8</maven.compiler.target>' >> pom.xml && \
    echo '    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>' >> pom.xml && \
    echo '  </properties>' >> pom.xml && \
    echo '  <dependencies>' >> pom.xml && \
    echo '    <dependency>' >> pom.xml && \
    echo '      <groupId>junit</groupId>' >> pom.xml && \
    echo '      <artifactId>junit</artifactId>' >> pom.xml && \
    echo '      <version>4.13.2</version>' >> pom.xml && \
    echo '    </dependency>' >> pom.xml && \
    echo '  </dependencies>' >> pom.xml && \
    echo '  <build>' >> pom.xml && \
    echo '    <plugins>' >> pom.xml && \
    echo '      <plugin>' >> pom.xml && \
    echo '        <groupId>org.pitest</groupId>' >> pom.xml && \
    echo '        <artifactId>pitest-maven</artifactId>' >> pom.xml && \
    echo '        <version>1.9.0</version>' >> pom.xml && \
    echo '      </plugin>' >> pom.xml && \
    echo '    </plugins>' >> pom.xml && \
    echo '  </build>' >> pom.xml && \
    echo '</project>' >> pom.xml && \
    mvn dependency:go-offline && \
    mvn org.pitest:pitest-maven:1.9.0:help -Ddetail=true && \
    cd / && rm -rf /tmp/pitest-deps

# 复制执行脚本
COPY run-pitest.sh /usr/local/bin/run-pitest.sh
RUN chmod +x /usr/local/bin/run-pitest.sh

# 创建结果目录
RUN mkdir -p /tmp/pitest-results /tmp/pitest-config /tmp/project-src

# 设置环境变量
ENV JAVA_OPTS="-Xmx1g -XX:+UseG1GC"
ENV MAVEN_OPTS="-Xmx1g -XX:+UseG1GC"

# 默认工作目录
WORKDIR /tmp/project

# 默认命令
CMD ["/usr/local/bin/run-pitest.sh"]