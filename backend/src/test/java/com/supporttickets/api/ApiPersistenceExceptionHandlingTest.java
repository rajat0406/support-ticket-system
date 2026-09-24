package com.supporttickets.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.supporttickets.application.TicketService;

@WebMvcTest(TicketController.class)
class ApiPersistenceExceptionHandlingTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketService tickets;

    @Test
    void persistenceFailureIs500WithoutInternals() throws Exception {
        when(tickets.get(ID)).thenThrow(new DataAccessResourceFailureException(
                "Unable to acquire JDBC Connection jdbc:postgresql://secret-host/db"));
        mockMvc.perform(get("/api/tickets/" + ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.currentStatus").doesNotExist())
                .andExpect(jsonPath("$.requestedStatus").doesNotExist())
                .andExpect(content().string(not(containsString("jdbc:"))))
                .andExpect(content().string(not(containsString("postgresql"))))
                .andExpect(content().string(not(containsString("secret-host"))))
                .andExpect(content().string(not(containsString("DataAccess"))))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    void optimisticLockIs409WithoutSql() throws Exception {
        when(tickets.transition(any(), any())).thenThrow(new OptimisticLockingFailureException(
                "Row was updated or deleted by another transaction: UPDATE ticket SET status"));
        mockMvc.perform(post("/api/tickets/" + ID + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedStatus\":\"IN_PROGRESS\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.message").value("The ticket was updated by another request."))
                .andExpect(jsonPath("$.currentStatus").doesNotExist())
                .andExpect(content().string(not(containsString("UPDATE ticket"))))
                .andExpect(content().string(not(containsString("Exception"))));
    }
}
