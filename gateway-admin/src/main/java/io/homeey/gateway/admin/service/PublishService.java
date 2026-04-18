package io.homeey.gateway.admin.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PublishService {
    private final AtomicLong version = new AtomicLong(0);

    public Map<String, Object> publish() {
        long next = version.incrementAndGet();
        return Map.of(
                "version", "v" + next,
                "publishedAt", Instant.now().toString()
        );
    }
}
