package github.javaguide.registry.zk.util;

import github.javaguide.enums.RpcConfigEnum;
import github.javaguide.loadbalance.NodeInfo;
import github.javaguide.registry.zk.entity.ServerInfo;
import github.javaguide.utils.PropertiesFileUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Curator(zookeeper client) utils
 *
 * @author shuang.kou
 * @createTime 2020年05月31日 11:38:00
 */
@Slf4j
public final class CuratorUtils {

    private static final int BASE_SLEEP_TIME = 1000;
    private static final int MAX_RETRIES = 3;
    public static final String ZK_REGISTER_ROOT_PATH = "/my-rpc";
    private static final Map<String, List<String>> SERVICE_ADDRESS_MAP = new ConcurrentHashMap<>(); // 服务名与服务地址列表映射，一个服务可以有好几个实例地址

    private static final Set<String> REGISTERED_PATH_SET = ConcurrentHashMap.newKeySet();
    private static CuratorFramework zkClient;
    private static final String DEFAULT_ZOOKEEPER_ADDRESS = "127.0.0.1:2181"; // zookeeper 默认启动端口

    private CuratorUtils() {
    }

    /**
     * Create persistent nodes. Unlike temporary nodes, persistent nodes are not removed when the client disconnects
     *
     * @param path node path
     */
    public static void createPersistentNode(CuratorFramework zkClient, String path) {
        try {
            if (REGISTERED_PATH_SET.contains(path) || zkClient.checkExists().forPath(path) != null) {
                log.info("The node already exists. The node is:[{}]", path);
            } else {
                //eg: /my-rpc/github.javaguide.HelloService/127.0.0.1:9999
                zkClient.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(path); // 创建临时节点，具有服务下线感知
                log.info("The node was created successfully. The node is:[{}]", path);
            }
            REGISTERED_PATH_SET.add(path);
        } catch (Exception e) {
            log.error("create persistent node for path [{}] fail", path);
        }
    }

    /**
     * Gets the children under a node
     * 获得某节点的子节点
     * @param rpcServiceName rpc service name eg:github.javaguide.HelloServicetest2version1
     * @return All child nodes under the specified node
     */
    public static List<String> getChildrenNodes(CuratorFramework zkClient, String rpcServiceName) {
        if (SERVICE_ADDRESS_MAP.containsKey(rpcServiceName)) {
            return SERVICE_ADDRESS_MAP.get(rpcServiceName);
        }
        List<String> result = null;
        String servicePath = ZK_REGISTER_ROOT_PATH + "/" + rpcServiceName; // 服务节点路径
        try {
            result = zkClient.getChildren().forPath(servicePath);
            SERVICE_ADDRESS_MAP.put(rpcServiceName, result); // 服务名与服务地址的映射
            registerWatcher(rpcServiceName, zkClient); // 监听服务的子节点（服务地址）
        } catch (Exception e) {
            log.error("get children nodes for path [{}] fail", servicePath);
        }
        return result;
    }

    /**
     * Empty the registry of data
     */
    public static void clearRegistry(CuratorFramework zkClient, InetSocketAddress inetSocketAddress) {
        REGISTERED_PATH_SET.stream().parallel().forEach(p -> {
            try {
                if (p.endsWith(inetSocketAddress.toString())) {
                    zkClient.delete().forPath(p);
                }
            } catch (Exception e) {
                log.error("clear registry for path [{}] fail", p);
            }
        });
        log.info("All registered services on the server are cleared:[{}]", REGISTERED_PATH_SET.toString());
    }

    public static CuratorFramework getZkClient() {
        // check if user has set zk address 检查是否已经设置地址
        Properties properties = PropertiesFileUtil.readPropertiesFile(RpcConfigEnum.RPC_CONFIG_PATH.getPropertyValue()); // 获取配置文件
        String zookeeperAddress = properties != null && properties.getProperty(RpcConfigEnum.ZK_ADDRESS.getPropertyValue()) != null ? properties.getProperty(RpcConfigEnum.ZK_ADDRESS.getPropertyValue()) : DEFAULT_ZOOKEEPER_ADDRESS;
        // if zkClient has been started, return directly
        if (zkClient != null && zkClient.getState() == CuratorFrameworkState.STARTED) {
            return zkClient;
        }
        // Retry strategy. Retry 3 times, and will increase the sleep time between retries.
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(BASE_SLEEP_TIME, MAX_RETRIES);
        zkClient = CuratorFrameworkFactory.builder()
                // the server to connect to (can be a server list)
                .connectString(zookeeperAddress)
                .retryPolicy(retryPolicy)
                .build();
        zkClient.start();
        try {
            // wait 30s until connect to the zookeeper 等待30s直到连接上zookeeper
            if (!zkClient.blockUntilConnected(30, TimeUnit.SECONDS)) {
                throw new RuntimeException("Time out waiting to connect to ZK!");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return zkClient;
    }

    /**
     * Registers to listen for changes to the specified node
     * 注册监听子节点
     * @param rpcServiceName rpc service name eg:github.javaguide.HelloServicetest2version
     */
    private static void registerWatcher(String rpcServiceName, CuratorFramework zkClient) throws Exception {
        String servicePath = ZK_REGISTER_ROOT_PATH + "/" + rpcServiceName;
        PathChildrenCache pathChildrenCache = new PathChildrenCache(zkClient, servicePath, true);
        // 服务地址该表的监听器
        PathChildrenCacheListener pathChildrenCacheListener = (curatorFramework, pathChildrenCacheEvent) -> { // 监听指定节点的子节点的监听器
            List<String> serviceAddresses = curatorFramework.getChildren().forPath(servicePath); // 获取指定服务的服务地址列表
            SERVICE_ADDRESS_MAP.put(rpcServiceName, serviceAddresses); // 更新服务列表
        };
        // 服务信息改变的监听器
        PathChildrenCacheListener pathChildrenMonitor = (curatorFramework, pathChildrenCacheEvent) -> {
            // 获取子节点的改变类型
            PathChildrenCacheEvent.Type type = pathChildrenCacheEvent.getType();
            // 若类型为数据修改
            if (type.equals(PathChildrenCacheEvent.Type.CHILD_UPDATED)) {
                log.info("Service info changed[{}]", pathChildrenCacheEvent.toString());
            }
        };
        pathChildrenCache.getListenable().addListener(pathChildrenCacheListener); // 添加监听器,用于更新服务地址列表
        pathChildrenCache.getListenable().addListener(pathChildrenMonitor);
        pathChildrenCache.start(); // 开启监听
    }

    /**
     * 获取子节点的节点数据
     * @param serviceUrlList
     * @param zkClient
     * @return
     * @throws Exception
     */
    public static Map<String, NodeInfo> getChildStats(List<String> serviceUrlList, CuratorFramework zkClient) throws Exception {
        String servicePath = ZK_REGISTER_ROOT_PATH + "/";
        Map<String, NodeInfo> serviceStateMap = new HashMap<>();
        // 更新服务节点信息
        for (String url : serviceUrlList) {
            Stat stat = new Stat();
            String data = new String(zkClient.getData().storingStatIn(stat).forPath(servicePath + "/" + url));
            serviceStateMap.put(url, new NodeInfo(stat, Integer.parseInt(data)));
        }
        return serviceStateMap;
    }

    /**
     * 更新节点数据
     * @param path
     * @param data
     * @param zkClient
     */
    public static void setNodeData(String path, String data, CuratorFramework zkClient) {
        try {
            Stat stat = new Stat();
            zkClient.setData().forPath(path, data.getBytes());
            log.info("节点数据被更新了！[{}], 数据为[{}]", path, new String(zkClient.getData().storingStatIn(stat).forPath(path)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getNodeData(String path, CuratorFramework zkClient) {
        String data = null;
        try {
            Stat stat = new Stat();
            data = new String(zkClient.getData().storingStatIn(stat).forPath(path));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return data;
    }


}
