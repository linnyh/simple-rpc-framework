import github.javaguide.HelloService;
import github.javaguide.annotation.RpcScan;
import github.javaguide.config.RpcServiceConfig;
import github.javaguide.remoting.transport.netty.server.NettyRpcServer;
import github.javaguide.serviceimpl.HelloServiceImpl2;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 通过 RpcService 注解自动注册服务
 * Server: Automatic registration service via @RpcService annotation
 *
 * @author shuang.kou
 * @createTime 2020年05月10日 07:25:00
 */
@RpcScan(basePackage = {"github.javaguide"})
public class NettyServerMain {
    public static void main(String[] args) {
        // Register service via annotation
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(NettyServerMain.class);
        NettyRpcServer nettyRpcServer = (NettyRpcServer) applicationContext.getBean("nettyRpcServer"); // Netty 实现的服务端
        // Register service manually
        HelloService helloService2 = new HelloServiceImpl2(); // 创建服务实现类的对象
        RpcServiceConfig rpcServiceConfig = RpcServiceConfig.builder()
                .group("test2").version("version2").service(helloService2).build(); // 包装配置服务
        nettyRpcServer.registerService(rpcServiceConfig); // 将服务注册到注册中心
        nettyRpcServer.setServerConfig(rpcServiceConfig); // 服务保留它自己的配置信息
        nettyRpcServer.start(); // 启动服务
    }
}
