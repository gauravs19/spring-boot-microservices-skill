package com.bench;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityTest {

    @Autowired
    MockMvc mvc;

    @Test
    void publicHealthIsOpenWithoutAuth() throws Exception {
        // The public health endpoint must be reachable anonymously (200).
        mvc.perform(get("/public/health"))
                .andExpect(status().isOk());
    }

    @Test
    void adminRequiresAuth() throws Exception {
        // The admin endpoint must reject anonymous access (401).
        mvc.perform(get("/admin/data"))
                .andExpect(status().isUnauthorized());
    }
}
