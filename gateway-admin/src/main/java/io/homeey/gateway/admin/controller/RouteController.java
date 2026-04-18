package io.homeey.gateway.admin.controller;

import io.homeey.gateway.admin.model.PublishRequest;
import io.homeey.gateway.admin.service.PublishService;
import io.homeey.gateway.plugin.api.PluginBinding;
import io.homeey.gateway.plugin.api.PublishRecord;
import io.homeey.gateway.plugin.api.PolicySet;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    private final List<PublishRequest.RouteItem> routes = new CopyOnWriteArrayList<>();
    private final List<PluginBinding> pluginBindings = new CopyOnWriteArrayList<>();
    private final PublishService publishService;

    public RouteController(PublishService publishService) {
        this.publishService = publishService;
    }

    @GetMapping
    public List<PublishRequest.RouteItem> list() {
        return new ArrayList<>(routes);
    }

    @PostMapping
    public PublishRequest.RouteItem create(@RequestBody PublishRequest.RouteItem route) {
        routes.add(route);
        return route;
    }

    @PostMapping("/plugins")
    public List<PluginBinding> bindPlugins(
            @RequestBody List<PluginBinding> bindings
    ) {
        pluginBindings.clear();
        if (bindings != null) {
            pluginBindings.addAll(bindings);
        }
        return new ArrayList<>(pluginBindings);
    }

    @PostMapping("/publish")
    public Map<String, Object> publish() {
        PublishRequest request = new PublishRequest(
                new ArrayList<>(routes),
                new ArrayList<>(pluginBindings),
                new PolicySet(Map.of()),
                "admin",
                "publish routes and plugins"
        );
        return publishService.publish(request);
    }

    @GetMapping("/publish-records")
    public List<PublishRecord> publishRecords() {
        return publishService.listRecords();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidationError(IllegalArgumentException ex) {
        return Map.of(
                "error", "BAD_REQUEST",
                "message", ex.getMessage()
        );
    }
}
