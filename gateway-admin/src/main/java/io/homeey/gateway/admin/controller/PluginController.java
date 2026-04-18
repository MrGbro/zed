package io.homeey.gateway.admin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/plugins")
public class PluginController {

    @GetMapping
    public List<Map<String, Object>> list() {
        return List.of(
                Map.of("id", "auth", "enabled", true),
                Map.of("id", "ratelimit", "enabled", true)
        );
    }
}
