package com.frp.server.handler;

import com.frp.server.manager.Proxy;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

import java.nio.channels.Channel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class RemoteProxyHandler extends ChannelInboundHandlerAdapter {
  // 静态映射：proxyId -> 公网用户连接Channel，用于回传响应
  private static final Map<String, Channel> PUBLIC_CHANNEL_MAP = new ConcurrentHashMap<>();

  private final Proxy proxy; // 当前代理实例
  private Channel publicChannel; //公网用户连接Channel

  public RemoteProxyHandler(Proxy proxy) {
    this.proxy = proxy;
  }
}
