package github.javaguide.remoting.transport.netty.server;

import github.javaguide.registry.zk.util.CuratorUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;


import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Lin YuHang
 * @date 2022/5/25 10:47
 * 当哟新的连接时，会更新Znode节点信息
 */
@Slf4j
public class ConnectServerHandler extends ChannelInboundHandlerAdapter {
    private AtomicInteger connectNum;
    private CuratorFramework zkClient;
    private String path;

    public ConnectServerHandler(AtomicInteger connectNum, CuratorFramework zkClient, String path) {
        this.connectNum = connectNum;
        this.zkClient = zkClient;
        this.path = path;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext channelHandlerContext) throws Exception {
        super.channelRegistered(channelHandlerContext);
        connectNum.incrementAndGet(); // 新增连接
        CuratorUtils.setNodeData(this.path, connectNum.toString(), zkClient);
        log.info("当前连接数[{}]", connectNum.get());
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext channelHandlerContext) throws Exception {
        super.channelUnregistered(channelHandlerContext);
        connectNum.decrementAndGet(); // 减少连接
        CuratorUtils.setNodeData(this.path, connectNum.toString(), zkClient);
        log.info("当前链接数[{}]", connectNum.get());
    }
}
