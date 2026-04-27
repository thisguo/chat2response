# Chat2Response

将 OpenAI Chat Completions API 转换为 OpenAI Responses API 的网关服务。

通过管理后台配置上游 BaseURL / APIKEY / 模型，自动生成一个唯一的 `targetModel`，对外暴露兼容 Responses API 的 `/v1/responses` 接口，支持流式 SSE。

## 架构概览

```
客户端  ──►  Nginx (端口 80)
               ├── /            → 前端静态资源（Vue 3 + Vite）
               ├── /admin/*     → 后端管理接口（Spring Boot）
               └── /v1/*        → 网关接口（Responses API）
```

| 组件 | 技术栈 |
|------|--------|
| 后端 | Spring Boot 4.0.6 / JDK 17 / Maven / MyBatis / H2 |
| 前端 | Vue 3 / Vite 8 |
| 网关 | Nginx 反向代理 |

---

## 一、从 Docker Hub 一键运行（推荐）

直接拉取官方镜像运行，无需本地构建环境。

### 拉取镜像

```bash
docker pull guoshengkai/chat2response:latest
```

### 运行容器

```bash
docker run -d \
  --name chat2response \
  -p 8080:80 \
  -e APP_USERNAME=myadmin \
  -e APP_PASSWORD='MyStrongPassword123' \
  -e JAVA_OPTS='-Xms256m -Xmx512m' \
  guoshengkai/chat2response:latest
```

**环境变量说明：**

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `APP_USERNAME` | 管理后台用户名 | `admin` |
| `APP_PASSWORD` | 管理后台密码 | `admin123456` |
| `JAVA_OPTS` | JVM 启动参数（可选） | 空 |

运行成功后访问：

- 前端页面：`http://localhost:8080`
- 管理后台 API：`http://localhost:8080/admin/`
- 网关接口：`http://localhost:8080/v1/responses`

---

## 二、使用 Dockerfile 自行构建

适用于需要修改代码后重新打包的场景。

### 环境要求

- Docker（支持 BuildKit / buildx）

### 构建镜像

在项目根目录执行：

```bash
docker build -t guoshengkai/chat2response:latest .
```

构建过程会自动完成：
1. Maven 编译后端 JAR（跳过测试）
2. Node 编译前端静态文件
3. 将产物打包进 OpenJDK + Nginx 运行时镜像

### 运行

```bash
docker run -d \
  --name chat2response \
  -p 8080:80 \
  guoshengkai/chat2response:latest
```

### 构建并推送多平台镜像

```bash
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t guoshengkai/chat2response:latest \
  --push \
  .
```

---

## 三、手动启动（开发调试）

适用于本地开发调试，前后端分离启动。

### 环境要求

- JDK 17+
- Maven 3.9+
- Node.js 20+
- MySQL 8+（或使用 H2 内存库）

### 3.1 配置数据库

编辑 `src/main/resources/application.yaml`，配置数据源：

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://<host>:<port>/<database>?useSSL=false&serverTimezone=UTC
    username: <username>
    password: <password>
```

也可切换为 H2 内存数据库进行本地测试（需修改驱动配置）。

默认管理账号密码在同文件中：

```yaml
management:
  user:
    username: admin
    password: admin123456
```

### 3.2 启动后端

```bash
# 编译并运行
./mvnw spring-boot:run

# 或先打包再运行
./mvnw clean package -DskipTests
java -jar target/chat2response-0.0.1-SNAPSHOT.jar
```

后端默认监听 `http://localhost:8080`。

### 3.3 启动前端

```bash
cd webui

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端开发服务器默认监听 `http://localhost:5173`，已配置代理将 `/admin/*` 和 `/v1/*` 请求转发到后端 `http://localhost:8080`。

### 3.4 编译前端生产产物

```bash
cd webui
npm install
npm run build
```

编译产物输出到 `webui/dist/`，部署时将其配置为 Nginx 静态目录即可。

---

## 四、快速使用

### 登录管理后台

```bash
curl -X POST 'http://localhost:8080/admin/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"username": "admin", "password": "admin123456"}'
```

### 创建转换项目

```bash
curl -X POST 'http://localhost:8080/admin/projects' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <LOGIN_TOKEN>' \
  -d '{
    "baseUrl": "https://api.openai.com",
    "apiKey": "sk-xxxx",
    "sourceModel": "gpt-4o-mini"
  }'
```

返回中会包含自动生成的 `targetModel`（如 `rsp-gpt-4o-mini-a1b2c3d4`）。

### 调用 Responses API

**非流式：**

```bash
curl -X POST 'http://localhost:8080/v1/responses' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer sk-xxxx' \
  -d '{
    "model": "rsp-gpt-4o-mini-a1b2c3d4",
    "input": "你好",
    "stream": false
  }'
```

**流式 SSE：**

```bash
curl -N -X POST 'http://localhost:8080/v1/responses' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer sk-xxxx' \
  -d '{
    "model": "rsp-gpt-4o-mini-a1b2c3d4",
    "input": "你好",
    "stream": true
  }'
```

---

## API 文档

详细接口文档请参阅 [API.md](./API.md)。
