# JRegistry

**中文** | [English](#english)

基于 Raft 共识的分布式键值注册中心，附带 Web 管理后台、Netty RPC 客户端与 SSH 命令行。

---

## 概述

JRegistry 是一个基于 [Raft](https://raft.github.io/) 共识算法的多节点分布式注册中心。数据以层级键值形式存储在内存状态机中（键名使用 `/` 分隔路径，例如 `app/config/timeout`；除 `/` 外的任意字符都可以作为键名，包括 `.`），写操作通过 Raft 日志复制，并可通过以下方式访问：

- **Netty RPC** — 通过 `JRegistryClient` 进行编程式 get / set / delete
- **Web 管理后台** — 集群监控、状态机浏览、选主动画回放
- **SSH Shell** — 每个节点提供类文件系统的交互式命令行

默认部署为本地 **3 节点集群**（`127.0.0.1` / `127.0.0.2` / `127.0.0.3`）。

## 功能特性

| 模块 | 说明 |
|------|------|
| **Raft 共识** | 领导者选举、日志复制、日志提交 |
| **状态机** | 层级 B+ 树结构；键名以 `/` 作为路径分隔符，除 `/` 外任意字符均可 |
| **持久化** | 将 Raft 节点状态、日志和状态机快照写入 `persistency/`；启动时自动恢复 |
| **日志压缩** | 支持定时与手动压缩，清理历史日志 |
| **管理后台** | React + Vite 仪表盘：集群概览、节点角色、状态机树、选主动画 |
| **SSH CLI** | 命令：`get`、`set`、`delete`、`ls`、`cd`、`pwd`、`persist`、`compact` |
| **客户端 SDK** | 基于 Netty 的 `JRegistryClient`，支持同步 `get` / `listAll` 与异步写操作 |

## 架构

```
                         JRegistryClient
                      (Raft :6001/6002/6003)
                               │
┌──────────────────────────────┼──────────────────────────────┐
│                        JRegistry 集群                        │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐               │
│  │  节点 1  │◄──►│  节点 2  │◄──►│  节点 3  │  (节点间 Raft) │
│  │ :6001    │    │ :6002    │    │ :6003    │               │
│  └──────────┘    └──────────┘    └──────────┘               │
│                                                              │
│  HTTP :6101         :6102           :6103  ◄── 管理后台 / API │
│  SSH  :2001         :2002           :2003  ◄── SSH 命令行    │
└──────────────────────────────────────────────────────────────┘
```

### 模块说明

| 模块 | 职责 |
|------|------|
| **JRegistryCore** | 公共核心：`RaftNode`、`StateMachine`、`LogEntry`、RPC 数据结构 |
| **JRegistry** | Spring Boot 服务端：Raft 服务、持久化、Web 管理、SSH |
| **JRegistryClient** | Netty 客户端库及示例 `TestApp` |

## 环境要求

- **JDK 11+**
- **Maven 3.6+**
- **Node.js 18+** 与 **npm**（用于构建管理后台）
- 推荐 Linux/macOS（启动脚本为 bash）

## 快速开始

### 1. 启动 3 节点集群

下载release压缩包后执行：

```bash
tar -xzf JRegistry-1.0.5.tar.gz #解压包
cd JRegistry-1.0.5
./start-cluster.sh
```

脚本会依次：
1. 停止已有节点
2. 清空旧日志
3. 在后台启动3个默认配置的节点集群

进程 PID 保存在 `logs/node1.pid`、`logs/node2.pid`、`logs/node3.pid`。

### 2. 打开管理后台

| 节点 | 管理后台地址 |
|------|-------------|
| 1 | http://127.0.0.1:6101/ |
| 2 | http://127.0.0.2:6102/ |
| 3 | http://127.0.0.3:6103/ |

### 3. 停止集群

```bash
./stop.sh
```


## 配置说明

每个节点对应 `config/` 下的一份 YAML 配置：

| 文件 | 节点 |
|------|------|
| `application.yaml` | 节点 1 |
| `application_node2.yaml` | 节点 2 |
| `application_node3.yaml` | 节点 3 |

主要配置项：

```yaml
host: 127.0.0.1

server:
  port: 6101          # HTTP / 管理后台

raft:
  node-id: 1
  port: 6001          # 本机 Raft端口配置
  peers: "{2:'127.0.0.2:6002',3:'127.0.0.3:6003'}"   # Raft集群 初始IP端口配置
  count: 3
  auto-persist: true
  image-path: "persistency/"
  log-compaction-interval: 14400   # 秒
  timing:                        # Raft 协议时间参数（毫秒）
    election-timeout-min-ms: 15000  # 选举超时下限，随机区间 [min, max]
    election-timeout-max-ms: 30000  # 选举超时上限；必须显著大于 heartbeat-interval-ms
    heartbeat-interval-ms: 5000     # Leader 写空闲这么久就补一次心跳
    reconnect-delay-ms: 5000        # peer 断开后多久重连
    connect-timeout-ms: 5000        # peer 连接超时

admin:
  peer-http-ports: "{1:6101, 2:6102, 3:6103}"  # 集群管理后台HTTP端口
  election-log-path: logs/JRegistry.log

registry:
  client-heartbeat:          # 客户端连接探活
    read-timeout-seconds: 20 # 连续这么久无入站流量算丢一次心跳（须大于客户端 10s 心跳间隔）
    max-misses: 3            # 连续丢几次 → 把该客户端标为「离线」
    retry-delay-seconds: 5
  session-sweep:             # 仅 Leader 生效：节点失效后回收它名下的客户端会话
    enabled: true
    sweep-interval-seconds: 10
    peer-dead-threshold-seconds: 60
    leader-grace-seconds: 30

ssh:
  enabled: true
  host: 127.0.0.1
  port: 2001
  auth:
    username: admin
    password: 123
```

> **注意：** 请在**仓库根目录**（或 release 解压目录）下启动进程，以确保 `persistency/` 和 `logs/` 等相对路径正确。

### 默认端口

| 节点 | HTTP | Raft RPC | SSH |
|------|------|----------|-----|
| 1 | 6101 | 6001 | 2001 |
| 2 | 6102 | 6002 | 2002 |
| 3 | 6103 | 6003 | 2003 |


**主要功能：**

- 集群概览与各节点 Raft 角色（Leader / Candidate / Follower）
- 状态机树浏览及写/删操作（经 Leader 提交）
- 选主历史与最新成功选主的动画回放
- 手动触发 **Persist**（持久化）与 **Compact**（日志压缩）


## JRegistryClient 客户端快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>io.github.noobcodergrowing</groupId>
    <artifactId>JRegistryClient</artifactId>
    <version>1.0.5</version>
</dependency>
```

> **从 1.0.0 升级：** 包名已由 `hawk.JRegistryClient` / `hawk.JRegitstryCore` 统一为 `io.github.noobcodergrowing.jregistryclient` / `io.github.noobcodergrowing.jregistrycore`，请同步更新 import。
>
> **1.0.4 新增：** `JRegistryClient#listAll(path)` —— 递归列出某个路径下所有带值的 key:value 对。旧版本升级只需替换依赖版本号，无破坏性变更。
>
> **1.0.5 新增：** 客户端连接**离线状态**（过期不再删 key，只翻二值状态；读结果一并带出状态）、**连接负载均衡**（新增 5 参构造：种子地址 + 服务端按连接数下发选点 + 自动故障转移）、**客户端时间参数外置**（新增全参构造，心跳间隔 / 重连间隔 / 故障转移阈值都可显式传入），并修复了**断线重连此前从未真正生效**的问题。详见下文「连接状态与负载均衡」。

### 前置条件

先启动 JRegistry 集群（见上文「快速开始」）。客户端连接任意节点的 **Raft RPC 端口**（默认 6001 / 6002 / 6003），无需经过 HTTP 管理端口。

### API 概览

构造方法：

- `JRegistryClient(host, port, taskTimeoutMs, connectTimeoutMillis)` —— 基础用法
- `JRegistryClient(host, port, taskTimeoutMs, connectTimeoutMillis, serverSideBalancing)` —— 开启连接负载均衡
- `JRegistryClient(host, port, taskTimeoutMs, connectTimeoutMillis, serverSideBalancing, heartbeatIntervalMs, reconnectDelayMs, failoverAfterAttempts)` —— 全参构造，需要自定义心跳间隔 / 重连间隔 / 故障转移阈值时用这个

| 方法 | 说明 |
|------|------|
| `connect()` | 同步建立 TCP 连接，成功返回 `true`；开启负载均衡时会先连种子地址，再由服务端下发选点结果 |
| `isConnected()` | 当前是否已连接 |
| `get(key)` | 同步读取，返回 `Pair<byte[], String>`（值 + 类型）；失败或超时返回 `null` |
| `getWithStatus(key)` | 同 `get`，但返回 `KV`，额外带出该 key 所属客户端的二值状态（`isOffline()`） |
| `listAll(path)` | 同步递归列出该路径及其所有后代中带值的 key:value 对，返回 `List<KV>`（`key` 为绝对完整路径，可直接再传给 `get`，每个 `KV` 都带 `isOffline()`）；路径不存在或超时返回 `null` |
| `set(key, data, dataType)` | 异步写入（fire-and-forget） |
| `delete(key)` | 异步删除 |
| `shutdown()` | 关闭连接并释放资源 |

键名使用 `/` 作为路径分隔符，例如 `app/config/timeout`。除 `/` 外的任意字符都可以作为键名的一部分（`.` 只是普通字符，因此 `app.config.name` 是一个键名而非三级路径）。

> **注意：** 该分隔符与旧版本的 `.` 不兼容，`persistency/` 下按 `.` 分层持久化的数据需要清空后重新写入。

### 示例代码

完整示例见 [`Example.java`](JRegistryClient/src/test/java/io/github/noobcodergrowing/jregistryclient/Example.java)。

```java
import io.github.noobcodergrowing.jregistryclient.JRegistryClient;
import io.github.noobcodergrowing.jregistrycore.KV;
import io.github.noobcodergrowing.jregistrycore.Pair;
import java.util.List;

public class Example {
    public static void main(String[] args) throws InterruptedException {
        // host, Raft 端口, 单次 RPC 超时(ms), TCP 连接超时(ms)
        JRegistryClient client = new JRegistryClient("127.0.0.3", 6003, 1000, 5000);
        if (!client.connect()) {
            client.shutdown();
            return;
        }
        try {
            client.set("app/config/name", "demo".getBytes(), "string");
            Thread.sleep(1000); // 等待 Raft 复制提交
            Pair<byte[], String> result = client.get("app/config/name");
            if (result != null) {
                System.out.println("value=" + new String(result.getLeft()));
            } else {
                System.out.println("get returned null");
            }

            // 递归列出 app 下所有带值的 key:value 对，key 为绝对完整路径
            List<KV> kvs = client.listAll("app");
            if (kvs == null) {
                System.out.println("listAll returned null (path not found)");
            } else {
                for (KV kv : kvs) {
                    System.out.println(kv.getKey() + "=" + new String(kv.getData())
                            + " (" + kv.getDataType() + ")");
                }
            }
        } finally {
            client.shutdown();
        }
    }
}
```

### 连接状态与负载均衡

**离线状态。** 服务端为每个客户端连接维持一份租约。以下任一情况都会把该客户端标记为**离线**：连接断开、连续 `max-misses` 次心跳超时、或它所在的节点被判定失效。

> **关键语义：只翻二值状态，不删除它写过的 key。** 数据仍在，只是状态变了。客户端重新连上（`clientId` 不变）即恢复「生效」。由 SSH / Admin 写入、没有归属客户端的 key 恒为「生效」。

状态随读结果带出：

```java
KV kv = client.getWithStatus("app/config/name");
if (kv != null) {
    System.out.println(new String(kv.getData()) + " offline=" + kv.isOffline());
}

for (KV item : client.listAll("app")) {
    System.out.println(item.getKey() + " offline=" + item.isOffline());
}
```

**连接负载均衡。** 客户端默认钉在构造时给的那一个地址上。用 5 参构造可以把它当作**种子地址**，让服务端决定落点：

```java
// 只需配置一个入口；服务端按各节点当前在线连接数下发候选并自动迁过去
JRegistryClient client = new JRegistryClient("127.0.0.1", 6001, 1000, 5000, true);
```

流程：连种子 → 服务端按各节点在线连接数排出候选列表（同负载的节点按 `clientId` 打散，避免所有客户端都拿到同一个首选）→ 迁到候选首位 → 连不上依次往下试。

落位后地址**保持稳定**，不会因节点抖动来回搬家；目标连续不可达（约 20s）时才故障转移到下一个候选。

> **注意：** 写的负载最终都落在 Leader 上（连到 follower 的写会被服务端转发一跳），所以连接均衡真正分摊的是**每个连接的固定开销**（IO、心跳、会话跟踪）和**读**。

**时间参数。** 用短构造时，心跳间隔、重连间隔、故障转移阈值取默认值——即外置之前的原值，行为与旧版一致；需要调就用全参构造。

| 参数 | 默认 |
|------|------|
| `heartbeatIntervalMs` | 10000 |
| `reconnectDelayMs` | 5000 |
| `failoverAfterAttempts` | 3 |

这三个默认值是 `JRegistryClient` 上的公开常量（`DEFAULT_HEARTBEAT_INTERVAL_MS` / `DEFAULT_RECONNECT_DELAY_MS` / `DEFAULT_FAILOVER_AFTER_ATTEMPTS`），可以直接引用。

```java
// 短构造：心跳 10s / 重连 5s / 连续失败 3 次后故障转移，全走默认值
JRegistryClient plain = new JRegistryClient("127.0.0.1", 6001, 1000, 5000, false);

// 全参构造，顺序：
// taskTimeout, connectTimeout, serverSideBalancing, heartbeatInterval, reconnectDelay, failoverAfterAttempts
JRegistryClient tuned = new JRegistryClient("127.0.0.1", 6001, 1000, 5000, true, 5000, 3000, 2);
```

> `heartbeatIntervalMs` 必须小于服务端的 `registry.client-heartbeat.read-timeout-seconds`（默认 10000 < 20000），否则服务端会把正常连接误判为掉线。位置参数多且单位相同，只改一两项时建议用短构造。

## SSH 命令行

每个节点通过 Apache MINA SSHD 提供 SSH 服务。使用配置文件中的账号连接（默认 `admin` / `123`）：

```bash
ssh admin@127.0.0.1 -p 2001
```

支持的命令：

| 命令 | 示例 | 说明 |
|------|------|------|
| `get` | `get app/config/timeout` | 按完整路径读取值 |
| `set` | `set mykey hello` | 写入字符串 |
| `delete` | `delete mykey` | 删除键 |
| `ls` | `ls` | 列出当前路径下的子节点 |
| `cd` | `cd app/config` | 在状态机树中切换路径 |
| `pwd` | `pwd` | 显示当前路径 |
| `persist` | `persist` | 全集群快照（仅 Leader） |
| `compact` | `compact` | 日志压缩（仅 Leader） |


### 技术栈

| 层次 | 技术 |
|------|------|
| 服务端 | Java 11、Spring Boot 2.7.8、Log4j2 |
| RPC | Netty 4.1 |
| 共识 | 自研 Raft 实现 |
| 管理后台 | React、Vite、TypeScript |
| SSH | Apache SSHD |
| 序列化 | FastJSON、Gson |

### 持久化目录

写入并执行 persist 后，快照保存在 `persistency/`：

```
persistency/
├── raftNode1.json
├── log1.json
├── stateMachine1.json
├── raftNode2.json
├── ...
```

## 项目结构

```
JRegistry/
├── JRegistryCore/          # Raft 与状态机公共核心
├── JRegistry/              # 服务端、管理后台、脚本
│   ├── admin-ui/           # React 管理仪表盘
│   ├── src/main/java/      # Raft 服务、Web、SSH
│   ├── start.sh            # 开发模式构建并启动 3 节点集群
│   ├── stop.sh             # 开发模式停止所有节点
│   └── build-release.sh    # 构建release包
├── JRegistryClient/        # Netty 客户端库
├── persistency/            # raft日志及state machine快照
├── logs/                   # 应用日志与 PID 文件
└── release/                # 发布包目录
```

---

# English

A distributed, Raft-based key-value registry with a web admin console, Netty RPC client, and SSH shell.

---

## Overview

JRegistry is a multi-node distributed registry built on the [Raft](https://raft.github.io/) consensus algorithm. Data is stored as hierarchical key-value pairs in an in-memory state machine (keys use `/` as path separators, e.g. `app/config/timeout`; any character other than `/` is allowed inside a key, including `.`). Writes are replicated through the Raft log and can be accessed via:

- **Netty RPC** — programmatic get / set / delete through `JRegistryClient`
- **Web Admin UI** — cluster monitoring, state machine browser, election replay animation
- **SSH Shell** — interactive, filesystem-style CLI on each node

The default deployment is a local **3-node cluster** (`127.0.0.1` / `127.0.0.2` / `127.0.0.3`).

## Features

| Area | Description |
|------|-------------|
| **Raft consensus** | Leader election, log replication, log commit |
| **State machine** | Hierarchical B+ tree; keys use `/` as path separator, any character other than `/` is allowed |
| **Persistence** | Snapshots of Raft node state, log, and state machine written to `persistency/`; auto-recovery on startup |
| **Log compaction** | Scheduled and manual compaction to trim old log entries |
| **Admin UI** | React + Vite dashboard: cluster overview, node roles, state machine tree, election animation |
| **SSH CLI** | Commands: `get`, `set`, `delete`, `ls`, `cd`, `pwd`, `persist`, `compact` |
| **Client SDK** | Netty-based `JRegistryClient` with synchronous `get` / `listAll` and asynchronous writes |

## Architecture

```
                         JRegistryClient
                      (Raft :6001/6002/6003)
                               │
┌──────────────────────────────┼──────────────────────────────┐
│                       JRegistry Cluster                      │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐               │
│  │  Node 1  │◄──►│  Node 2  │◄──►│  Node 3  │  (inter-node) │
│  │ :6001    │    │ :6002    │    │ :6003    │               │
│  └──────────┘    └──────────┘    └──────────┘               │
│                                                              │
│  HTTP :6101         :6102           :6103  ◄── Admin UI / API│
│  SSH  :2001         :2002           :2003  ◄── SSH shell     │
└──────────────────────────────────────────────────────────────┘
```

### Modules

| Module | Role |
|--------|------|
| **JRegistryCore** | Shared core: `RaftNode`, `StateMachine`, `LogEntry`, RPC DTOs |
| **JRegistry** | Spring Boot server: Raft services, persistence, web admin, SSH |
| **JRegistryClient** | Netty client library and sample `TestApp` |

## Requirements

- **JDK 11+**
- **Maven 3.6+**
- **Node.js 18+** and **npm** (for building the admin UI)
- Linux/macOS recommended (startup scripts use bash)

## Quick Start

### 1. Start a 3-node cluster

After downloading the release archive:

```bash
tar -xzf JRegistry-1.0.5.tar.gz   # extract
cd JRegistry-1.0.5
./start-cluster.sh
```

The script will:

1. Stop any existing nodes
2. Clear old logs
3. Start a 3-node cluster with default config in the background

PID files are saved to `logs/node1.pid`, `logs/node2.pid`, and `logs/node3.pid`.

### 2. Open the admin console

| Node | Admin UI |
|------|----------|
| 1 | http://127.0.0.1:6101/ |
| 2 | http://127.0.0.2:6102/ |
| 3 | http://127.0.0.3:6103/ |

### 3. Stop the cluster

```bash
./stop.sh
```

## Configuration

Each node has a YAML config file under `config/`:

| File | Node |
|------|------|
| `application.yaml` | Node 1 |
| `application_node2.yaml` | Node 2 |
| `application_node3.yaml` | Node 3 |

Key settings:

```yaml
host: 127.0.0.1

server:
  port: 6101          # HTTP / Admin UI

raft:
  node-id: 1
  port: 6001          # local Raft port
  peers: "{2:'127.0.0.2:6002',3:'127.0.0.3:6003'}"   # initial cluster peer addresses
  count: 3
  auto-persist: true
  image-path: "persistency/"
  log-compaction-interval: 14400   # seconds
  timing:                        # Raft protocol timings (milliseconds)
    election-timeout-min-ms: 15000  # election timeout lower bound, random in [min, max]
    election-timeout-max-ms: 30000  # election timeout upper bound; must exceed heartbeat-interval-ms
    heartbeat-interval-ms: 5000     # leader sends a heartbeat after this much write-idle
    reconnect-delay-ms: 5000        # delay before reconnecting to a peer
    connect-timeout-ms: 5000        # peer connect timeout

admin:
  peer-http-ports: "{1:6101, 2:6102, 3:6103}"  # admin HTTP ports for all nodes
  election-log-path: logs/JRegistry.log

registry:
  client-heartbeat:          # client connection liveness
    read-timeout-seconds: 20 # silence for this long counts as one missed heartbeat (> client's 10s interval)
    max-misses: 3            # this many consecutive misses -> mark the client offline
    retry-delay-seconds: 5
  session-sweep:             # leader-only: reclaim sessions owned by a node judged dead
    enabled: true
    sweep-interval-seconds: 10
    peer-dead-threshold-seconds: 60
    leader-grace-seconds: 30

ssh:
  enabled: true
  host: 127.0.0.1
  port: 2001
  auth:
    username: admin
    password: 123
```

> **Note:** Start the process from the **repository root** (or the extracted release directory) so relative paths like `persistency/` and `logs/` resolve correctly.

### Default ports

| Node | HTTP | Raft RPC | SSH |
|------|------|----------|-----|
| 1 | 6101 | 6001 | 2001 |
| 2 | 6102 | 6002 | 2002 |
| 3 | 6103 | 6003 | 2003 |

**Capabilities:**

- Cluster overview and per-node Raft role (Leader / Candidate / Follower)
- State machine tree browser with write/delete operations (submitted via Leader)
- Election history and animated replay of the latest successful election
- Manual **Persist** and **Compact** triggers

## JRegistryClient quick start

### Maven dependency

```xml
<dependency>
    <groupId>io.github.noobcodergrowing</groupId>
    <artifactId>JRegistryClient</artifactId>
    <version>1.0.5</version>
</dependency>
```

> **Upgrading from 1.0.0:** packages were renamed from `hawk.JRegistryClient` / `hawk.JRegitstryCore` to `io.github.noobcodergrowing.jregistryclient` / `io.github.noobcodergrowing.jregistrycore`. Update your imports accordingly.
>
> **New in 1.0.4:** `JRegistryClient#listAll(path)` — recursively lists every valued key:value pair under a path. Upgrading from an earlier release only requires bumping the dependency version; no breaking changes.
>
> **New in 1.0.5:** an **offline status** for client connections (expiry flips a binary status instead of deleting keys; read results carry it), **connection load balancing** (new 5-arg constructor: seed address + server-side placement by connection count + automatic failover), client **timing tunables exposed on the client constructor** (new all-args overload: heartbeat interval / reconnect delay / failover threshold), and a fix for **reconnect never actually working**. See *Connection status and load balancing* below.

### Prerequisites

Start the JRegistry cluster first (see **Quick start** above). The client connects to any node's **Raft RPC port** (defaults: 6001 / 6002 / 6003), not the HTTP admin port.

### API overview

Constructors:

- `JRegistryClient(host, port, taskTimeoutMs, connectTimeoutMillis)` — basic
- `JRegistryClient(host, port, taskTimeoutMs, connectTimeoutMillis, serverSideBalancing)` — connection load balancing
- `JRegistryClient(host, port, taskTimeoutMs, connectTimeoutMillis, serverSideBalancing, heartbeatIntervalMs, reconnectDelayMs, failoverAfterAttempts)` — all-args overload, for tuning the heartbeat interval / reconnect delay / failover threshold

| Method | Description |
|--------|-------------|
| `connect()` | Establish TCP connection synchronously; returns `true` on success. With balancing enabled it first connects to the seed, then follows the server's placement result |
| `isConnected()` | Whether the client is currently connected |
| `get(key)` | Synchronous read; returns `Pair<byte[], String>` (value + type), or `null` on failure/timeout |
| `getWithStatus(key)` | Same as `get`, but returns a `KV` carrying the owning client's binary status (`isOffline()`) |
| `listAll(path)` | Synchronously lists, recursively, the key:value pairs that hold a value at or below `path`; returns `List<KV>` (`key` is the absolute path, reusable with `get`; each `KV` carries `isOffline()`), or `null` if the path does not exist / on timeout |
| `set(key, data, dataType)` | Asynchronous write (fire-and-forget) |
| `delete(key)` | Asynchronous delete |
| `shutdown()` | Close the connection and release resources |

Keys use `/` as a path separator, e.g. `app/config/timeout`. Any character other than `/` is allowed inside a key (`.` is just an ordinary character, so `app.config.name` is a single key, not a three-level path).

> **Note:** This separator is incompatible with the `.` used by earlier versions; data persisted under `persistency/` with `.`-separated keys must be cleared and written again.

### Example

See [`Example.java`](JRegistryClient/src/test/java/io/github/noobcodergrowing/jregistryclient/Example.java) for the full source.

```java
import io.github.noobcodergrowing.jregistryclient.JRegistryClient;
import io.github.noobcodergrowing.jregistrycore.KV;
import io.github.noobcodergrowing.jregistrycore.Pair;
import java.util.List;

public class Example {
    public static void main(String[] args) throws InterruptedException {
        // host, Raft port, per-RPC timeout (ms), TCP connect timeout (ms)
        JRegistryClient client = new JRegistryClient("127.0.0.3", 6003, 1000, 5000);
        if (!client.connect()) {
            client.shutdown();
            return;
        }
        try {
            client.set("app/config/name", "demo".getBytes(), "string");
            Thread.sleep(1000); // wait for Raft replication
            Pair<byte[], String> result = client.get("app/config/name");
            if (result != null) {
                System.out.println("value=" + new String(result.getLeft()));
            } else {
                System.out.println("get returned null");
            }

            // Recursively list every valued key:value pair under app; keys are absolute paths
            List<KV> kvs = client.listAll("app");
            if (kvs == null) {
                System.out.println("listAll returned null (path not found)");
            } else {
                for (KV kv : kvs) {
                    System.out.println(kv.getKey() + "=" + new String(kv.getData())
                            + " (" + kv.getDataType() + ")");
                }
            }
        } finally {
            client.shutdown();
        }
    }
}
```

### Connection status and load balancing

**Offline status.** The server keeps a lease per client connection. Any of these marks the client **offline**: the connection drops, `max-misses` consecutive heartbeats time out, or the node holding it is judged dead.

> **Key semantics: only the binary status flips — the keys it wrote are never deleted.** The data stays; only the status changes. Reconnecting (same `clientId`) flips it back to active. Keys written via SSH / Admin have no owning client and are always active.

The status rides along with read results:

```java
KV kv = client.getWithStatus("app/config/name");
if (kv != null) {
    System.out.println(new String(kv.getData()) + " offline=" + kv.isOffline());
}

for (KV item : client.listAll("app")) {
    System.out.println(item.getKey() + " offline=" + item.isOffline());
}
```

**Connection load balancing.** By default the client is pinned to the single address it was constructed with. The 5-arg constructor treats it as a **seed** and lets the server pick the placement:

```java
// configure a single entry point; the server hands out candidates by live connection count
JRegistryClient client = new JRegistryClient("127.0.0.1", 6001, 1000, 5000, true);
```

Flow: connect to the seed → the server ranks candidates by each node's live connection count (ties spread by `clientId` so clients don't all pick the same node) → move to the top candidate → walk down the list on failure.

Once placed the address is **stable** — no shuffling on transient blips; failover to the next candidate only kicks in after the target has been unreachable for ~20s.

> **Note:** writes ultimately land on the Leader anyway (a write to a follower is forwarded one hop), so connection balancing spreads the **per-connection overhead** (IO, heartbeats, session tracking) and **reads**.

**Timings.** With the short constructors the heartbeat interval, reconnect delay and failover threshold take their defaults — the values that used to be hard-coded, so behaviour matches earlier versions. The all-args constructor is there when you need to tune them.

| Parameter | Default |
|-----------|---------|
| `heartbeatIntervalMs` | 10000 |
| `reconnectDelayMs` | 5000 |
| `failoverAfterAttempts` | 3 |

These defaults are public constants on `JRegistryClient` (`DEFAULT_HEARTBEAT_INTERVAL_MS` / `DEFAULT_RECONNECT_DELAY_MS` / `DEFAULT_FAILOVER_AFTER_ATTEMPTS`), so you can reference them directly.

```java
// short form: heartbeat 10s / reconnect 5s / fail over after 3 failures, all defaults
JRegistryClient plain = new JRegistryClient("127.0.0.1", 6001, 1000, 5000, false);

// all-args, in this order:
// taskTimeout, connectTimeout, serverSideBalancing, heartbeatInterval, reconnectDelay, failoverAfterAttempts
JRegistryClient tuned = new JRegistryClient("127.0.0.1", 6001, 1000, 5000, true, 5000, 3000, 2);
```

> `heartbeatIntervalMs` must stay below the server's `registry.client-heartbeat.read-timeout-seconds` (default 10000 < 20000), otherwise the server will judge a healthy connection as offline. Several of the positional parameters share a unit, so prefer the short form when changing only one or two.

## SSH Shell

Each node exposes an SSH server (Apache MINA SSHD). Connect using credentials from config (default `admin` / `123`):

```bash
ssh admin@127.0.0.1 -p 2001
```

Supported commands:

| Command | Example | Description |
|---------|---------|-------------|
| `get` | `get app/config/timeout` | Read a value by full key path |
| `set` | `set mykey hello` | Write a string value |
| `delete` | `delete mykey` | Remove a key |
| `ls` | `ls` | List children at current path |
| `cd` | `cd app/config` | Navigate the state machine tree |
| `pwd` | `pwd` | Show current path |
| `persist` | `persist` | Snapshot all nodes (leader only) |
| `compact` | `compact` | Compact logs (leader only) |

### Tech stack

| Layer | Technology |
|-------|------------|
| Server | Java 11, Spring Boot 2.7.8, Log4j2 |
| RPC | Netty 4.1 |
| Consensus | Custom Raft implementation |
| Admin UI | React, Vite, TypeScript |
| SSH | Apache SSHD |
| Serialization | FastJSON, Gson |

### Persistence layout

After writes and persist operations, snapshots are saved under `persistency/`:

```
persistency/
├── raftNode1.json
├── log1.json
├── stateMachine1.json
├── raftNode2.json
├── ...
```

## Project layout

```
JRegistry/
├── JRegistryCore/          # Shared Raft & state machine core
├── JRegistry/              # Server, admin UI, scripts
│   ├── admin-ui/           # React admin dashboard
│   ├── src/main/java/      # Raft services, web, SSH
│   ├── start.sh            # Dev: build and start 3-node cluster
│   ├── stop.sh             # Dev: stop all nodes
│   └── build-release.sh    # Build release package
├── JRegistryClient/        # Netty client library
├── persistency/            # Raft log & state machine snapshots
├── logs/                   # Application logs & PID files
└── release/                # Release output directory
```

