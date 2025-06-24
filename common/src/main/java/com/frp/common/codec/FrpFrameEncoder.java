package com.frp.common.codec;

import com.frp.common.protocol.FrpFrame;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.nio.charset.StandardCharsets;

// 帧编码器：将FrpFrame对象序列化为字节流，即按协议格式打包
public class FrpFrameEncoder extends MessageToByteEncoder<FrpFrame> {

  @Override
  protected void encode(ChannelHandlerContext ctx, FrpFrame msg, ByteBuf out) throws Exception {
    // 计算总长度
    byte[] proxyIdBytes = msg.getProxyId().getBytes(StandardCharsets.UTF_8);
    int payloadLength = msg.getPayload()!=null?msg.getPayload().length:0;
    int totalLength = 3 + proxyIdBytes.length + payloadLength;

    //2.写入长度字段
    out.writeInt(totalLength);

    //3.写入帧类型
    out.writeBytes(proxyIdBytes);

    //4.写入保留字段
    out.writeByte(msg.getReserved());

    //5.写入proxyId
    out.writeByte(proxyIdBytes.length);
    out.writeBytes(proxyIdBytes);

    //6.写入payload
    if(msg.getPayload()!=null && payloadLength > 0) {
      out.writeBytes(msg.getPayload());
    }
  }
}
