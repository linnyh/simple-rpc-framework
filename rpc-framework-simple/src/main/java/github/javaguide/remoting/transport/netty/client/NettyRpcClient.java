package github.javaguide.remoting.transport.netty.client;


import github.javaguide.enums.CompressTypeEnum;
import github.javaguide.enums.SerializationTypeEnum;
import github.javaguide.extension.ExtensionLoader;
import github.javaguide.factory.SingletonFactory;
import github.javaguide.registry.ServiceDiscovery;
import github.javaguide.remoting.constants.RpcConstants;
import github.javaguide.remoting.dto.RpcMessage;
import github.javaguide.remoting.dto.RpcRequest;
import github.javaguide.remoting.dto.RpcResponse;
import github.javaguide.remoting.transport.RpcRequestTransport;
import github.javaguide.remoting.transport.netty.codec.RpcMessageDecoder;
import github.javaguide.remoting.transport.netty.codec.RpcMessageEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * initialize and close Bootstrap object
 *
 * @author shuang.kou
 * @createTime 2020年05月29日 17:51:00
 */
@Slf4j
public final class NettyRpcClient implements RpcRequestTransport {
    private final ServiceDiscovery serviceDiscovery;
    private final UnprocessedRequests unprocessedRequests;
    private final ChannelProvider channelProvider;
    private final Bootstrap bootstrap;
    private final EventLoopGroup eventLoopGroup;
    private final AtomicInteger reconnectNum = new AtomicInteger(0); // 重连计数器

    public NettyRpcClient() {
        // initialize resources such as EventLoopGroup, Bootstrap
        eventLoopGroup = new NioEventLoopGroup(); // 时间循环组，默认线程数为 2 * cpu核心数
        bootstrap = new Bootstrap(); // 服务端用 ServerBootstrap()
        bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                //  The timeout period of the connection.
                //  If this time is exceeded or the connection cannot be established, the connection fails.
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        // If no data is sent to the server within 15 seconds, a heartbeat request is sent
                        // 利用心跳机制保持与服务端的长连接, 设置写超时时间为 5 秒，若5秒后没有写操作，NettyRpcClientHandler中的userEventTriggered函数将被触发
                        p.addLast(new IdleStateHandler(0, 5, 0, TimeUnit.SECONDS));
                        p.addLast(new RpcMessageEncoder());     // 编码器
                        p.addLast(new RpcMessageDecoder());     // 解码器
                        p.addLast(new NettyRpcClientHandler());
                    }
                });
        this.serviceDiscovery = ExtensionLoader.getExtensionLoader(ServiceDiscovery.class).getExtension("zk"); // zookeeper注册中心
        this.unprocessedRequests = SingletonFactory.getInstance(UnprocessedRequests.class);
        this.channelProvider = SingletonFactory.getInstance(ChannelProvider.class);
    }

    /**
     * connect server and get the channel ,so that you can send rpc message to server
     * 链接服务器获得通道，发送数据给服务器
     * @param inetSocketAddress server address
     * @return the channel
     */
    @SneakyThrows
    public Channel doConnect(InetSocketAddress inetSocketAddress) {
        if (reconnectNum.get() > 3) {
            throw new IllegalStateException();
        }
        CompletableFuture<Channel> completableFuture = new CompletableFuture<>();
        bootstrap.connect(inetSocketAddress).addListener((ChannelFutureListener) future -> { // 监听结果的回调函数
            if (future.isSuccess()) {
                log.info("客户端经过[{}]次尝试，成功连接到 [{}] !", reconnectNum.getAndSet(0), inetSocketAddress.toString());
                completableFuture.complete(future.channel());
            } else { // 链接失败，重试
//                throw new IllegalStateException();
                log.info("客户端连接服务器失败，5秒后尝试第[{}]次重新连接服务器！", reconnectNum.incrementAndGet());
                future.channel().eventLoop().schedule(() -> {
                    doConnect(inetSocketAddress);
                }, 5, TimeUnit.SECONDS);
            }
        });
        return completableFuture.get();
    }

    /**
     * 发送RPC请求
     * @param rpcRequest message body
     * @return
     */
    @Override
    public Object sendRpcRequest(RpcRequest rpcRequest) {
        // build return value 构建一个新的返回用来接收服务器返回
        CompletableFuture<RpcResponse<Object>> resultFuture = new CompletableFuture<>();
        // get server address 服务发现，通过负载均衡算法获取服务地址
        InetSocketAddress inetSocketAddress = serviceDiscovery.lookupService(rpcRequest);
        // get  server address related channel 连接服务端，获取服务地址的相关通道
        Channel channel = getChannel(inetSocketAddress);
        if (channel.isActive()) {
            // put unprocessed request 异步
            unprocessedRequests.put(rpcRequest.getRequestId(), resultFuture); // 请求被发送前，将其放入未处理请求map，requestId是唯一的
            RpcMessage rpcMessage = RpcMessage.builder().data(rpcRequest) // 封装请求信息
                    .codec(SerializationTypeEnum.HESSIAN.getCode())
                    .compress(CompressTypeEnum.GZIP.getCode())
                    .messageType(RpcConstants.REQUEST_TYPE).build();
            channel.writeAndFlush(rpcMessage).addListener((ChannelFutureListener) future -> { // 发送信息，添加处理返回的回调函数监听返回
                if (future.isSuccess()) { // 链接成功
                    log.info("客户端成功发送信息: [{}]", rpcMessage);
                } else { // 连接失败，关闭通道
                    future.channel().close();
                    resultFuture.completeExceptionally(future.cause());
                    log.error("客户端发送信息失败：", future.cause());
                }
            });
        } else {
            throw new IllegalStateException();
        }

        return resultFuture;
    }

    public Channel getChannel(InetSocketAddress inetSocketAddress) {
        Channel channel = channelProvider.get(inetSocketAddress); // 从 channelProvider 中查看是否已经有该channel
        if (channel == null) { // 如果没有，重新连接服务端获得channel
            channel = doConnect(inetSocketAddress); // 连接服务端
            channelProvider.set(inetSocketAddress, channel); // 将通道注册到channelProvider
        }
        return channel;
    }

    public void close() {
        eventLoopGroup.shutdownGracefully();
    }
}
