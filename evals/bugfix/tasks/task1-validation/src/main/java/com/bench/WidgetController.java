package com.bench;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/widgets")
public class WidgetController {

    // Creates a widget. Invalid input should be rejected with HTTP 400.
    @PostMapping
    public ResponseEntity<String> create(@RequestBody CreateWidgetRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body("created:" + req.name());
    }
}
