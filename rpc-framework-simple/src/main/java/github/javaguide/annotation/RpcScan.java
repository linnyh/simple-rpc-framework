package github.javaguide.annotation;

import github.javaguide.spring.CustomScannerRegistrar;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * scan custom annotations
 * 自定义扫描注解
 * @author shuang.kou
 * @createTime 2020年08月10日 21:42:00
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
// 使用Import导入自定义实现了ImportBeanDefinitionRegistrar接口的扫描类
@Import(CustomScannerRegistrar.class)
@Documented
public @interface RpcScan {
    String[] basePackage(); // 指定被扫描的包路径
}

