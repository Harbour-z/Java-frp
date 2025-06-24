package com.frp.client.handler;

import com.frp.common.protocol.FrameType;
import com.frp.common.protocol.FrpFrame;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LocalProxyHandler extends ChannelInboundHandlerAdapter {
  private final Channel serverControlChannel; // 客户端与服务端的控制连接Channel（用于回传响应）
  private final String proxyId;               // 当前代理ID（如"web-8080"）
  /**
   * 构造函数：绑定控制连接和代理ID
   * @param serverControlChannel 客户端与服务端的控制连接（必须是活跃的）
   * @param proxyId 当前代理的唯一标识
   */
  public LocalProxyHandler(Channel serverControlChannel, String proxyId) {
    this.serverControlChannel = serverControlChannel;
    this.proxyId = proxyId;
  }
  /**
   * 读取内网服务的响应数据（如内网Web服务返回的HTML/JSON），转发给服务端
   */
  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) {
    if (!(msg instanceof ByteBuf)) {
      ctx.fireChannelRead(msg); // 非ByteBuf类型数据透传（通常不会出现）
      return;
    }
    ByteBuf buf = (ByteBuf) msg;
    try {
      // 1. 读取内网服务响应的字节数据
      byte[] responseData = new byte[buf.readableBytes()];
      buf.readBytes(responseData);
      // 2. 封装为DATA类型帧（协议格式：类型=DATA，proxyId=当前代理ID，payload=响应数据）
      FrpFrame dataFrame = new FrpFrame(
          FrameType.DATA,    // 帧类型：数据帧
          (byte) 0,          // 保留字段
          proxyId,           // 代理ID（服务端据此转发给公网用户）
          responseData       // 内网服务响应数据
      );
      // 3. 通过控制连接发送给服务端（服务端再转发给公网用户）
      if (serverControlChannel.isActive()) {
        serverControlChannel.writeAndFlush(dataFrame);
        log.debug("代理[{}]：内网服务响应已转发，数据长度：{}字节", proxyId, responseData.length);
      } else {
        log.error("代理[{}]：控制连接已断开，无法转发内网响应", proxyId);
      }
    } finally {
      buf.release(); // 释放ByteBuf，避免内存泄漏
    }
  }
  /**
   * 内网服务连接建立成功时触发（如成功连接本地8080端口）
   */
  @Override
  public void channelActive(ChannelHandlerContext ctx) {
    log.info("代理[{}]：成功连接内网服务，本地连接ID：{}",
        proxyId, ctx.channel().id().asShortText());
  }
  /**
   * 内网服务连接断开时触发（如内网服务停止）
   */
  @Override
  public void channelInactive(ChannelHandlerContext ctx) {
    log.warn("代理[{}]：内网服务连接已断开", proxyId);
    // （可选）可发送断开通知给服务端，告知公网用户连接关闭
  }
  /**
   * 内网服务连接异常时触发（如内网服务未启动）
   */
  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    log.error("代理[{}]：内网服务连接异常", proxyId, cause);
    ctx.close(); // 关闭内网连接，避免资源泄漏
  }
}
