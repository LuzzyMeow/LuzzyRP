# RP-Hub 远程更新检测服务

一个无需数据库的版本检测接口。服务每分钟读取线上 RP-Hub 的五位数公告 ID；旧页面每 20 秒查询一次，发现更大的公告 ID 后弹出“发现新版本”。

## Zeabur 部署

1. 在 Zeabur 新建服务并连接 RP-Hub 仓库。
2. 将服务的根目录设为 `presence-server`，Zeabur 会自动读取 `Dockerfile`。
3. 建议添加环境变量 `ALLOWED_ORIGINS`，值为 RP-Hub 网页的完整来源，例如 `https://example.com`。多个来源用英文逗号分隔。
4. 部署完成后复制 Zeabur 提供的 HTTPS 域名，填入 RP-Hub `index.html` 中的 `rphub-update-api` 配置。

可选环境变量：

- `ALLOWED_ORIGINS`：允许访问接口的网站来源；未设置时允许所有来源。

## 接口

- `GET /health`：健康检查。
- `GET /v1/version?current=10189`：读取最新公告 ID，并判断当前版本是否需要刷新。
- `POST /v1/presence`：仅供旧版页面接收更新提醒，不统计人数或保存浏览器编号。

服务不生成浏览器编号，也不接收或保存角色卡、聊天记录、API 密钥等 RP-Hub 数据。
