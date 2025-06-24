package com.frp.client.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ClientConfig {
  private String serverHost; //服务端公网IP或域名
  private int serverPort;
  private String authToken;
  private List<ProxyConfig> proxies = new ArrayList<>();//代理规则表
}
