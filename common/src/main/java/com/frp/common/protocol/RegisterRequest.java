package com.frp.common.protocol;

import lombok.Data;

// 注册请求实体类：客户端向服务端申请创建代理
@Data
public class RegisterRequest {
  private ControlType type = ControlType.REGISTER;
  // 代理唯一ID
  private String proxyId;
  //代理类型
  private String proxyType;
  private String localIp;
  private int localPort;
  private int remotePort;
  // 认证Token，需与服务端一致
  private String authToken;
}
