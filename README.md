# Java-frp

## 项目简介

本项目作为计网课程的大作业，目的是用Java语言实现frp(Fast Reverse Proxy)即快速反向代理，用于内网穿透服务。

frp官方实现：[https://github.com/fatedier/frp](https://github.com/fatedier/frp)

本项目使用maven构建。

frp本质是反向代理工具，通过公网服务端-frps和内网客户端frpc配合，将内网服务暴露到公网，核心功能包括：

* TCP/UDP端口映射：将公网端口请求转发到内网服务；
* HTTP/HTTPS代理：基于域名路由到内网不同HTTP服务；
* 控制连接：客户端与服务端建立长连接，传递代理配置和控制指令；
* 数据转发：公网请求通过服务端转发到客户端，再到内网服务。

## 技术选型

* 网络框架：使用Netty(高性能异步NIO框架)，处理高并发连接和数据转发，比Java原生NIO易用，内置编解码器与连接管理；
* 协议设置：自定义二进制协议(参考frp官方协议)，用于控制指令(入注册代理、心跳)和数据转发；
* 配置解析：支持JSON/INI配置文件(Jackson解析JSON，ini4j解析INI)；
* 日志：SLF4J+Logback日志；

## 核心模块设计

