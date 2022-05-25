package github.javaguide.spring;

import github.javaguide.annotation.RpcReference;
import github.javaguide.annotation.RpcService;
import github.javaguide.config.RpcServiceConfig;
import github.javaguide.extension.ExtensionLoader;
import github.javaguide.factory.SingletonFactory;
import github.javaguide.provider.ServiceProvider;
import github.javaguide.provider.impl.ZkServiceProviderImpl;
import github.javaguide.proxy.RpcClientProxy;
import github.javaguide.remoting.transport.RpcRequestTransport;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

/**
 * BeanPostProcessor接口有两个回调方法。当一个BeanPostProcessor的实现类注册到Spring IOC容器后，
 * 对于该Spring IOC容器所创建的每个bean实例在初始化方法（如afterPropertiesSet和任意已声明的init方法）调用前，
 * 将会调用BeanPostProcessor中的postProcessBeforeInitialization方法，而在bean实例初始化方法调用完成后，
 * 则会调用BeanPostProcessor中的postProcessAfterInitialization方法，整个调用顺序可以简单示意如下：
 *
 * --> Spring IOC容器实例化Bean
 * --> 调用BeanPostProcessor的postProcessBeforeInitialization方法
 * --> 调用bean实例的初始化方法
 * --> 调用BeanPostProcessor的postProcessAfterInitialization方法
 *
 * 可以看到，Spring容器通过BeanPostProcessor给了我们一个机会对Spring管理的bean进行再加工。
 * 比如：我们可以修改bean的属性，可以给bean生成一个动态代理实例等等。
 * 一些Spring AOP的底层处理也是通过实现BeanPostProcessor来执行代理包装逻辑的。
 *
 * 链接：https://www.jianshu.com/p/1417eefd2ab1
 */

/**
 * call this method before creating the bean to see if the class is annotated
 * 借用spring容器来读取服务注解
 * @author shuang.kou
 * @createTime 2020年07月14日 16:42:00
 */
@Slf4j
@Component
public class SpringBeanPostProcessor implements BeanPostProcessor {

    private final ServiceProvider serviceProvider;
    private final RpcRequestTransport rpcClient;

    public SpringBeanPostProcessor() {
        this.serviceProvider = SingletonFactory.getInstance(ZkServiceProviderImpl.class);
        this.rpcClient = ExtensionLoader.getExtensionLoader(RpcRequestTransport.class).getExtension("netty");
    }

    /**
     * Spring Bean 初始化方法调用前被调用, 发布服务
     * @param bean
     * @param beanName
     * @return
     * @throws BeansException
     */
    @SneakyThrows
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean.getClass().isAnnotationPresent(RpcService.class)) { // 判断是否为服务类
            log.info("[{}] is annotated with  [{}]", bean.getClass().getName(), RpcService.class.getCanonicalName());
            // get RpcService annotation
            RpcService rpcService = bean.getClass().getAnnotation(RpcService.class);
            // build RpcServiceProperties 构建服务配置类
            RpcServiceConfig rpcServiceConfig = RpcServiceConfig.builder()
                    .group(rpcService.group())
                    .version(rpcService.version())
                    .data("0")
                    .service(bean).build();
            serviceProvider.publishService(rpcServiceConfig); // 发布服务，ZkRegistry
        }
        return bean;
    }

    /**
     * Spring Bean 初始化方法调用之后调用这个方法, 创建客户端动态代理对象
     * @param bean
     * @param beanName
     * @return
     * @throws BeansException
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = bean.getClass();
        Field[] declaredFields = targetClass.getDeclaredFields(); // 获取目标类的所有字段
        for (Field declaredField : declaredFields) { // 判断字段上是否有 @RpcReference 注解
            RpcReference rpcReference = declaredField.getAnnotation(RpcReference.class);
            if (rpcReference != null) {
                RpcServiceConfig rpcServiceConfig = RpcServiceConfig.builder()
                        .group(rpcReference.group())
                        .version(rpcReference.version()).build();
                RpcClientProxy rpcClientProxy = new RpcClientProxy(rpcClient, rpcServiceConfig); // 创建客户端代理类
                Object clientProxy = rpcClientProxy.getProxy(declaredField.getType()); // 获取字段类型Class，获取代理类实例对象
                declaredField.setAccessible(true); // 当字段修饰符为 private 时需要加上这句话
                try {
                    declaredField.set(bean, clientProxy); // 向Bean对象的属性设置新值，即将客户端类设置为客户端代理类
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

        }
        return bean;
    }
}
