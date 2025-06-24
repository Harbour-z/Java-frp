package com.frp.common.util;

/**
 * @author Zhidong Zhang
 */
public class Constants {
  public static final int DEFAULT_CONTROL_PORT = 7000;
  public static final int HEARTBEAT_INTERVAL = 30; //心跳间隔(秒)
  public static final int HEARTBEAT_TIMEOUT = 70; //心跳超时
  public static final int MAX_PROXY_ID_LENGTH = 64;
  public static final int BUFFER_SIZE = 1024 * 8; //缓冲区大小(8KB)
}
