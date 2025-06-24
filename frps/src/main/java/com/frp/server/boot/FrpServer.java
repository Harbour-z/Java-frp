package com.frp.server.boot;

import com.frp.common.codec.FrpFrameDecoder;
import com.frp.common.codec.FrpFrameEncoder;
import com.frp.server.config.ServerConfig;
import com.frp.server.handler.ServerControlHandler;
import com.frp.server.manager.ProxyManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * 服务端启动入口，初始化Netty服务端，绑定控制端口，处理客户端连接
 * @author Zhidong Zhang
 */
@Slf4j
public class FrpServer {
  private final ServerConfig config;
  private EventLoopGroup bossGroup;
  private EventLoopGroup workerGroup;

  public FrpServer(ServerConfig config) {
    this.config = config;
  }

  public void start() throws InterruptedException {
    try{
      ServerBootstrap bootstrap = new ServerBootstrap();
      bootstrap.group(bossGroup, workerGroup)
          .channel(NioServerSocketChannel.class) // 使用NIO通道
          .option(ChannelOption.SO_BACKLOG, 128) // 连接队列大小
          .childOption(ChannelOption.SO_KEEPALIVE, true) // 保持连接
          .handler(new LoggingHandler(LogLevel.INFO)) // 服务端日志（可选）
          .childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
              ch.pipeline()
                  // 心跳超时检测：60秒未读（客户端断连）触发事件
                  .addLast(new IdleStateHandler(0, 0, 60, TimeUnit.SECONDS))
                  // 协议帧解码（解决TCP粘包/拆包）
                  .addLast(new FrpFrameDecoder())
                  // 协议帧编码
                  .addLast(new FrpFrameEncoder())
                  // 控制连接业务处理器（核心）
                  .addLast(new ServerControlHandler(config.getAuthToken()));
            }
          });
      // 绑定控制端口（如7000），同步等待绑定完成
      ChannelFuture future = bootstrap.bind(config.getControlPort()).sync();
      log.info("服务端启动成功，控制端口：{}", config.getControlPort());
      // 等待服务端关闭（阻塞）
      future.channel().closeFuture().sync();
    } finally {
      // 优雅关闭线程组
      bossGroup.shutdownGracefully();
      workerGroup.shutdownGracefully();
      // 关闭所有代理
      ProxyManager.INSTANCE.shutdown();
      log.info("服务端已关闭");
    }
  }

  public static void main(String[] args) throws InterruptedException {
    // 加载配置
    ServerConfig config = new ServerConfig();
    //启动服务端
    new FrpServer(config).start();
  }

}
