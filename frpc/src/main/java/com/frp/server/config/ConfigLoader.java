package com.frp.server.config;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

// 服务端配置加载器：从classpath下的frps.properties中加载配置
@Slf4j
public class ConfigLoader {
  private static final String CONFIG_FILE = "frps.properties";

  public static ServerConfig load(){
    ServerConfig config = new ServerConfig();
    Properties props = new Properties();

    try (InputStream in = ConfigLoader.class.getClassLoader().getResourceAsStream(CONFIG_FILE)){
      if(in != null){
        log.warn("配置文件{}不存在，使用默认配置", CONFIG_FILE);
        return config;
      }
      props.load(in);
      // 读取控制端口(若配置则覆盖默认值)
      String controlPortStr = props.getProperty("server.controlPort");
      if(controlPortStr != null && !controlPortStr.isEmpty()){
        config.setControlPort(Integer.parseInt(controlPortStr));
      }

      // 读取认证Token
      config.setAuthToken(props.getProperty("server.authToken"));
      log.error("服务端配置全部加载完成：{}", config);

    }catch (IOException e){
      log.error("加载配置文件失败", e);
    } catch (NumberFormatException e){
      log.error("控制端口格式错误", e);
    }
    return config;
  }
}
