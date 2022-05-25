package github.javaguide.registry.zk;

import github.javaguide.registry.ServiceRegistry;
import github.javaguide.registry.zk.util.CuratorUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;

import java.net.InetSocketAddress;

/**
 * service registration  based on zookeeper
 * 基于zookeeper的服务注册中心
 * @author shuang.kou
 * @createTime 2020年05月31日 10:56:00
 */
@Slf4j
public class ZkServiceRegistryImpl implements ServiceRegistry {

    /**
     * 注册服务
     * @param rpcServiceName    rpc service name
     * @param inetSocketAddress service address
     */
    @Override
    public void registerService(String rpcServiceName, InetSocketAddress inetSocketAddress, Object data) {
        String servicePath = CuratorUtils.ZK_REGISTER_ROOT_PATH + "/" + rpcServiceName + inetSocketAddress.toString();
        CuratorFramework zkClient = CuratorUtils.getZkClient(); // zookeeper 实例
        CuratorUtils.createPersistentNode(zkClient, servicePath); // 新增节点
        CuratorUtils.setNodeData(servicePath, (String) data, zkClient); // 注册时更新节点数据
    }
}
