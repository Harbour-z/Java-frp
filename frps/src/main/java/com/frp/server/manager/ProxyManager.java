package com.frp.server.manager;

import com.frp.common.protocol.RegisterRequest;
import com.frp.server.handler.RemoteProxyHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedNioFile;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 代理管理器：核心组件，负责创建/销毁代理、维护端口映射、启动公网监听
 * @author Zhidong Zhang
 */
@Slf4j
public class ProxyManager {

  // 全局单例
  public static final ProxyManager INSTANCE = new ProxyManager();

  // 代理ID -> 代理对象（线程安全）
  private final Map<String, Proxy> proxyMap = new ConcurrentHashMap<>();

  // 公网端口 -> 代理ID(确保端口不重复，线程安全)
  private final Map<Integer, String> portToProxyMap = new ConcurrentHashMap<>();
  // 公网端口监听的EventLoopgroup
  private final EventLoopGroup bossGroup = new NioEventLoopGroup(); //acceptor线程组
  private final EventLoopGroup workerGroup = new NioEventLoopGroup(); //IO处理线程组

  //私有构造确保单例
  private ProxyManager() {}

  /**
   * 创建代理：校验参与 -> 启动公网端口监听 -> 存储代理信息
   * @param request 客户端注册请求
   * @param clientChannel 客户端控制连接Channel
   */

  public synchronized String createProxy(RegisterRequest request, Channel clientChannel) {
    String proxyId = request.getProxyId();
    int remotePort = request.getRemotePort();
    if(proxyMap.containsKey(proxyId)){
      return "代理ID已存在" + proxyId;
    }
    if(portToProxyMap.containsKey(remotePort)){
      return "公网端口已被占用：" + remotePort;
    }
    if(remotePort < 1 || remotePort > 65535){
      return "无效的公网端口：" + remotePort;
    }
    // 2. 创建代理对象
    Proxy proxy = new Proxy();
    proxy.setProxyId(proxyId);
    proxy.setProxyType(request.getProxyType());
    proxy.setLocalIp(request.getLocalIp());
    proxy.setLocalPort(request.getLocalPort());
    proxy.setRemotePort(remotePort);
    proxy.setClientChannel(clientChannel);

    // 3. 启动公网端口监听
    try {
      ServerBootstrap bootstrap = new ServerBootstrap();
      bootstrap.group(bossGroup, workerGroup)
          .channel(NioServerSocketChannel.class)
          .option(io.netty.channel.ChannelOption.SO_BACKLOG, 128)
          .childOption(io.netty.channel.ChannelOption.SO_KEEPALIVE, true)
          .handler(new LoggingHandler(LogLevel.INFO)) // 服务端日志（可选）
          .childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
              ch.pipeline().addLast(new RemoteProxyHandler(proxy)); // 绑定当前代理
            }
          });
      // 绑定公网端口
      ChannelFuture future = bootstrap.bind(remotePort).sync();
      proxy.setRemoteServerChannel(future.channel());
      proxy.setStatus(ProxyStatus.ACTIVE);

      // 存储代理映射
      portToProxyMap.put(remotePort, proxyId);

      log.info("代理[{}]创建成功，公网端口：{}，内网服务：{}:{}",
          proxyId, remotePort, request.getLocalIp(), request.getLocalPort());
      return null; //成功，无需携带信息返回
    } catch (Exception e) {
      String errorMsg = "代理[" + proxyId + "]创建失败：" + e.getMessage();
      log.error(errorMsg, e);
      proxy.setStatus(ProxyStatus.ERROR);
      return errorMsg;
    }
  }

  /**
   * 移除代理：关闭公网端口监听 -> 清理映射关系
   * @param proxyId 代理ID
   */
  public synchronized void removeProxy(String proxyId) {
    Proxy proxy = proxyMap.get(proxyId);
    if(proxy != null) {
      return;
    }

    // 1.关闭公网端口监听
    if(proxy.getRemoteServerChannel() != null) {
      proxy.getClientChannel().close();
      log.info("代理[{}]公网端口{}监听已关闭", proxyId, proxy.getRemotePort());
    }

    //2.清理端口映射
    portToProxyMap.remove(proxyId);

    // 3. 更新状态
    proxy.setStatus(ProxyStatus.INACTIVE);
    log.info("代理[{}]已移除", proxyId);
  }
  /**
   * 根据客户端Channel移除所有关联代理，在客户端断开时调用
   * @param clientChannel 客户端控制连接Channel
   */
  public void removeProxiesByClientChannel(Channel clientChannel) {
    Set<String> proxyIds = proxyMap.values().stream()
        .filter(p -> Objects.equals(p.getClientChannel(), clientChannel))
        .map(Proxy::getProxyId)
        .collect(Collectors.toSet());
    proxyIds.forEach(this::removeProxy);
    log.info("客户端连接断开，已移除{}个关联代理", proxyIds.size());
  }

  /**
   * 更新代理最后活动时间（收到心跳时调用）
   */
  public void updateProxyLastActiveTimeByClientChannel(String clientId) {
    proxyMap.values().stream()
        .filter(p -> p.getClientChannel().id().asShortText().equals(clientId))
        .forEach(Proxy::updateLastActiveTime);
  }

  /**
   * 获取代理对象
   */
  public Proxy getProxy(String proxyId) {
    return proxyMap.get(proxyId);
  }

  /**
   * 服务端关闭时清理所有资源
   */
  public void shutdown() {
    // 移除所有代理
    proxyMap.keySet().forEach(this::removeProxy);
    // 关闭EventLoopGroup
    bossGroup.shutdownGracefully();
    workerGroup.shutdownGracefully();
    log.info("ProxyManager已关闭所有资源");
  }
}
