package io.homeey.gateway.admin.controller;

import io.homeey.gateway.common.spi.ExtensionLoader;
import io.homeey.gateway.plugin.api.GatewayFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
