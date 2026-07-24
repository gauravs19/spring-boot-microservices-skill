package com.bench;

import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/widgets")
public class WidgetController {

    private final Map<Long, String> store = new ConcurrentHashMap<>(Map.of(1L, "bolt"));

    // Returns the widget name. A request for a widget that does not exist
    // should result in HTTP 404 Not Found (not a 500).
    @GetMapping("/{id}")
    public String get(@PathVariable Long id) {
        return Optional.ofNullable(store.get(id)).get();
    }
}
