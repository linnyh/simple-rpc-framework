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
        if (connectNum.incrementAndGet() % 100 == 0) {
            log.info("当前连接数[{}]", connectNum.get());
        }
        CuratorUtils.setNodeData(this.path, connectNum.toString(), zkClient);
        log.info("当前连接数[{}]", connectNum.get());
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext channelHandlerContext) throws Exception {
        super.channelUnregistered(channelHandlerContext);
        if (connectNum.decrementAndGet() % 100 == 0) {
            log.info("当前链接数[{}]", connectNum.get());
        }
        CuratorUtils.setNodeData(this.path, connectNum.toString(), zkClient);
        log.info("当前链接数[{}]", connectNum.get());
    }
}
