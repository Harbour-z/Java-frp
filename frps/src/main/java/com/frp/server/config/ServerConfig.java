package com.frp.server.config;

import lombok.Data;

/**
 * @author Zhidong Zhang
 */
@Data
public class ServerConfig {
  private int controlPort = 7000; //控制端口默认
  private String authToken; // 与客户端匹配的Token
}
