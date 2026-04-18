package io.homeey.gateway.common.spi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SPI（Service Provider Interface）注解，用于标识可扩展的服务接口。
 * <p>
 * 该注解应用于接口上，声明该接口支持通过 {@link ExtensionLoader} 进行扩展加载。
 * 可以指定默认的扩展实现名称。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SPI {
    /**
     * 默认扩展实现的名称。
     * <p>
     * 当调用 {@link ExtensionLoader#getDefaultExtension()} 时，将返回此名称对应的扩展实例。
     * </p>
     *
     * @return 默认扩展名称，默认为空字符串
     */
    String value() default "";
}
