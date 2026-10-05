# 知库 ZhiKu

> 企业级 AI 知识库平台：文档上传 → RAG 检索问答 → 引用溯源，支持私有化部署与企业微信/钉钉接入。

![JDK](https://img.shields.io/badge/JDK-21-blue) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-green) ![License](https://img.shields.io/badge/License-MIT-yellow)

**本项目基于 [RuoYi-Vue-Plus 6.X](https://gitee.com/dromara/RuoYi-Vue-Plus)（MIT 协议）二次开发**，感谢原作者 [疯狂的狮子Li](https://gitee.com/lionli) 与 Dromara 开源社区的贡献。衍生关系与第三方组件清单见 [NOTICE.md](./NOTICE.md)。

配套前端：[zhiku-ui](https://github.com/MoMingMq/zhiku-ui)

## 平台特性

- **企业级权限**：RBAC 菜单/按钮权限 + 数据权限（`@DataPermission`）+ Sa-Token JWT 认证
- **系统管理**：用户/角色/菜单/部门/岗位/字典/参数/通知公告/操作与登录日志
- **代码生成**：表结构导入 → Java/Vue/SQL（含四方言菜单脚本）一键生成
- **工作流**：WarmFlow 审批流 + LiteFlow 规则流
- **AI 能力**：Spring AI MCP（`/mcp` 端点将业务能力暴露为 AI 工具）；知识库 RAG 模块自建开发中
- **Excel**：Apache Fesod（EasyExcel 后继）注解化导入导出
- **运维监控**：Spring Boot Admin + Actuator、Redisson 多缓存、多数据源

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| JDK | 21 | 运行时（Jetty 容器） |
| Spring Boot | 4.1.x | 基础框架 |
| Sa-Token | 1.46.x | 认证鉴权 |
| MyBatis-Plus | 3.5.17 | ORM（含联表/动态数据源/数据权限） |
| Spring AI | 2.0.x | MCP server/client |
| Redisson | 4.7.x | 缓存/分布式锁/限流 |
| mapstruct-plus | 1.5.x | 对象转换 |
| Apache Fesod | 2.0.x | Excel 导入导出 |

## 快速开始

环境要求：JDK 21、MySQL 8.x、Redis 6+、Maven 3.9+（可用自带 `mvnw`）

```bash
# 1. 初始化数据库：仅需建空库，无需手工执行 SQL
#    create database `ry-vue` default character set utf8mb4;
#    （首此启动时 Liquibase 按 db/changelog 自动建表并写入初始数据）

# 2. 修改配置：zhiku-admin/src/main/resources/application-dev.yml
#    spring.datasource（MySQL）与 spring.data.redis 连接信息

# 3. 启动（默认 8080）
./mvnw spring-boot:run -pl zhiku-admin
```

前端：clone [zhiku-ui](https://github.com/MoMingMq/zhiku-ui) → `pnpm i` → `pnpm dev`

Docker 全套环境（MySQL/Redis/Nginx/监控）见 `script/docker/docker-compose.yml`。

## 模块说明

```
zhiku-backend
├─ zhiku-admin        # Web 入口与认证
├─ zhiku-api          # 跨模块 API 契约
├─ zhiku-common       # 22 个公共能力子模块（core/mybatis/web/redis/satoken/mcp...）
├─ zhiku-modules      # 业务模块：system / gen / workflow
└─ zhiku-extend       # 独立部署服务：monitor-admin
```

## Roadmap

- [ ] **kb 知识库模块**：文档管理 / 切片 / RAG 检索问答 / 引用溯源
- [ ] 企业微信、钉钉入口
- [ ] 本体（Ontology）知识组织层：概念—关系—实例，增强检索与推理
- [ ] 演示文档与视频

## 开源协议

- 本项目遵循 **MIT License**（继承自上游），见 [LICENSE](./LICENSE) 与 [NOTICE.md](./NOTICE.md)
- 基于 RuoYi-Vue-Plus（MIT）二次开发，原版权声明与 `@author` 标签均按要求保留
