package com.frp.common.protocol;

public enum ControlType {
  // c -> s，注册代理
  REGISTER,
  // s -> c，注册响应
  REGISTER_RESP,
  // c -> s，心跳包
  HEARTBEAT,
  // c -> s，注销代理
  CLOSE_PROXY
}
