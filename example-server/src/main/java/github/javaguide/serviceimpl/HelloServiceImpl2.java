package github.javaguide.serviceimpl;

import github.javaguide.Hello;
import github.javaguide.HelloService;
import github.javaguide.annotation.RpcService;
import lombok.extern.slf4j.Slf4j;

/**
 * @author shuang.kou
 * @createTime 2020年05月10日 07:52:00
 */

// 服务端提供的api
@Slf4j
@RpcService(group = "test2", version = "version1") // 表示这是个rpc服务
public class HelloServiceImpl2 implements HelloService {

    static {
        System.out.println("HelloServiceImpl2被创建");
    }

    @Override
    public String hello(Hello hello) {
        log.info("HelloServiceImpl2收到: {}.", hello.getMessage());
        String result = "Hello description is " + hello.getDescription();
        log.info("HelloServiceImpl2返回: {}.", result);
        return result;
    }
}
