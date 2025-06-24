package com.frp.common.protocol;

/**
 * @author Zhidong Zhang
 */

public enum FrameType {
  //控制帧和数据帧
  CONTROL((byte) 0x01),
  DATA((byte) 0x02),;

  FrameType(byte value) {
  }
}
