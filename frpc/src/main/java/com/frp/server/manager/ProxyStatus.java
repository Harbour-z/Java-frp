package com.frp.server.manager;

/**
 * @author Zhidong Zhang
 */

// 代理状态：描述代理的生命周期
public enum ProxyStatus {
  // 四状态：初始化、正常运行、停止、异常状态
  INIT,
  ACTIVE,
  INACTIVE,
  ERROR
}
