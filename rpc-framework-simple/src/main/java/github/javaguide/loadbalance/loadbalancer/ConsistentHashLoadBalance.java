package github.javaguide.loadbalance.loadbalancer;

import github.javaguide.loadbalance.AbstractLoadBalance;
import github.javaguide.remoting.dto.RpcRequest;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * refer to dubbo consistent hash load balance: https://github.com/apache/dubbo/blob/2d9583adf26a2d8bd6fb646243a9fe80a77e65d5/dubbo-cluster/src/main/java/org/apache/dubbo/rpc/cluster/loadbalance/ConsistentHashLoadBalance.java
 * 一致性哈希算法
 * @author RicardoZ
 * @createTime 2020年10月20日 18:15:20
 */
@Slf4j
public class ConsistentHashLoadBalance extends AbstractLoadBalance {
    // 用来存放 服务名称与 一致性哈希选择器的映射
    private final ConcurrentHashMap<String, ConsistentHashSelector> selectors = new ConcurrentHashMap<>();

    @Override
    protected String doSelect(List<String> serviceAddresses, RpcRequest rpcRequest) { // 传入服务地址列表与请求对象
        int identityHashCode = System.identityHashCode(serviceAddresses); // 认证哈希值
        // build rpc service name by rpcRequest 找到该请求对应的服务名称
        String rpcServiceName = rpcRequest.getRpcServiceName();
        ConsistentHashSelector selector = selectors.get(rpcServiceName); // 获取服务名称对应的选择器
        // check for updates
        if (selector == null || selector.identityHashCode != identityHashCode) { // 选择器未创建或者服务列表改变，重新创建选择器
            selectors.put(rpcServiceName, new ConsistentHashSelector(serviceAddresses, 160, identityHashCode));
            selector = selectors.get(rpcServiceName);
        }
        return selector.select(rpcServiceName + Arrays.stream(rpcRequest.getParameters()));
    }

    @Override
    protected String selectServiceAddress(Map<String, String> addresStatMap, RpcRequest rpcRequest) { // 弃用
        return null;
    }

    static class ConsistentHashSelector {
        /**
         * 负载均衡一般是读多写少（新增或者删除节点毕竟少数），在读多写少的情况下，我们容易想到用二叉树来实现，在java中一般直接可以用TreeMap来实现，TreeMap是由红黑树实现的，很适合作为hash环的存储结构
         */
        private final TreeMap<Long, String> virtualInvokers; // 哈希环

        private final int identityHashCode; // 服务列表认证哈希值，用于比对服务列表是否改变

        // 选择器构造函数, 传入服务地址列表，
        ConsistentHashSelector(List<String> invokers, int replicaNumber, int identityHashCode) {
            this.virtualInvokers = new TreeMap<>();
            this.identityHashCode = identityHashCode;

            for (String invoker : invokers) { // 遍历服务地址，计算其在哈希环中的位置，并将其放入哈希环
                for (int i = 0; i < replicaNumber / 4; i++) {
                    byte[] digest = md5(invoker + i); // 计算地址+i的md5值
                    for (int h = 0; h < 4; h++) {
                        long m = hash(digest, h); // 计算哈希值
                        virtualInvokers.put(m, invoker); // 将服务节点放入哈希环
                    }
                }
            }
        }

        static byte[] md5(String key) {
            MessageDigest md;
            try {
                md = MessageDigest.getInstance("MD5");
                byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
                md.update(bytes);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }

            return md.digest();
        }

        static long hash(byte[] digest, int idx) {
            return ((long) (digest[3 + idx * 4] & 255) << 24 | (long) (digest[2 + idx * 4] & 255) << 16 | (long) (digest[1 + idx * 4] & 255) << 8 | (long) (digest[idx * 4] & 255)) & 4294967295L;
        }

        public String select(String rpcServiceKey) {
            byte[] digest = md5(rpcServiceKey);
            return selectForKey(hash(digest, 0));
        }

        public String selectForKey(long hashCode) {
            Map.Entry<Long, String> entry = virtualInvokers.tailMap(hashCode, true).firstEntry();

            if (entry == null) {
                entry = virtualInvokers.firstEntry();
            }

            return entry.getValue();
        }
    }
}
