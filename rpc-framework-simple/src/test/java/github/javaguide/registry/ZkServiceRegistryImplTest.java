package github.javaguide.registry;

import github.javaguide.DemoRpcService;
import github.javaguide.DemoRpcServiceImpl;
import github.javaguide.config.RpcServiceConfig;
import github.javaguide.registry.zk.ZkServiceDiscoveryImpl;
import github.javaguide.registry.zk.ZkServiceRegistryImpl;
import github.javaguide.remoting.dto.RpcRequest;
import org.junit.jupiter.api.Test;
import sun.awt.image.ImageWatched;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @author shuang.kou
 * @createTime 2020年05月31日 16:25:00
 */
class ZkServiceRegistryImplTest {
    List<String> list = new ArrayList<>();
    List<String> list1 = new Vector<>();
    List<String> list2 = new LinkedList<>();
    Map<String, String> m = new ConcurrentHashMap<>();

    Set<String> set = new HashSet<>();
    SortedSet<String> sset = new TreeSet<>();
//    ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor();
//    ClassLoader cl = new ClassLoader() {
//    }

    @Test
    void should_register_service_successful_and_lookup_service_by_service_name() {
        ServiceRegistry zkServiceRegistry = new ZkServiceRegistryImpl();
        InetSocketAddress givenInetSocketAddress = new InetSocketAddress("127.0.0.1", 9333);
        DemoRpcService demoRpcService = new DemoRpcServiceImpl();
        RpcServiceConfig rpcServiceConfig = RpcServiceConfig.builder()
                .group("test2").version("version2").service(demoRpcService).build();
        zkServiceRegistry.registerService(rpcServiceConfig.getRpcServiceName(), givenInetSocketAddress, "10");
        ServiceDiscovery zkServiceDiscovery = new ZkServiceDiscoveryImpl();
        RpcRequest rpcRequest = RpcRequest.builder()
//                .parameters(args)
                .interfaceName(rpcServiceConfig.getServiceName())
//                .paramTypes(method.getParameterTypes())
                .requestId(UUID.randomUUID().toString())
                .group(rpcServiceConfig.getGroup())
                .version(rpcServiceConfig.getVersion())
                .build();
        InetSocketAddress acquiredInetSocketAddress = zkServiceDiscovery.lookupService(rpcRequest);
        assertEquals(givenInetSocketAddress.toString(), acquiredInetSocketAddress.toString());
    }
}
