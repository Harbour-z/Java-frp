package com.frp.common.protocol;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Zhidong Zhang
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FrpFrame {
  /*
   * 协议帧实体
   * */
  private FrameType type;
  private byte reserved;
  private String proxyId;
  private byte[] payload;
}
