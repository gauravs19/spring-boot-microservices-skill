package com.bench;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WidgetController.class)
class WidgetControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void existingWidgetReturns200() throws Exception {
        mvc.perform(get("/widgets/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("bolt"));
    }

    @Test
    void missingWidgetReturns404() throws Exception {
        // A widget that does not exist must produce 404 Not Found, not 500.
        mvc.perform(get("/widgets/999"))
                .andExpect(status().isNotFound());
    }
}
