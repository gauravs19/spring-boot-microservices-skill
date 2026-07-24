package com.acme.orders;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private OrderRepository orderRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping
    public List<Order> getAll() {
        return orderRepository.findAll();
    }

    @GetMapping("/{id}")
    public Order getOne(@PathVariable Long id) {
        return orderRepository.findById(id).get();
    }

    @PostMapping
    @Transactional
    public Order create(@RequestBody Order order) {
        // call inventory service to check stock
        String url = "http://inventory-service/api/stock/" + order.getCustomerId();
        try {
            Object stock = restTemplate.getForObject(url, Object.class);
            order.setStatus("CONFIRMED");
            return orderRepository.save(order);
        } catch (Exception e) {
            throw new RuntimeException("Order creation failed: " + e.getMessage(), e);
        }
    }
}
