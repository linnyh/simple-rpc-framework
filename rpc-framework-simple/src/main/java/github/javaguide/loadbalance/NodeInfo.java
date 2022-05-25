package github.javaguide.loadbalance;

import org.apache.zookeeper.data.Stat;

/**
 * @author Lin YuHang
 * @date 2022/5/25 10:21
 */
public class NodeInfo {
    private Stat stat;
    private int connectNumber;

    public NodeInfo(Stat stat, int connectNumber) {
        this.stat = stat;
        this.connectNumber = connectNumber;
    }
}
