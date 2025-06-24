# Java-frp

## 项目简介

本项目作为计网课程的大作业，目的是用Java语言实现frp(Fast Reverse Proxy)即快速反向代理，用于内网穿透服务。

> 由于是课程大作业，而且我也是第一次了解到内网穿透的原理和实践，故本文档会写的比较啰嗦，记录很多我学习到的东西。
> 实现的过程中参考了一些github上的源码实现，与deepseek-V3、deepseek-R1、doubao-1.6进行了多轮对话。

frp官方实现：[https://github.com/fatedier/frp](https://github.com/fatedier/frp)

本项目使用maven构建。

frp本质是反向代理工具，通过公网服务端-frps和内网客户端frpc配合，将内网服务暴露到公网，核心功能包括：

* TCP/UDP端口映射：将公网端口请求转发到内网服务；
* HTTP/HTTPS代理：基于域名路由到内网不同HTTP服务；
* 控制连接：客户端与服务端建立长连接，传递代理配置和控制指令；
* 数据转发：公网请求通过服务端转发到客户端，再到内网服务。

## 技术选型

* 采用经典的C/S架构：进行公网中转的服务端和内网代理的客户端
* 网络框架：使用Netty(高性能异步NIO框架)，处理高并发连接和数据转发，比Java原生NIO易用，内置编解码器与连接管理；
* 协议设置：自定义二进制协议(参考frp官方协议)，用于控制指令(入注册代理、心跳)和数据转发；
* 配置解析：支持JSON/INI配置文件(Jackson解析JSON，ini4j解析INI)；
* 日志：SLF4J+Logback日志；

## 核心模块设计

项目根目录
```text
frp-java/                  # 项目根目录  
├── pom.xml                # 父POM，声明公共依赖（如Netty、Jackson）  
├── frp-common/            # 公共模块（客户端/服务端共用代码）  
├── frp-server/            # 服务端模块（frps，公网中转节点）  
└── frp-client/            # 客户端模块（frpc，内网代理节点）  
```

### 通用协议定义

```text
frp-common/  
├── pom.xml                # 公共模块依赖（如Jackson、Netty）  
└── src/main/java/com/example/frp/common/  
    ├── protocol/          # 协议定义（帧类型、控制指令、实体类）  
    │   ├── FrameType.java         # 帧类型枚举（CONTROL/DATA）  
    │   ├── FrpFrame.java          # 协议帧实体类（封装帧信息）  
    │   ├── ControlType.java       # 控制指令枚举（REGISTER/HEARTBEAT等）  
    │   ├── RegisterRequest.java   # 注册请求实体类（客户端→服务端）  
    │   └── RegisterResponse.java  # 注册响应实体类（服务端→客户端）  
    │  
    ├── codec/             # 编解码器（Netty编解码逻辑）  
    │   ├── FrpFrameDecoder.java   # 帧解码器（字节流→FrpFrame对象）  
    │   ├── FrpFrameEncoder.java   # 帧编码器（FrpFrame对象→字节流）  
    │   └── ControlFrameCodec.java # 控制帧JSON编解码器（对象→JSON字节数组）  
    │  
    └── util/              # 通用工具类  
        ├── Constants.java         # 常量定义（如默认端口、心跳间隔）  
        └── LogUtils.java          # 日志工具类（简化日志调用）  
```


### 客户端

frpc，内网代理节点

```text
frp-client/  
├── pom.xml                # 客户端依赖（引入frp-common、Netty等）  
├── src/main/java/com/example/frp/client/  
│   ├── boot/              # 客户端启动入口  
│   │   └── FrpClient.java         # 客户端启动类（连接服务端、加载代理配置）  
│   │  
│   ├── config/            # 客户端配置（代理规则、服务端地址等）  
│   │   ├── ClientConfig.java      # 客户端配置实体类（服务端地址、代理列表）  
│   │   ├── ProxyConfig.java       # 单个代理配置（localIp、localPort、remotePort等）  
│   │   └── ConfigLoader.java      # 配置加载器（从文件读取代理规则）  
│   │  
│   └── handler/           # 网络事件处理器（Netty Handler）  
│       ├── ClientControlHandler.java  # 控制连接处理器（注册/心跳/断线重连）  
│       └── LocalProxyHandler.java     # 内网代理处理器（转发请求到内网服务）  
│  
└── src/main/resources/    # 客户端配置文件  
    └── frpc.properties             # 客户端配置（示例：服务端地址、代理规则列表）  
```

### 服务端

frps，公网中转节点

```text
frp-server/  
├── pom.xml                # 服务端依赖（引入frp-common、Netty等）  
├── src/main/java/com/example/frp/server/  
│   ├── boot/              # 服务端启动入口  
│   │   └── FrpServer.java         # 服务端启动类（初始化Netty、绑定端口）  
│   │  
│   ├── config/            # 服务端配置（端口、Token等）  
│   │   ├── ServerConfig.java      # 配置实体类（控制端口、认证Token等）  
│   │   └── ConfigLoader.java      # 配置加载器（从文件/命令行读取配置）  
│   │  
│   ├── handler/           # 网络事件处理器（Netty Handler）  
│   │   ├── ServerControlHandler.java  # 控制连接处理器（注册/心跳/注销）  
│   │   └── RemoteProxyHandler.java    # 远程代理处理器（公网请求转发）  
│   │  
│   └── manager/           # 代理管理（维护代理生命周期和映射关系）  
│       ├── ProxyManager.java       # 代理管理器（创建/销毁代理、端口映射）  
│       ├── Proxy.java              # 代理实体类（存储代理配置和状态）  
│       └── ProxyStatus.java        # 代理状态枚举（INIT/ACTIVE/INACTIVE）  
│  
└── src/main/resources/    # 服务端配置文件  
    └── frps.properties             # 服务端配置（示例：控制端口7000、Token=abc123）  
```
