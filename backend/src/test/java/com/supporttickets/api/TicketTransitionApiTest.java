package com.supporttickets.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supporttickets.domain.DomainErrorCodes;
import com.supporttickets.persistence.InMemoryTicketRepository;

@SpringBootTest
@AutoConfigureMockMvc
class TicketTransitionApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InMemoryTicketRepository tickets;

    @BeforeEach
    void clear() {
        tickets.clear();
    }

    @ParameterizedTest
    @CsvSource({
            "OPEN, IN_PROGRESS",
            "IN_PROGRESS, RESOLVED",
            "RESOLVED, CLOSED",
            "OPEN, CANCELLED",
            "IN_PROGRESS, CANCELLED"
    })
    void validTransitions(String from, String to) throws Exception {
        String id = ticketIn(from);
        mockMvc.perform(post("/api/tickets/" + id + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedStatus\":\"" + to + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(to));
        mockMvc.perform(get("/api/tickets/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(to));
    }

    @Test
    void fullHappyPath() throws Exception {
        String id = ticketIn("OPEN");
        transition(id, "IN_PROGRESS");
        transition(id, "RESOLVED");
        mockMvc.perform(post("/api/tickets/" + id + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedStatus\":\"CLOSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @ParameterizedTest
    @CsvSource({
            "CLOSED, OPEN",
            "RESOLVED, OPEN",
            "CANCELLED, OPEN",
            "OPEN, RESOLVED",
            "OPEN, CLOSED",
            "IN_PROGRESS, CLOSED",
            "IN_PROGRESS, OPEN",
            "RESOLVED, CANCELLED",
            "CLOSED, CANCELLED",
            "OPEN, OPEN"
    })
    void invalidTransitionsRejected(String from, String to) throws Exception {
        String id = ticketIn(from);
        mockMvc.perform(post("/api/tickets/" + id + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedStatus\":\"" + to + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(DomainErrorCodes.ILLEGAL_STATUS_TRANSITION))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.currentStatus").value(from))
                .andExpect(jsonPath("$.requestedStatus").value(to));
        mockMvc.perform(get("/api/tickets/" + id))
                .andExpect(jsonPath("$.status").value(from));
    }

    @Test
    void fieldUpdateCannotChangeStatus() throws Exception {
        String id = ticketIn("OPEN");
        mockMvc.perform(patch("/api/tickets/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"status\":\"CLOSED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(DomainErrorCodes.STATUS_NOT_UPDATABLE));
        mockMvc.perform(get("/api/tickets/" + id))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.title").value("Fixture"));
    }

    @Test
    void createCannotSetStatus() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New\",\"status\":\"CLOSED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(DomainErrorCodes.STATUS_NOT_UPDATABLE));
    }

    @Test
    void unknownStatusLabel() throws Exception {
        String id = ticketIn("OPEN");
        mockMvc.perform(post("/api/tickets/" + id + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedStatus\":\"open\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(DomainErrorCodes.INVALID_STATUS_VALUE));
    }

    @Test
    void blankRequestedStatus() throws Exception {
        String id = ticketIn("OPEN");
        mockMvc.perform(post("/api/tickets/" + id + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedStatus\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(DomainErrorCodes.INVALID_REQUEST));
    }

    @Test
    void missingTicket() throws Exception {
        mockMvc.perform(post("/api/tickets/00000000-0000-0000-0000-000000000099/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedStatus\":\"IN_PROGRESS\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(DomainErrorCodes.TICKET_NOT_FOUND));
    }

    @Test
    void repeatingValidTransitionIsSameStateAndRejected() throws Exception {
        String id = ticketIn("OPEN");
        transition(id, "IN_PROGRESS");
        mockMvc.perform(post("/api/tickets/" + id + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedStatus\":\"IN_PROGRESS\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(DomainErrorCodes.ILLEGAL_STATUS_TRANSITION));
    }

    private void transition(String id, String to) throws Exception {
        mockMvc.perform(post("/api/tickets/" + id + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedStatus\":\"" + to + "\"}"))
                .andExpect(status().isOk());
    }

    private String ticketIn(String status) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Fixture\",\"description\":\"Body\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        String id = body.get("id").asText();
        if ("OPEN".equals(status)) {
            return id;
        }
        if ("IN_PROGRESS".equals(status)) {
            transition(id, "IN_PROGRESS");
            return id;
        }
        if ("RESOLVED".equals(status)) {
            transition(id, "IN_PROGRESS");
            transition(id, "RESOLVED");
            return id;
        }
        if ("CLOSED".equals(status)) {
            transition(id, "IN_PROGRESS");
            transition(id, "RESOLVED");
            transition(id, "CLOSED");
            return id;
        }
        transition(id, "CANCELLED");
        return id;
    }
}
