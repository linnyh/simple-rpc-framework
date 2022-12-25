package github.javaguide.loadbalance.loadbalancer;

import github.javaguide.loadbalance.AbstractLoadBalance;
import github.javaguide.registry.zk.util.CuratorUtils;
import github.javaguide.remoting.dto.RpcRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;

import java.util.List;
import java.util.Map;


/**
 * 最小连接数算法，选取具有最小连接数的服务地址
 * @author Lin YuHang
 * @date 2022/5/25 9:33
 */
@Slf4j
public class MinConnectionLoadBalance extends AbstractLoadBalance {

    @Override
    public String selectServiceAddress(List<String> serviceUrlList, RpcRequest rpcRequest) {
        CuratorFramework zkClient = CuratorUtils.getZkClient();
        String path = CuratorUtils.ZK_REGISTER_ROOT_PATH + '/' + rpcRequest.getRpcServiceName() + '/';
        int maxCnt = 100;
        String res = null;
        // find the service with minimal connection number
        for (String addr : serviceUrlList) {
            String connect = CuratorUtils.getNodeData(path + addr, zkClient);
            log.info("获取到服务[{}]的负载数据为[{}]", path + addr, connect);
            if (connect != null) {
                int connectNum = Integer.parseInt(connect);
                if (maxCnt >= connectNum) {
                    maxCnt = connectNum;
                    res = addr;
                }
            }
        }
        log.info("用最小连接数算法找到一个服务[{}]", res);
        return res;
    }

    @Override
    protected String doSelect(List<String> serviceAddresses, RpcRequest rpcRequest) {
        return null;
    }

    @Override
    protected String selectServiceAddress(Map<String, String> addressStatMap, RpcRequest rpcRequest) {
        return null;
    }


}
