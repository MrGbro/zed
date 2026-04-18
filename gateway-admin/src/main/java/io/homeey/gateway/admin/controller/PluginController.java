package io.homeey.gateway.admin.controller;

import io.homeey.gateway.common.spi.ExtensionLoader;
import io.homeey.gateway.plugin.api.GatewayFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 插件管理控制器，提供插件查询功能。
 * <p>
 * 该控制器用于列出系统中所有可用的网关过滤器插件。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
@RestController
@RequestMapping("/api/plugins")
public class PluginController {

    @GetMapping
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<>();
        ExtensionLoader<GatewayFilter> loader = ExtensionLoader.getExtensionLoader(GatewayFilter.class);
        for (String name : loader.getSupportedExtensions()) {
            result.add(Map.of("id", name, "enabled", true));
        }
        return result;
    }
}
