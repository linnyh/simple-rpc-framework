package github.javaguide.spring;

import github.javaguide.annotation.RpcScan;
import github.javaguide.annotation.RpcService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * ImportBeanDefinitionRegistrar 接口实现bean的动态注入
 * ImportBeanDefinitionRegistrar，我们一般会实现ImportBeanDefinitionRegistrar类，然后配合一个自定义的注解一起使用。而且在注解类上@Import我们的这个实现类。
 * 通过自定义注解的配置，拿到注解的一些元数据。然后在ImportBeanDefinitionRegistrar的实现类里面做相应的逻辑处理，比如把自定义注解标记的类添加到Spring IOC容器里面去。
 */

/**
 * scan and filter specified annotations
 * 扫描并过滤带有特殊注解的Bean
 * 所有实现了该接口（ImportBeanDefinitionRegistrar）的类的都会被ConfigurationClassPostProcessor处理，
 * @author shuang.kou
 * @createTime 2020年08月10日 22:12:00
 */
@Slf4j
public class CustomScannerRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {
    private static final String SPRING_BEAN_BASE_PACKAGE = "github.javaguide";
    private static final String BASE_PACKAGE_ATTRIBUTE_NAME = "basePackage";
    private ResourceLoader resourceLoader;

    @Override
    public void setResourceLoader(@NonNull ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Spring 启动时会运行该方法，利用ClassPathBeanDefinitionScanner扫描并注册指定注解的BeanDefinition
     * @param annotationMetadata 注解的元数据
     * @param beanDefinitionRegistry
     */
    @Override
    public void registerBeanDefinitions(AnnotationMetadata annotationMetadata, BeanDefinitionRegistry beanDefinitionRegistry) {
        /*
         * 根据注解的给定注释元数据，根据需要注册bean定义
         * @param importingClassMetadata 可以拿到@Import的这个class的注解的元数据
         * @param registry BeanDefinitionRegistry 就可以拿到目前所有注册的BeanDefinition，然后可以对这些BeanDefinition进行额外的修改或增强。
         */

        // 1. 获取注解定义的属性及其值，即从RpcScan 注解获取到我们要搜索的包路径 basePacage
        AnnotationAttributes rpcScanAnnotationAttributes = AnnotationAttributes.fromMap(annotationMetadata.getAnnotationAttributes(RpcScan.class.getName()));
        String[] rpcScanBasePackages = new String[0];
        if (rpcScanAnnotationAttributes != null) { // 包路径字符串数组不为空
            // get the value of the basePackage property
            rpcScanBasePackages = rpcScanAnnotationAttributes.getStringArray(BASE_PACKAGE_ATTRIBUTE_NAME); // 获取 RpcScan 的basePackage属性包路径字符串数组
        }
        if (rpcScanBasePackages.length == 0) { // 没有指定包路径，默认扫描RpcScan所在的当前包
            rpcScanBasePackages = new String[]{((StandardAnnotationMetadata) annotationMetadata).getIntrospectedClass().getPackage().getName()};
        }
        // Scan the RpcService annotation 扫描器
        CustomScanner rpcServiceScanner = new CustomScanner(beanDefinitionRegistry, RpcService.class); // CustomScanner 继承了 ClassPathBeanDefinitionScanner
        // Scan the Component annotation
        CustomScanner springBeanScanner = new CustomScanner(beanDefinitionRegistry, Component.class);

        if (resourceLoader != null) {
            rpcServiceScanner.setResourceLoader(resourceLoader);
            springBeanScanner.setResourceLoader(resourceLoader);
        }

        // 找到指定包路径下所有添加了 RpcService 以及 Component 注解的类，并把这些类添加到IOC容器中
        int springBeanAmount = springBeanScanner.scan(SPRING_BEAN_BASE_PACKAGE);
        log.info("springBeanScanner扫描的数量 [{}]", springBeanAmount);
        int rpcServiceCount = rpcServiceScanner.scan(rpcScanBasePackages);
        log.info("rpcServiceScanner扫描的数量 [{}]", rpcServiceCount);
    }
}
