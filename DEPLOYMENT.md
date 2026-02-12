# 测评报告系统 - 阿里云部署方案

## 1. 环境准备

### 1.1 阿里云服务器配置
- **实例类型**：ecs.t5-lc2m1.nano（或根据实际需求选择）
- **操作系统**：CentOS 7.9 64位
- **CPU**：1核
- **内存**：1GB
- **存储**：40GB ESSD云盘
- **带宽**：1Mbps

### 1.2 必要软件安装
在阿里云服务器上执行以下命令安装必要软件：

```bash
# 更新系统
sudo yum update -y

# 安装Java 11
sudo yum install -y java-11-openjdk-devel

# 验证Java安装
java -version

# 安装wget
sudo yum install -y wget
```

## 2. 项目构建

在本地开发环境执行以下命令构建项目：

```bash
# 进入项目目录
cd /path/to/report-system

# 使用Gradle构建项目
./gradlew clean bootJar -x test
```

构建完成后，会在 `build/libs` 目录生成 `report-system-0.0.1-SNAPSHOT.jar` 文件。

## 3. 部署步骤

### 3.1 上传jar包到阿里云服务器

使用scp命令将构建好的jar包上传到阿里云服务器：

```bash
scp build/libs/report-system-0.0.1-SNAPSHOT.jar root@your-server-ip:/opt/
```

### 3.2 启动应用

在阿里云服务器上执行以下命令启动应用：

```bash
# 进入opt目录
cd /opt

# 启动应用
nohup java -jar report-system-0.0.1-SNAPSHOT.jar > app.log 2>&1 &

# 查看启动日志
tail -f app.log
```

### 3.3 配置安全组

在阿里云控制台配置安全组规则，开放8080端口：

1. 登录阿里云控制台
2. 进入ECS实例管理页面
3. 点击实例对应的安全组
4. 点击「配置规则」
5. 点击「添加安全组规则」
6. 配置以下信息：
   - 规则方向：入方向
   - 授权策略：允许
   - 协议类型：TCP
   - 端口范围：8080/8080
   - 授权对象：0.0.0.0/0
   - 描述：测评报告系统端口

### 3.4 访问应用

在浏览器中输入以下地址访问应用：

```
http://your-server-ip:8080/
```

## 4. 监控与维护

### 4.1 查看应用状态

```bash
# 查看应用进程
ps -ef | grep java

# 查看应用日志
cat /opt/app.log
```

### 4.2 重启应用

```bash
# 停止应用
pkill -f report-system

# 启动应用
nohup java -jar /opt/report-system-0.0.1-SNAPSHOT.jar > /opt/app.log 2>&1 &
```

### 4.3 设置开机自启

创建systemd服务文件：

```bash
sudo vi /etc/systemd/system/report-system.service
```

添加以下内容：

```ini
[Unit]
Description=Report System
After=network.target

[Service]
Type=simple
ExecStart=/usr/bin/java -jar /opt/report-system-0.0.1-SNAPSHOT.jar
WorkingDirectory=/opt
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

启用服务：

```bash
sudo systemctl daemon-reload
sudo systemctl enable report-system
sudo systemctl start report-system
```

## 5. 后续迭代建议

1. **数据库集成**：添加MySQL或PostgreSQL数据库，存储测评报告数据
2. **用户认证**：实现用户登录和权限管理
3. **报告管理**：添加报告上传、编辑、删除功能
4. **数据可视化**：集成Chart.js或ECharts，展示测评数据
5. **API接口**：提供RESTful API，支持第三方系统集成
6. **容器化部署**：使用Docker容器化部署，简化运维
7. **CI/CD**：配置持续集成和持续部署流程

## 6. 故障排查

### 6.1 常见问题

| 问题 | 可能原因 | 解决方案 |
|------|---------|--------|
| 无法访问应用 | 安全组未开放8080端口 | 配置安全组规则，开放8080端口 |
| 应用启动失败 | Java版本不兼容 | 确保使用Java 11 |
| 应用运行缓慢 | 服务器配置过低 | 升级服务器配置 |
| 日志报错 | 端口被占用 | 检查端口占用情况，使用其他端口 |

### 6.2 查看详细日志

```bash
# 查看应用详细日志
cat /opt/app.log

# 查看系统日志
sudo journalctl -u report-system
```
