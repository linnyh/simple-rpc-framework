package github.javaguide.loadbalance.loadbalancer;

import github.javaguide.loadbalance.LoadBalance;
import github.javaguide.remoting.dto.RpcRequest;

import java.util.List;


/**
 * @author Lin YuHang
 * @date 2022/5/25 9:33
 */
public class MinConnectionLoadBalance implements LoadBalance {

    @Override
    public String selectServiceAddress(List<String> serviceUrlList, RpcRequest rpcRequest) {
        return null;
    }



}
