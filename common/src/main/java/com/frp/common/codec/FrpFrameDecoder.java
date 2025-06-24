package com.frp.common.codec;

import com.frp.common.protocol.FrameType;
import com.frp.common.protocol.FrpFrame;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.awt.*;
import java.nio.charset.StandardCharsets;

/**
 * @author Zhidong Zhang
 */ // 解决TCP粘包/拆包问题，解析网络字节流为FrpFrame
public class FrpFrameDecoder extends LengthFieldBasedFrameDecoder {
  private static final int MAX_FRAME_LENGTH = 1024 * 1024;
  private static final int LENGTH_FIELD_OFFSET = 0;
  private static final int LENGTH_FIELD_LENGTH = 4;
  private static final int LENGTH_ADJUSTMENT = 0;
  private static final int INITIAL_BYTES_TO_STRIP = 4; // 解析时跳过长度字段

  public FrpFrameDecoder() {
    super(MAX_FRAME_LENGTH, LENGTH_FIELD_OFFSET, LENGTH_FIELD_LENGTH,
        LENGTH_ADJUSTMENT, INITIAL_BYTES_TO_STRIP);
  }

  @Override
  protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
    ByteBuf frame = (ByteBuf) super.decode(ctx, in); //通过LengthField解析完整帧
    if (frame == null) {
      return null;
    }

    try{
      // 1.读取1字节的帧类型
      byte typeValue = frame.readByte();
      FrameType type = FrameType.fromValue(typeValue);
      if(type == null) {
        ctx.close();
        return null;
      }

      // 2.读取保留字节
      byte reserverd = frame.readByte();

      // 3.读取proxyId
      int proxyIdLength = frame.readUnsignedByte();
      byte[] proxyIdBytes = new byte[proxyIdLength];
      frame.readBytes(proxyIdBytes);
      String proxyId = new String(proxyIdBytes, StandardCharsets.UTF_8);

      // 4.读取payload
      byte[] payload = new byte[frame.readableBytes()];
      frame.readBytes(payload);

      return new FrpFrame(type, reserverd, proxyId, payload);
    } finally {
      frame.release();
    }
  }
}
