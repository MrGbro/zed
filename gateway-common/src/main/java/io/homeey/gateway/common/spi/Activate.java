package io.homeey.gateway.common.spi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 激活注解，用于控制SPI扩展的自动激活条件。
 * <p>
 * 该注解应用于SPI扩展实现类上，指定在什么条件下该扩展应该被自动加载和激活。
 * 支持基于分组、条件键和表达式的激活控制。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Activate {
    /**
     * 激活分组，指定该扩展属于哪些分组。
     * <p>
     * 当调用 {@link ExtensionLoader#getActivateExtensions(String, java.util.function.Function)} 时，
     * 只有分组匹配的扩展才会被返回。
     * </p>
     *
     * @return 分组名称数组，默认为空数组表示属于所有分组
     */
    String[] group() default {};

    /**
     * 激活顺序，数值越小优先级越高。
     * <p>
     * 当多个扩展同时激活时，按照order值从小到大排序。
     * </p>
     *
     * @return 顺序值，默认为0
     */
    int order() default 0;

    /**
     * 条件键数组，指定需要检查的属性键。
     * <p>
     * 配合 {@link #matchAll()} 使用：
     * <ul>
     *   <li>matchAll=true：所有键都必须存在才激活</li>
     *   <li>matchAll=false：任意一个键存在即可激活</li>
     * </ul>
     * </p>
     *
     * @return 条件键数组，默认为空数组表示不检查条件键
     */
    String[] conditionKeys() default {};

    /**
     * 条件表达式数组，支持更复杂的激活条件。
     * <p>
     * 支持的表达式格式：
     * <ul>
     *   <li>{@code key=value}：键等于指定值</li>
     *   <li>{@code key!=value}：键不等于指定值</li>
     *   <li>{@code key}：键存在且值为true</li>
     *   <li>{@code !key}：键不存在或值为false</li>
     * </ul>
     * 配合 {@link #matchAll()} 使用控制匹配逻辑。
     * </p>
     *
     * @return 条件表达式数组，默认为空数组表示不检查表达式
     */
    String[] conditions() default {};

    /**
     * 是否要求所有条件都匹配。
     * <p>
     * <ul>
     *   <li>true：所有条件键和表达式都必须满足（AND逻辑）</li>
     *   <li>false：任意一个条件键或表达式满足即可（OR逻辑）</li>
     * </ul>
     * </p>
     *
     * @return 是否要求全匹配，默认为true
     */
    boolean matchAll() default true;
}
