package com.frp.client.config;

import lombok.Data;

// 单个代理规则配置：描述内网服务如何通过公网暴露
@Data
public class ProxyConfig {
  private String proxyId; //代理ID 需与服务端唯一
  private String proxyType = "tcp"; //代理类型，目前仅支持tcp
  private String localIp; //内网服务IP
  private int localPort; //内网服务端口
  private int remotePort; //公网暴露端口
}
