package github.javaguide.remoting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

/**
 * @author shuang.kou
 * @createTime 2020年05月10日 08:24:00
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@ToString
public class RpcRequest implements Serializable { // 可序列化的rpc请求对象，使用的是jdk自带的序列化
    private static final long serialVersionUID = 1905122041950251207L;
    private String requestId;                   // 请求ID
    private String interfaceName;               // 接口名
    private String methodName;                  // 方法名
    private Object[] parameters;                // 方法参数
    private Class<?>[] paramTypes;              // 方法参数类型
    private String version;                     // 版本
    private String group;                       // 组别
    // 获得服务名称 即为 接口名+组别+版本名
    public String getRpcServiceName() {
        return this.getInterfaceName() + this.getGroup() + this.getVersion();
    }
}
