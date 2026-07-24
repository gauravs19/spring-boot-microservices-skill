package com.bench;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controllers {

    // Public, unauthenticated health check. Must be reachable without credentials.
    @GetMapping("/public/health")
    public String health() {
        return "ok";
    }

    // Protected resource. Must require authentication (401 when anonymous).
    @GetMapping("/admin/data")
    public String data() {
        return "secret";
    }
}
