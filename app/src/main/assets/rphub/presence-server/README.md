# RP-Hub 远程更新检测服务

一个无需数据库的版本检测接口。服务启动时读取线上 RP-Hub 的五位数公告 ID，后续查询最多每分钟刷新一次；前端在页面可见时约每 20 秒查询一次，发现更大的公告 ID 后弹出“发现新版本”。服务不会推送或强制刷新页面。

它是可选服务，不负责聊天、角色卡下载或账号同步。目录名 `presence-server` 为沿用原部署路径，当前已经不统计在线人数。

## 本地运行

需要 Node.js 20 或更高版本，无第三方依赖。在本目录执行：

```sh
npm start
```

默认监听 `0.0.0.0:3000`，健康检查为 `GET /health`。`npm run check` 只检查脚本语法，不会启动服务。也可使用仓库提供的 Dockerfile 部署。

## Zeabur 部署

1. 在 Zeabur 新建服务并连接 RP-Hub 仓库。
2. 将服务的根目录设为 `presence-server`，Zeabur 会自动读取 `Dockerfile`。
3. 建议添加环境变量 `ALLOWED_ORIGINS`，值为 RP-Hub 网页的完整来源，例如 `https://example.com`。多个来源用英文逗号分隔。
4. 部署完成后复制 Zeabur 提供的 HTTPS 域名，填入 RP-Hub `index.html` 中的 `rphub-update-api` 配置。

可选环境变量：

- `ALLOWED_ORIGINS`：允许访问接口的网站来源；未设置时允许所有来源。
- `PORT`：监听端口，默认 `3000`。

来源需要包含协议和端口（如 `http://127.0.0.1:8765`），不是带路径的完整页面 URL。CORS 限制不是身份验证，不能替代服务端访问控制。

版本来源固定在 `server.js` 的 `versionSourceUrl`，默认读取 `https://sta1n156.github.io/RP-Hub/assets/js/built-in-content.js`。部署自己维护的分支时请改为自己的公开文件地址，并同步初始 `latestVersionId`。公告 ID 必须为五位数字且递增，服务运行期间不回退到更小的版本号。读取超时或失败会沿用已知版本。

## 接口

- `GET /health`：健康检查。
- `GET /v1/version?current=10189`：读取最新公告 ID，并判断当前版本是否需要刷新。
- `POST /v1/presence`：仅供旧版页面接收更新提醒，不统计人数或保存浏览器编号。

服务不生成浏览器编号，也不接收或保存角色卡、聊天记录、API 密钥等 RP-Hub 数据。

`POST /v1/presence` 保留是为了兼容已经部署的旧页面，不应因为当前前端未调用它就直接删除。当前前端使用 `GET /v1/version`；要关闭检查，将主页面 `rphub-update-api` 的 `content` 留空即可。
