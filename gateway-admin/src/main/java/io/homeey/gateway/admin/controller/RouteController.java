package io.homeey.gateway.admin.controller;

import io.homeey.gateway.admin.service.PublishService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/routes")
public class RouteController {
    private final List<Map<String, Object>> routes = new CopyOnWriteArrayList<>();
    private final PublishService publishService;

    public RouteController(PublishService publishService) {
        this.publishService = publishService;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return new ArrayList<>(routes);
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> route) {
        routes.add(route);
        return route;
    }

    @PostMapping("/publish")
    public Map<String, Object> publish() {
        return publishService.publish();
    }
}
