package com.frp.server.handler;

import com.frp.common.codec.ControlFrameCodec;
import com.frp.common.protocol.FrameType;
import com.frp.common.protocol.FrpFrame;
import com.frp.common.protocol.RegisterRequest;
import com.frp.common.protocol.RegisterResponse;
import com.frp.common.util.Constants;
import com.frp.server.manager.Proxy;
import com.frp.server.manager.ProxyManager;
import com.frp.server.manager.ProxyStatus;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 控制连接处理器，处理客户端的控制连接，如注册/心跳/注销等指令
 */
@Slf4j
public class ServerControlHandler extends SimpleChannelInboundHandler<FrpFrame> {
  private final String authToken; // 服务端认证Token，用来校验客户端
  private String clientId; //客户端连接ID
  private ScheduledFuture<?> heartbeatTimeoutTask; //心跳超时检测任务

  public ServerControlHandler(String authToken) {
    this.authToken = authToken;
  }

  /**
   * 客户端连接建立时触发
   */
  @Override
  public void channelActive(ChannelHandlerContext ctx){
    clientId = ctx.channel().id().asShortText();
    log.info("客户端[{}]已连接", clientId);

    // 启动心跳超市检测，默认时长60秒
    resetHeartbeatTimeout(ctx);
  }

  // 客户端连接断开时触发
  @Override
  public void channelInactive(ChannelHandlerContext ctx){
    log.info("客户端[{}]已断开连接", clientId);

    //取消心跳超时任务
    if(heartbeatTimeoutTask != null) {
      heartbeatTimeoutTask.cancel(true);
    }
    //移除该客户端的所有代理（通过客户端Channel关联）
    ProxyManager.INSTANCE.removeProxiesByClientChannel(ctx.channel());
  }

  // 接收客户端发送的帧（控制帧/数据帧）
  @Override
  protected void channelRead0(ChannelHandlerContext ctx, FrpFrame frame){
    // 收到数据，重置心跳超时检测
    resetHeartbeatTimeout(ctx);

    if (frame.getType() == FrameType.CONTROL) {
      handleControlFrame(ctx, frame); // 处理控制帧（注册/心跳等）
    } else if (frame.getType() == FrameType.DATA) {
      handleDataFrame(ctx, frame); // 处理数据帧（内网服务响应→公网用户）
    }
  }

  //处理控制帧
  private void handleControlFrame(ChannelHandlerContext ctx, FrpFrame frame) {
    try {
      // 解析payload
      Object controlObj = ControlFrameCodec.deserialize(frame.getPayload(), Object.class);
      String type = (String) ((com.fasterxml.jackson.databind.node.ObjectNode) controlObj).get("type").asText();

      switch (type) {
        case "REGISTER":
          handleRegister(ctx, frame); // 处理注册请求
          break;
        case "HEARTBEAT":
          handleHeartbeat(); // 处理心跳包
          break;
        case "CLOSE_PROXY":
          handleCloseProxy(frame); // 处理注销代理请求
          break;
        default:
          log.warn("客户端[{}]发送未知控制指令：{}", clientId, type);
      }
    }catch (Exception e){
      log.error("客户端[{}]控制帧解析失败", clientId, e);
    }
  }

  /**
   * 处理注册请求（客户端申请创建代理）
   */
  private void handleRegister(ChannelHandlerContext ctx, FrpFrame frame) throws Exception {
    // 1. 解析注册请求
    RegisterRequest request = ControlFrameCodec.deserialize(frame.getPayload(), RegisterRequest.class);
    log.info("客户端[{}]发送注册请求：proxyId={}, remotePort={}, local={}:{}",
        clientId, request.getProxyId(), request.getRemotePort(),
        request.getLocalIp(), request.getLocalPort());
    // 2. 校验Token
    if (!authToken.equals(request.getAuthToken())) {
      sendRegisterResponse(ctx, request.getProxyId(), false, "认证失败：Token不匹配");
      log.warn("客户端[{}]注册失败：Token不匹配", clientId);
      return;
    }
    // 3. 调用ProxyManager创建代理
    String errorMsg = ProxyManager.INSTANCE.createProxy(request, ctx.channel());
    boolean success = errorMsg == null;
    sendRegisterResponse(ctx, request.getProxyId(), success, success ? "注册成功" : errorMsg);
  }

  /**
   * 发送注册响应给客户端
   */
  private void sendRegisterResponse(ChannelHandlerContext ctx, String proxyId, boolean success, String message) {
    try {
      RegisterResponse response = new RegisterResponse();
      response.setProxyId(proxyId);
      response.setSuccess(success);
      response.setMessage(message);
      // 封装为控制帧发送
      byte[] payload = ControlFrameCodec.serialize(response);
      FrpFrame frame = new FrpFrame(FrameType.CONTROL, (byte) 0, proxyId, payload);
      ctx.writeAndFlush(frame);
    } catch (Exception e) {
      log.error("发送注册响应失败", e);
    }
  }

  // 处理心跳包
  private void handleHeartbeat() {
    log.debug("收到客户端[{}]的心跳包", clientId);
    // 更新所有关联代理的最后活动时间
    ProxyManager.INSTANCE.updateProxyLastActiveTimeByClientChannel(clientId);
  }

  //处理注销代理请求
  private void handleCloseProxy(FrpFrame frame) {
    String proxyId = frame.getProxyId();
    log.info("客户端[{}]请求注销代理：{}", clientId, proxyId);
    ProxyManager.INSTANCE.removeProxy(proxyId);
  }

  // 处理数据帧
  private void handleDataFrame(ChannelHandlerContext ctx, FrpFrame frame) {
    String proxyId = frame.getProxyId();
    byte[] data = frame.getPayload();
    if(data == null || data.length == 0) {
      return;
    }

    //通过proxyId获取公网用户连接，转发数据
    Proxy proxy = ProxyManager.INSTANCE.getProxy(proxyId);
    if(proxy == null || proxy.getStatus() != ProxyStatus.ACTIVE){
      log.warn("代理[{}]不存在或未激活，无法转发数据", proxyId);
      return;
    }

    // 公网用户连接由RemoteProxyHandler维护，通过proxyId关联
    Channel publicUserChannel = RemoteProxyHandler.getPublicUserChannel(proxyId);
    if (publicUserChannel != null && publicUserChannel.isActive()) {
      publicUserChannel.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(data));
      log.debug("代理[{}]转发内网响应数据，长度：{}字节", proxyId, data.length);
    } else {
      log.warn("代理[{}]无活跃公网用户连接，丢弃数据", proxyId);
    }
  }

  //重置心跳超时检测（每次收到数据时调用）
  private void resetHeartbeatTimeout(ChannelHandlerContext ctx) {
    if(heartbeatTimeoutTask != null) {
      heartbeatTimeoutTask.cancel(true);
    }
    heartbeatTimeoutTask = ctx.executor().schedule(() -> {
      log.warn("客户端[{}]心跳超时（{}秒未响应），关闭连接", clientId, Constants.HEARTBEAT_TIMEOUT);
      ctx.close();
    }, Constants.HEARTBEAT_TIMEOUT, TimeUnit.SECONDS);
  }

  //处理超时事件
  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt){
    if(evt instanceof IdleStateEvent) {
      log.warn("客户端[{}]连接超时，关闭连接", clientId);
      ctx.close();
    }
  }

  // 处理异常
  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause){
    log.error("客户端[{}]连接异常", clientId, cause);
    ctx.close(); // 异常时关闭连接
  }
}
