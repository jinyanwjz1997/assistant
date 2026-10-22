# 环境变量配置

项目不再在 `application.yml` 中保存 DeepSeek API Key 和数据库密码。启动前必须提供：

```bash
export DEEPSEEK_API_KEY="替换为新密钥"
export DB_PASSWORD="替换为数据库密码"
./mvnw spring-boot:run
```

IntelliJ IDEA 中可以在 Run/Debug Configuration 的 `Environment variables` 中配置同名变量。
变量清单和非敏感示例见项目根目录 `.env.example`。

`.env`、`application-local.yml` 和 `application-*.local.yml` 已加入 `.gitignore`。Spring Boot
默认不会自动读取 `.env` 文件；生产部署应由操作系统、Docker/Kubernetes Secret 或 CI/CD
密钥管理功能注入环境变量。

## 必须轮换旧密钥

此前提交到源码中的 DeepSeek API Key 已经属于泄露凭据。仅从当前文件删除不能清除 Git
历史和已有副本，应在 DeepSeek 控制台立即吊销旧密钥并创建新密钥。新密钥只能通过
`DEEPSEEK_API_KEY` 注入。
