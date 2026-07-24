package com.acme.orders;

import javax.persistence.*;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String customerId;
    private String status;

    @OneToMany(mappedBy = "order", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    private List<OrderLine> lines;

    // getters / setters omitted for brevity
    public Long getId() { return id; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String c) { this.customerId = c; }
    public String getStatus() { return status; }
    public void setStatus(String s) { this.status = s; }
    public List<OrderLine> getLines() { return lines; }
}
