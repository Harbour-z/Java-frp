package com.frp.client.config;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * @author Zhidong Zhang
 */
@Slf4j
public class ConfigLoader {
  private static final String CONFIG_FILE = "frpc.properties";
  public static ClientConfig load() {
    ClientConfig config = new ClientConfig();
    Properties props = new Properties();
    try (InputStream in = ConfigLoader.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
      if (in == null) {
        log.error("配置文件{}不存在，无法启动客户端", CONFIG_FILE);
        throw new RuntimeException("配置文件缺失");
      }
      props.load(in);
      // 1. 加载服务端连接信息
      config.setServerHost(props.getProperty("client.serverHost"));
      String serverPortStr = props.getProperty("client.serverPort");
      if (serverPortStr != null && !serverPortStr.isEmpty()) {
        config.setServerPort(Integer.parseInt(serverPortStr));
      }
      config.setAuthToken(props.getProperty("client.authToken"));
      // 校验必填项
      if (config.getServerHost() == null || config.getAuthToken() == null) {
        log.error("服务端地址或Token未配置");
        throw new RuntimeException("配置不完整");
      }
      // 2. 加载代理规则（格式：proxy.N.xxx，N从1开始）
      int proxyIndex = 1;
      while (true) {
        String proxyId = props.getProperty("proxy." + proxyIndex + ".proxyId");
        if (proxyId == null) break; // 没有更多代理规则
        ProxyConfig proxy = new ProxyConfig();
        proxy.setProxyId(proxyId);
        proxy.setLocalIp(props.getProperty("proxy." + proxyIndex + ".localIp"));
        proxy.setLocalPort(Integer.parseInt(
            props.getProperty("proxy." + proxyIndex + ".localPort")));
        proxy.setRemotePort(Integer.parseInt(
            props.getProperty("proxy." + proxyIndex + ".remotePort")));
        config.getProxies().add(proxy);
        proxyIndex++;
      }
      log.info("客户端配置加载完成：服务端={}:{}，代理规则{}条",
          config.getServerHost(), config.getServerPort(), config.getProxies().size());
    } catch (IOException e) {
      log.error("加载配置文件失败", e);
      throw new RuntimeException("配置加载失败", e);
    } catch (NumberFormatException e) {
      log.error("端口配置格式错误", e);
      throw new RuntimeException("配置格式错误", e);
    }
    return config;
  }
}
