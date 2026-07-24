package com.bench;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WidgetController.class)
class WidgetControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void validRequestIsCreated() throws Exception {
        mvc.perform(post("/widgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"bolt\",\"quantity\":5}"))
                .andExpect(status().isCreated());
    }

    @Test
    void invalidRequestIsRejectedWith400() throws Exception {
        // Blank name and non-positive quantity must be rejected with 400 Bad Request.
        mvc.perform(post("/widgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"quantity\":0}"))
                .andExpect(status().isBadRequest());
    }
}
