package com.frp.common.protocol;

import lombok.Getter;

/**
 * @author Zhidong Zhang
 */


@Getter
public enum FrameType {
  //控制帧：注册/心跳/注销
  CONTROL((byte) 0x01),
  DATA((byte) 0x02);

  private final byte value;

  FrameType(byte value) {
    this.value = value;
  }

  public static FrameType fromValue(byte value){
    for(FrameType type : FrameType.values()){
      if (type.value==value) {
        return type;
      }
    }
    return null;
  }
}
