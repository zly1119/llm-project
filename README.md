# 基于微服务架构的大语言模型赋能在线教育问答系统

多模块 Maven 工程（JDK 21 + Spring Boot 3.2 + Spring Cloud Alibaba 2023）。

## 模块说明

| 模块 | 端口 | 职责 |
|------|------|------|
| `common-api` | — | 公共 DTO |
| `user-service` | 8101 | 会话与多轮消息（Redis） |
| `kg-service` | 8102 | Neo4j 知识图谱查询 |
| `qa-service` | 8103 | 普通问答（先 KG 再通义千问），静态页面（主页与学习页） |
| `guide-service` | 8104 | 攻略异步任务（Redis 存状态） |
| `gateway-service` | 8090 | Spring Cloud Gateway 统一入口 |

## 前置依赖

1. **Nacos** 3.1.2（管理端口 `8080`，服务对接地址默认 `127.0.0.1:8848`）
2. **Redis**（默认 `127.0.0.1:6379`）
3. **Neo4j**（与图谱数据一致，`bolt://127.0.0.1:7687`）
4. 环境变量 **`DASHSCOPE_API_KEY`**（通义千问）
5. 环境变量 **`NEO4J_PASSWORD`**（与 Neo4j 实例一致）

可选：启动 **Sentinel Dashboard**（默认配置 `8858`）用于网关/服务限流演示。

## 编译

```bash
mvn clean package -DskipTests
```

## 启动顺序建议

1. Nacos、Redis、Neo4j  
2. `user-service`、`kg-service`  
3. `qa-service`、`guide-service`  
4. `gateway-service`（对外 **http://localhost:8090**）

各模块：

```bash
java -jar user-service/target/user-service-1.0.0-SNAPSHOT.jar
java -jar kg-service/target/kg-service-1.0.0-SNAPSHOT.jar
java -jar qa-service/target/qa-service-1.0.0-SNAPSHOT.jar
java -jar guide-service/target/guide-service-1.0.0-SNAPSHOT.jar
java -jar gateway-service/target/gateway-service-1.0.0-SNAPSHOT.jar
```

## 访问与测试

- 主页：`http://localhost:8090/`  
- 学习页：`http://localhost:8090/app/`  
- 网关 API：  
  - `POST /api/chat`（`application/x-www-form-urlencoded`，字段 `q`）  
  - `POST /api/guide`（同上，字段 `q,goal,level,days`）  
  - `GET /api/guideResult?taskId=...`  
  - `GET /api/kg/search?keyword=...`  

前端已通过相对路径请求上述 `/api/*` 地址（由网关转发）。

## Nacos 配置说明

各服务 `spring.application.name` 已在 `application.yml` 中设置；启动并连接 Nacos 后，在控制台 **服务管理 → 服务列表** 中应看到：`user-service`、`kg-service`、`qa-service`、`guide-service`、`gateway-service`。

可在 Nacos **配置管理** 中下发公共配置（如 Redis、Neo4j、DashScope），并在各服务使用 `spring.config.import=nacos:...`（按需自行启用）。

## Sentinel（可选）

已引入 Sentinel 依赖；在 Sentinel Dashboard 中为网关或资源名配置流控规则。示例：对路由 id `api-chat` 配置 QPS 阈值（具体以控制台资源名为准）。

## 与原单体差异说明

- 原 `Main.java`（`com.sun.net.httpserver`）已移除，逻辑拆至各微服务。  
- 会话与攻略任务由 **Redis** 持久化，替代内存 `Map`。  
- 图谱定义接口 `GET /kg/definition` 返回 `{"found":true/false,...}`，便于 Feign 调用（避免用 HTTP 404 表示未命中）。
