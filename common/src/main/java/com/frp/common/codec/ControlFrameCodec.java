package com.frp.common.codec;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

import java.io.IOException;

/**
 * 控制帧编解码器：将控制指令对象喝JSON字节数组互转
 */
public class ControlFrameCodec {
  private static final ObjectMapper objectMapper = new ObjectMapper();

  // 控制指令对象序列化为JSON字节数组
  public static byte[] serialize(Object obj) throws JsonProcessingException{
    return objectMapper.writeValueAsBytes(obj);
  }

  // JSON字节数组反序列化为控制指令对象
  @SneakyThrows(IOException.class)
  public static <T> T deserialize(byte[] data, Class<T> clazz){
    return objectMapper.readValue(data, clazz);
  }
}
