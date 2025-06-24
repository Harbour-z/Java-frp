package com.frp.client.boot;

import com.frp.client.config.ClientConfig;
import com.frp.client.config.ConfigLoader;
import com.frp.client.handler.ClientControlHandler;
import com.frp.common.codec.FrpFrameDecoder;
import com.frp.common.codec.FrpFrameEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class FrpClient {
  private final ClientConfig clientConfig; // 客户端配置（服务端地址、代理规则等）
  private EventLoopGroup workerGroup;      // Netty IO线程组
  private ClientControlHandler controlHandler; // 控制连接处理器（核心业务逻辑）
  public FrpClient(ClientConfig clientConfig) {
    this.clientConfig = clientConfig;
  }
  /**
   * 启动客户端：初始化Netty并连接服务端控制端口
   */
  public void start() {
    workerGroup = new NioEventLoopGroup(); // 创建IO线程组（客户端通常只需要workerGroup）
    controlHandler = new ClientControlHandler(clientConfig); // 创建控制连接处理器
    try {
      Bootstrap bootstrap = new Bootstrap(); // Netty客户端启动器
      bootstrap.group(workerGroup)
          .channel(NioSocketChannel.class) // 使用NIO Socket通道
          .option(ChannelOption.SO_KEEPALIVE, true) // 开启TCP保活机制
          .option(ChannelOption.TCP_NODELAY, true) // 禁用Nagle算法（减少延迟）
          .handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
              // 配置客户端ChannelPipeline（责任链）
              ch.pipeline()
                  // 1. 超时检测：5秒未读取到服务端数据（心跳超时），触发IdleStateEvent
                  .addLast(new IdleStateHandler(5, 0, 0, TimeUnit.SECONDS))
                  // 2. 协议帧解码（解决TCP粘包/拆包）
                  .addLast(new FrpFrameDecoder())
                  // 3. 协议帧编码
                  .addLast(new FrpFrameEncoder())
                  // 4. 控制连接业务处理器（核心逻辑：注册代理、心跳、数据转发）
                  .addLast(controlHandler);
            }
          });
      // 连接服务端控制端口（如1.2.3.4:7000）
      ChannelFuture future = bootstrap.connect(
          clientConfig.getServerHost(),
          clientConfig.getServerPort()
      ).sync();
      log.info("客户端启动成功，已连接服务端：{}:{}",
          clientConfig.getServerHost(), clientConfig.getServerPort());
      // 阻塞等待控制连接关闭（客户端主逻辑在此期间通过Netty事件驱动运行）
      future.channel().closeFuture().sync();
    } catch (InterruptedException e) {
      log.error("客户端启动失败或被中断", e);
      Thread.currentThread().interrupt(); // 恢复中断状态
    } finally {
      // 优雅关闭线程组，释放资源
      if (workerGroup != null) {
        workerGroup.shutdownGracefully();
      }
      log.info("客户端已关闭");
    }
  }
  /**
   * 主方法：程序入口，加载配置并启动客户端
   */
  public static void main(String[] args) {
    try {
      // 1. 加载客户端配置（从classpath下的frpc.properties）
      ClientConfig config = ConfigLoader.load();
      // 2. 启动客户端
      new FrpClient(config).start();
    } catch (Exception e) {
      log.error("客户端初始化失败，程序退出", e);
      System.exit(1); // 配置加载失败时，直接退出程序
    }
  }
}
