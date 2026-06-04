# JRegistry Center 管理后台

基于 React + Vite 的 Raft 集群节点管理界面。

## 开发模式

```bash
cd JRegistry/admin-ui
npm install
npm run dev
```

浏览器访问 http://localhost:5173 ，API 会代理到本机节点 `http://127.0.0.1:6101`。

## 生产构建

```bash
npm run build
```

产物输出到 `JRegistry/src/main/resources/static/`，随 Spring Boot 一起打包。

启动任意 JRegistryCenter 节点后，访问该节点的 HTTP 端口即可，例如：

- 节点 1: http://127.0.0.1:6101/
- 节点 2: http://127.0.0.2:6102/
- 节点 3: http://127.0.0.3:6103/

## API

| 接口 | 说明 |
|------|------|
| `GET /api/admin/cluster` | 集群概览 + 全部节点列表 |
| `GET /api/admin/node` | 当前节点 Raft 状态 |
| `GET /api/admin/self` | 当前节点详细信息 |
