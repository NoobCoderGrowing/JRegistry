# JRegistry

**中文** | [English](#english)

基于 Raft 共识的分布式键值注册中心，附带 Web 管理后台、Netty RPC 客户端与 SSH 命令行。

---

## 概述

JRegistry 是一个基于 [Raft](https://raft.github.io/) 共识算法的多节点分布式注册中心。数据以层级键值形式存储在内存状态机中（键名使用 `.` 分隔路径，例如 `app.config.timeout`），写操作通过 Raft 日志复制，并可通过以下方式访问：

- **Netty RPC** — 通过 `JRegistryClient` 进行编程式 get / set / delete
- **Web 管理后台** — 集群监控、状态机浏览、选主动画回放
- **SSH Shell** — 每个节点提供类文件系统的交互式命令行

默认部署为本地 **3 节点集群**（`127.0.0.1` / `127.0.0.2` / `127.0.0.3`）。

## 功能特性

| 模块 | 说明 |
|------|------|
| **Raft 共识** | 领导者选举、日志复制、日志提交 |
| **状态机** | 层级 B+ 树结构；键名以 `.` 作为路径分隔符 |
| **持久化** | 将 Raft 节点状态、日志和状态机快照写入 `persistency/`；启动时自动恢复 |
| **日志压缩** | 支持定时与手动压缩，清理历史日志 |
| **管理后台** | React + Vite 仪表盘：集群概览、节点角色、状态机树、选主动画 |
| **SSH CLI** | 命令：`get`、`set`、`delete`、`ls`、`cd`、`pwd`、`persist`、`compact` |
| **客户端 SDK** | 基于 Netty 的 `JRegistryClient`，支持同步 `get` 与异步写操作 |

## 架构

```
┌─────────────────────────────────────────────────────────────┐
│                        JRegistry 集群                        │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐               │
│  │  节点 1  │◄──►│  节点 2  │◄──►│  节点 3  │  Raft RPC     │
│  │ :6001    │    │ :6002    │    │ :6003    │  (Netty)      │
│  └────┬─────┘    └────┬─────┘    └────┬─────┘               │
│       │ HTTP :6101    │ HTTP :6102    │ HTTP :6103          │
│       │ SSH  :2001    │ SSH  :2002    │ SSH  :2003          │
└───────┼───────────────┼───────────────┼─────────────────────┘
        │               │               │
   管理后台 / API    JRegistryClient    SSH 命令行
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
tar -xzf JRegistry-1.0.0.tar.gz #解压包
cd JRegistry-1.0.0
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

admin:
  peer-http-ports: "{1:6101, 2:6102, 3:6103}"  # 集群管理后台HTTP端口
  election-log-path: logs/JRegistry.log

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


## JRegistryClient 客户端

连接任意节点的 Raft 端口进行读写：

```java
JRegistryClient client = new JRegistryClient("127.0.0.3", 6003, 1000, 5000);
if (!client.connect()) {
    throw new IllegalStateException("无法连接 Registry");
}

client.set("app.config.name", "demo".getBytes(), "string");
Thread.sleep(1000);

Pair<byte[], String> result = client.get("app.config.name");
if (result != null) {
    System.out.println(new String(result.getLeft()));
}

client.delete("app.config.name");
client.shutdown();
```

运行示例客户端：

```bash
mvn -f pom.xml clean package -pl JRegistryClient -am -DskipTests
cd JRegistryClient
./start.sh
```

## SSH 命令行

每个节点通过 Apache MINA SSHD 提供 SSH 服务。使用配置文件中的账号连接（默认 `admin` / `123`）：

```bash
ssh admin@127.0.0.1 -p 2001
```

支持的命令：

| 命令 | 示例 | 说明 |
|------|------|------|
| `get` | `get app.config.timeout` | 按完整路径读取值 |
| `set` | `set mykey hello` | 写入字符串 |
| `delete` | `delete mykey` | 删除键 |
| `ls` | `ls` | 列出当前路径下的子节点 |
| `cd` | `cd app.config` | 在状态机树中切换路径 |
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

JRegistry is a multi-node distributed registry built on the [Raft](https://raft.github.io/) consensus algorithm. Data is stored as hierarchical key-value pairs in an in-memory state machine (keys use `.` as path separators, e.g. `app.config.timeout`). Writes are replicated through the Raft log and can be accessed via:

- **Netty RPC** — programmatic get / set / delete through `JRegistryClient`
- **Web Admin UI** — cluster monitoring, state machine browser, election replay animation
- **SSH Shell** — interactive, filesystem-style CLI on each node

The default deployment is a local **3-node cluster** (`127.0.0.1` / `127.0.0.2` / `127.0.0.3`).

## Features

| Area | Description |
|------|-------------|
| **Raft consensus** | Leader election, log replication, log commit |
| **State machine** | Hierarchical B+ tree; keys use `.` as path separator |
| **Persistence** | Snapshots of Raft node state, log, and state machine written to `persistency/`; auto-recovery on startup |
| **Log compaction** | Scheduled and manual compaction to trim old log entries |
| **Admin UI** | React + Vite dashboard: cluster overview, node roles, state machine tree, election animation |
| **SSH CLI** | Commands: `get`, `set`, `delete`, `ls`, `cd`, `pwd`, `persist`, `compact` |
| **Client SDK** | Netty-based `JRegistryClient` with synchronous `get` and asynchronous writes |

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        JRegistry Cluster                     │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐               │
│  │  Node 1  │◄──►│  Node 2  │◄──►│  Node 3  │  Raft RPC    │
│  │ :6001    │    │ :6002    │    │ :6003    │  (Netty)       │
│  └────┬─────┘    └────┬─────┘    └────┬─────┘               │
│       │ HTTP :6101    │ HTTP :6102    │ HTTP :6103          │
│       │ SSH  :2001    │ SSH  :2002    │ SSH  :2003          │
└───────┼───────────────┼───────────────┼─────────────────────┘
        │               │               │
   Admin UI / API   JRegistryClient   SSH shell
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
tar -xzf JRegistry-1.0.0.tar.gz   # extract
cd JRegistry-1.0.0
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

admin:
  peer-http-ports: "{1:6101, 2:6102, 3:6103}"  # admin HTTP ports for all nodes
  election-log-path: logs/JRegistry.log

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

## JRegistryClient

Connect to any node's Raft port for read/write operations:

```java
JRegistryClient client = new JRegistryClient("127.0.0.3", 6003, 1000, 5000);
if (!client.connect()) {
    throw new IllegalStateException("Cannot connect to registry");
}

client.set("app.config.name", "demo".getBytes(), "string");
Thread.sleep(1000);

Pair<byte[], String> result = client.get("app.config.name");
if (result != null) {
    System.out.println(new String(result.getLeft()));
}

client.delete("app.config.name");
client.shutdown();
```

Run the sample client:

```bash
mvn -f pom.xml clean package -pl JRegistryClient -am -DskipTests
cd JRegistryClient
./start.sh
```

## SSH Shell

Each node exposes an SSH server (Apache MINA SSHD). Connect using credentials from config (default `admin` / `123`):

```bash
ssh admin@127.0.0.1 -p 2001
```

Supported commands:

| Command | Example | Description |
|---------|---------|-------------|
| `get` | `get app.config.timeout` | Read a value by full key path |
| `set` | `set mykey hello` | Write a string value |
| `delete` | `delete mykey` | Remove a key |
| `ls` | `ls` | List children at current path |
| `cd` | `cd app.config` | Navigate the state machine tree |
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

