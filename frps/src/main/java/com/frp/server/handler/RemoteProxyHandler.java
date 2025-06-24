package com.frp.server.handler;

import com.frp.common.protocol.FrameType;
import com.frp.common.protocol.FrpFrame;
import com.frp.server.manager.Proxy;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 远程代理处理器：监听公网端口，接收公网用户请求并转发给内网客户端
 * 每个公网代理对应一个处理器实例
 * @author Zhidong Zhang
 */
@Slf4j
public class RemoteProxyHandler extends ChannelInboundHandlerAdapter {
  // 静态映射：proxyId -> 公网用户连接Channel，用于回传响应
  private static final Map<String, Channel> PUBLIC_CHANNEL_MAP = new ConcurrentHashMap<>();

  private final Proxy proxy; // 当前代理实例
  private Channel publicUserChannel; //公网用户连接Channel

  public RemoteProxyHandler(Proxy proxy) {
    this.proxy = proxy;
  }
  /**
   * 公网用户连接建立时触发（如浏览器访问公网IP:端口）
   */
  @Override
  public void channelActive(ChannelHandlerContext ctx) {
    publicUserChannel = ctx.channel();
    String proxyId = proxy.getProxyId();
    int remotePort = proxy.getRemotePort();
    // 缓存公网用户连接（同一代理仅允许一个公网连接，简化实现）
    PUBLIC_CHANNEL_MAP.put(proxyId, publicUserChannel);
    log.info("公网用户连接代理[{}]（公网端口{}），连接ID：{}",
        proxyId, remotePort, publicUserChannel.id().asShortText());
  }

  //接收公网用户发送的数据，并转发给内网客户端
  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) {
    if(!(msg instanceof ByteBuf)) {
      ctx.fireChannelRead(msg);
      return;
    }

    ByteBuf buf = (ByteBuf) msg;
    byte[] data = new byte[buf.readableBytes()];
    buf.readBytes(data);
    buf.release();

    String proxyId = proxy.getProxyId();
    Channel clientChannel = proxy.getClientChannel();
    if(clientChannel == null || !clientChannel.isActive()) {
      log.error("代理[{}]的客户端连接已断开，无法转发数据", proxyId);
      return;
    }

    //封装为数据帧，发送给客户端
    FrpFrame dataframe = new FrpFrame(
        FrameType.DATA,
        (byte) 0,
        proxyId,
        data
    );
    clientChannel.writeAndFlush(dataframe);
    log.debug("代理[{}]转发公网数据到内网，长度：{}字节", proxyId, data.length);
  }

  /**
   * 公网用户连接断开时清理
   */
  @Override
  public void channelInactive(ChannelHandlerContext ctx) {
    String proxyId = proxy.getProxyId();
    PUBLIC_CHANNEL_MAP.remove(proxyId);
    log.info("公网用户断开代理[{}]连接", proxyId);
  }

  /**
   * 异常处理
   */
  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    log.error("公网代理[{}]异常", proxy.getProxyId(), cause);
    ctx.close();
  }
  /**
   * 静态方法：通过proxyId获取公网用户连接（供ServerControlHandler回传响应）
   */
  public static Channel getPublicUserChannel(String proxyId) {
    return PUBLIC_CHANNEL_MAP.get(proxyId);
  }
}
