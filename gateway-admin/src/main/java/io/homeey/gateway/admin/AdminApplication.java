package io.homeey.gateway.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关管理应用启动类。
 * <p>
 * 提供路由管理、插件配置、发布记录等管理功能的REST API。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
@SpringBootApplication
public class AdminApplication {
    /**
     * 应用入口方法。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
