package com.frp.server.manager;

import io.netty.channel.Channel;
import lombok.Data;

import java.util.concurrent.atomic.AtomicLong;

// 代理实体：存储单个单例的配置喝运行
@Data
public class Proxy {
  private String proxyId; // 代理ID（唯一）
  private String proxyType; // 代理类型，如tcp
  private String localIp; // 内网服务IP，由客户端上报
  private int localPort; // 内网服务端口，客户端上报
  private int remotePort; // 公网暴露端口，服务端监听
  private Channel clientChannel; //客户端控制连接Channel
  private Channel remoteServerChannel; // 公网监听Channel
  private ProxyStatus status;
  private final AtomicLong lastActiveTime = new AtomicLong(System.currentTimeMillis()); //最后活动时间

  public Proxy(){
    this.status = ProxyStatus.INIT;
  }
  public void updateLastActiveTime() {
    lastActiveTime.set(System.currentTimeMillis());
  }
}
