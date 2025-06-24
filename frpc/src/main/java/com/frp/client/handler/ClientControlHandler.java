package com.frp.client.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.frp.client.config.ClientConfig;
import com.frp.client.config.ProxyConfig;
import com.frp.common.codec.ControlFrameCodec;
import com.frp.common.codec.FrpFrameDecoder;
import com.frp.common.codec.FrpFrameEncoder;
import com.frp.common.protocol.*;
import com.frp.common.util.Constants;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ClientControlHandler extends SimpleChannelInboundHandler<FrpFrame>{
  private final ClientConfig clientConfig; // 客户端全局配置
  private Channel serverChannel; // 与服务端的控制连接Channel
  private final Map<String, Channel> proxyChannelMap = new ConcurrentHashMap<>(); // proxyId→内网服务连接
  private ScheduledFuture<?> heartbeatTask; // 心跳定时任务
  private EventLoopGroup reconnectGroup = new NioEventLoopGroup(1); // 断线重连线程组
  public ClientControlHandler(ClientConfig clientConfig) {
    this.clientConfig = clientConfig;
  }
  /**
   * 启动客户端：连接服务端控制端口
   */
  public void start() {
    doConnect(); // 首次连接
  }
  /**
   * 连接服务端（含断线重连逻辑）
   */
  private void doConnect() {
    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(new NioEventLoopGroup())
        .channel(NioSocketChannel.class)
        .option(ChannelOption.SO_KEEPALIVE, true)
        .handler(new ChannelInitializer<SocketChannel>() {
          @Override
          protected void initChannel(SocketChannel ch) {
            ch.pipeline()
                // 超时检测：5秒未读（服务端断连）触发事件
                .addLast(new IdleStateHandler(5, 0, 0, TimeUnit.SECONDS))
                // 协议帧编解码器（公共模块）
                .addLast(new FrpFrameDecoder())
                .addLast(new FrpFrameEncoder())
                // 业务处理器（当前类）
                .addLast(ClientControlHandler.this);
          }
        });
    // 发起连接
    ChannelFuture future = bootstrap.connect(clientConfig.getServerHost(), clientConfig.getServerPort());
    future.addListener((ChannelFutureListener) f -> {
      if (f.isSuccess()) {
        serverChannel = f.channel();
        log.info("成功连接服务端：{}:{}", clientConfig.getServerHost(), clientConfig.getServerPort());
        // 连接成功后，注册所有代理规则
        registerAllProxies();
        // 启动心跳定时任务
        startHeartbeat();
      } else {
        log.error("连接服务端失败，{}秒后重试...", 5);
        // 5秒后重试连接
        reconnectGroup.schedule(this::doConnect, 5, TimeUnit.SECONDS);
      }
    });
  }
  /**
   * 向服务端注册所有代理规则
   */
  private void registerAllProxies() {
    List<ProxyConfig> proxies = clientConfig.getProxies();
    for (ProxyConfig proxy : proxies) {
      try {
        RegisterRequest request = new RegisterRequest();
        request.setProxyId(proxy.getProxyId());
        request.setProxyType(proxy.getProxyType());
        request.setLocalIp(proxy.getLocalIp());
        request.setLocalPort(proxy.getLocalPort());
        request.setRemotePort(proxy.getRemotePort());
        request.setAuthToken(clientConfig.getAuthToken());
        // 封装为控制帧发送
        byte[] payload = ControlFrameCodec.serialize(request);
        FrpFrame frame = new FrpFrame(FrameType.CONTROL, (byte) 0, proxy.getProxyId(), payload);
        serverChannel.writeAndFlush(frame);
        log.info("已发送代理注册请求：{}", proxy.getProxyId());
      } catch (Exception e) {
        log.error("代理{}注册请求序列化失败", proxy.getProxyId(), e);
      }
    }
  }
  /**
   * 启动心跳定时任务（每30秒发送一次心跳）
   */
  private void startHeartbeat() {
    if (heartbeatTask != null) {
      heartbeatTask.cancel(true); // 取消旧任务
    }
    heartbeatTask = serverChannel.eventLoop().scheduleAtFixedRate(() -> {
      if (serverChannel.isActive()) {
        // 发送心跳帧（控制帧，payload为空）
        FrpFrame heartbeatFrame = null;
        try {
          heartbeatFrame = new FrpFrame(
              FrameType.CONTROL, (byte) 0, "",
              ControlFrameCodec.serialize(new HeartbeatRequest())
          );
        } catch (JsonProcessingException e) {
          throw new RuntimeException(e);
        }
        serverChannel.writeAndFlush(heartbeatFrame);
        log.debug("发送心跳包");
      }
    }, 0, Constants.HEARTBEAT_INTERVAL, TimeUnit.SECONDS);
  }
  /**
   * 处理从服务端接收的帧（注册响应/公网请求数据）
   */
  @Override
  protected void channelRead0(ChannelHandlerContext ctx, FrpFrame frame) {
    if (frame.getType() == FrameType.CONTROL) {
      handleControlFrame(frame); // 处理控制帧（注册响应等）
    } else if (frame.getType() == FrameType.DATA) {
      handleDataFrame(frame); // 处理数据帧（公网用户请求）
    }
  }
  /**
   * 处理控制帧（注册响应）
   */
  private void handleControlFrame(FrpFrame frame) {
    try {
      // 解析为注册响应
      RegisterResponse response = ControlFrameCodec.deserialize(frame.getPayload(), RegisterResponse.class);
      if (response.getType() == ControlType.REGISTER_RESP) {
        if (response.isSuccess()) {
          log.info("代理{}注册成功", response.getProxyId());
        } else {
          log.error("代理{}注册失败：{}", response.getProxyId(), response.getMessage());
        }
      }
    } catch (Exception e) {
      log.error("解析控制帧失败", e);
    }
  }
  /**
   * 处理数据帧（公网用户请求→转发到内网服务）
   */
  private void handleDataFrame(FrpFrame frame) {
    String proxyId = frame.getProxyId();
    byte[] data = frame.getPayload();
    if (data == null || data.length == 0) return;
    // 1. 查找该代理对应的内网服务连接（复用连接）
    Channel localChannel = proxyChannelMap.get(proxyId);
    if (localChannel != null && localChannel.isActive()) {
      // 复用已有连接，直接转发数据
      localChannel.writeAndFlush(Unpooled.wrappedBuffer(data));
      return;
    }
    // 2. 若连接不存在，创建新连接到内网服务
    ProxyConfig proxyConfig = findProxyConfig(proxyId);
    if (proxyConfig == null) {
      log.error("未找到代理{}的配置", proxyId);
      return;
    }
    // 连接内网服务（如127.0.0.1:8080）
    Bootstrap localBootstrap = new Bootstrap();
    localBootstrap.group(serverChannel.eventLoop()) // 复用服务端连接的EventLoop
        .channel(NioSocketChannel.class)
        .handler(new ChannelInitializer<SocketChannel>() {
          @Override
          protected void initChannel(SocketChannel ch) {
            ch.pipeline().addLast(new LocalProxyHandler(serverChannel, proxyId));
          }
        });
    localBootstrap.connect(proxyConfig.getLocalIp(), proxyConfig.getLocalPort())
        .addListener((ChannelFutureListener) f -> {
          if (f.isSuccess()) {
            localChannel = f.channel();
            proxyChannelMap.put(proxyId, localChannel); // 缓存连接
            localChannel.writeAndFlush(Unpooled.wrappedBuffer(data)); // 转发数据
            log.info("成功连接内网服务：{}:{}（代理ID：{}）",
                proxyConfig.getLocalIp(), proxyConfig.getLocalPort(), proxyId);
            // 内网连接关闭时，从缓存移除
            localChannel.closeFuture().addListener(cf -> {
              proxyChannelMap.remove(proxyId);
              log.info("内网服务连接已关闭：{}", proxyId);
            });
          } else {
            log.error("连接内网服务失败：{}:{}（代理ID：{}）",
                proxyConfig.getLocalIp(), proxyConfig.getLocalPort(), proxyId, f.cause());
          }
        });
  }
  /**
   * 根据proxyId查找代理配置
   */
  private ProxyConfig findProxyConfig(String proxyId) {
    return clientConfig.getProxies().stream()
        .filter(p -> p.getProxyId().equals(proxyId))
        .findFirst()
        .orElse(null);
  }
  /**
   * 处理连接断开（触发重连）
   */
  @Override
  public void channelInactive(ChannelHandlerContext ctx) {
    log.warn("与服务端的连接已断开，正在重连...");
    // 取消心跳任务
    if (heartbeatTask != null) {
      heartbeatTask.cancel(true);
    }
    // 清除内网连接缓存
    proxyChannelMap.clear();
    // 触发重连
    reconnectGroup.schedule(this::doConnect, 5, TimeUnit.SECONDS);
  }
  /**
   * 处理超时事件（服务端无响应）
   */
  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
    if (evt instanceof IdleStateEvent) {
      IdleStateEvent event = (IdleStateEvent) evt;
      if (event.state() == IdleState.READER_IDLE) {
        log.warn("服务端长时间无响应，主动断开连接并重连");
        ctx.close(); // 关闭当前连接，触发channelInactive重连
      }
    }
  }
  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    log.error("客户端控制连接异常", cause);
    ctx.close(); // 关闭连接，触发重连
  }
  /**
   * 心跳请求实体（内部类）
   */
  private static class HeartbeatRequest {
    private ControlType type = ControlType.HEARTBEAT; // 固定为HEARTBEAT
  }
}
