package com.supporttickets.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supporttickets.domain.DomainErrorCodes;
import com.supporttickets.persistence.InMemoryTicketRepository;

@SpringBootTest
@AutoConfigureMockMvc
class ApiExceptionHandlingTest {

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

    @Test
    void ticketNotFoundIs404WithoutTransitionFields() throws Exception {
        mockMvc.perform(get("/api/tickets/00000000-0000-0000-0000-000000000099"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(DomainErrorCodes.TICKET_NOT_FOUND))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.currentStatus").doesNotExist())
                .andExpect(jsonPath("$.requestedStatus").doesNotExist())
                .andExpect(safeErrorBody());
    }

    @Test
    void malformedJsonIs400InvalidRequest() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(DomainErrorCodes.INVALID_REQUEST))
                .andExpect(jsonPath("$.message").value("Request body is invalid."))
                .andExpect(jsonPath("$.currentStatus").doesNotExist())
                .andExpect(safeErrorBody());
    }

    @Test
    void validationFailureOnBlankTitleIs400InvalidRequest() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(DomainErrorCodes.INVALID_REQUEST))
                .andExpect(jsonPath("$.currentStatus").doesNotExist())
                .andExpect(jsonPath("$.requestedStatus").doesNotExist())
                .andExpect(safeErrorBody());
    }

    @Test
    void invalidUuidIs400InvalidRequest() throws Exception {
        mockMvc.perform(get("/api/tickets/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(DomainErrorCodes.INVALID_REQUEST))
                .andExpect(jsonPath("$.currentStatus").doesNotExist())
                .andExpect(safeErrorBody());
    }

    @Test
    void malformedStatusLabelIs400InvalidStatusValue() throws Exception {
        String id = createTicket();
        mockMvc.perform(post("/api/tickets/" + id + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedStatus\":\"open\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(DomainErrorCodes.INVALID_STATUS_VALUE))
                .andExpect(jsonPath("$.currentStatus").doesNotExist())
                .andExpect(jsonPath("$.requestedStatus").doesNotExist())
                .andExpect(safeErrorBody());
    }

    @Test
    void illegalTransitionIs409WithCurrentAndRequestedStatus() throws Exception {
        String id = createTicket();
        mockMvc.perform(post("/api/tickets/" + id + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedStatus\":\"CLOSED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(DomainErrorCodes.ILLEGAL_STATUS_TRANSITION))
                .andExpect(jsonPath("$.message").value(containsString("not allowed")))
                .andExpect(jsonPath("$.currentStatus").value("OPEN"))
                .andExpect(jsonPath("$.requestedStatus").value("CLOSED"))
                .andExpect(safeErrorBody());
    }

    @Test
    void blankRequestedStatusIs400InvalidRequest() throws Exception {
        String id = createTicket();
        mockMvc.perform(post("/api/tickets/" + id + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedStatus\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(DomainErrorCodes.INVALID_REQUEST))
                .andExpect(jsonPath("$.currentStatus").doesNotExist())
                .andExpect(safeErrorBody());
    }

    @Test
    void statusOnCreateIs400StatusNotUpdatable() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New\",\"status\":\"CLOSED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(DomainErrorCodes.STATUS_NOT_UPDATABLE))
                .andExpect(jsonPath("$.currentStatus").doesNotExist())
                .andExpect(safeErrorBody());
    }

    @Test
    void statusOnFieldUpdateIs400StatusNotUpdatable() throws Exception {
        String id = createTicket();
        mockMvc.perform(patch("/api/tickets/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Still open\",\"status\":\"CLOSED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(DomainErrorCodes.STATUS_NOT_UPDATABLE))
                .andExpect(jsonPath("$.currentStatus").doesNotExist())
                .andExpect(safeErrorBody());
    }

    private String createTicket() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Fixture\",\"description\":\"Body\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        return body.get("id").asText();
    }

    private static ResultMatcher safeErrorBody() {
        return ResultMatcher.matchAll(
                content().string(not(containsString("Exception"))),
                content().string(not(containsString("at com."))),
                content().string(not(containsString("jdbc:"))),
                content().string(not(containsString("SQL"))),
                content().string(not(containsString("stackTrace")))
        );
    }
}
