package com.supporttickets.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supporttickets.domain.DomainErrorCodes;
import com.supporttickets.persistence.InMemoryTicketRepository;

@SpringBootTest
@AutoConfigureMockMvc
class TicketApiContractTest {

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
    void createReturns201AndOpenDetailDto() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": " Login page crashes ",
                                  "description": "Crash on submit",
                                  "priority": null,
                                  "assignee": null
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Login page crashes"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.comments", hasSize(0)));
    }

    @Test
    void listReturnsWrapperAndOmitsCommentsFromItems() throws Exception {
        createTicket("First ticket", "First body");
        createTicket("Second ticket", "Second body");

        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickets", hasSize(2)))
                .andExpect(jsonPath("$.tickets[0].id").isNotEmpty())
                .andExpect(jsonPath("$.tickets[0].status").value("OPEN"))
                .andExpect(jsonPath("$.tickets[0].comments").doesNotExist());
    }

    @Test
    void getReturnsTicketDetail() throws Exception {
        String id = createTicket("Login page crashes", "Crash on submit");

        mockMvc.perform(get("/api/tickets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("Login page crashes"))
                .andExpect(jsonPath("$.comments", hasSize(0)));
    }

    @Test
    void updateChangesOnlyAllowedFieldsAndSupportsExplicitNull() throws Exception {
        String id = createTicket("Old title", "Old description");

        mockMvc.perform(patch("/api/tickets/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "New title",
                                  "description": null,
                                  "priority": "HIGH",
                                  "assignee": "alex"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New title"))
                .andExpect(jsonPath("$.description").isEmpty())
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.assignee").value("alex"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void addCommentReturns201AndAppearsOnTicketWithoutChangingStatus() throws Exception {
        String id = createTicket("Login page crashes", "Crash on submit");

        mockMvc.perform(post("/api/tickets/{id}/comments", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":" Looking into it ","author":"alex"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.body").value("Looking into it"))
                .andExpect(jsonPath("$.author").value("alex"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        mockMvc.perform(get("/api/tickets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.comments", hasSize(1)))
                .andExpect(jsonPath("$.comments[0].body").value("Looking into it"));
    }

    @Test
    void keywordSearchUsesCaseInsensitivePartialTitleOrDescriptionMatch() throws Exception {
        createTicket("Login page crashes", "Crash on submit");
        createTicket("Password reset broken", "Email never arrives");
        createTicket("Login logo missing", "Asset 404");

        mockMvc.perform(get("/api/tickets").queryParam("q", "LOGIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickets", hasSize(2)));

        mockMvc.perform(get("/api/tickets").queryParam("q", "email"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickets", hasSize(1)))
                .andExpect(jsonPath("$.tickets[0].title").value("Password reset broken"));
    }

    @Test
    void statusFilterReturnsOnlyMatchingTickets() throws Exception {
        String openId = createTicket("Open ticket", "Body");
        String inProgressId = createTicket("Working ticket", "Body");
        transition(inProgressId, "IN_PROGRESS");

        mockMvc.perform(get("/api/tickets").queryParam("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickets", hasSize(1)))
                .andExpect(jsonPath("$.tickets[0].id").value(openId));
    }

    @Test
    void searchAndStatusFilterCombineWithAnd() throws Exception {
        String openLogin = createTicket("Login is broken", "Body");
        String workingLogin = createTicket("Login is slow", "Body");
        createTicket("Printer issue", "Body");
        transition(workingLogin, "IN_PROGRESS");

        mockMvc.perform(get("/api/tickets")
                        .queryParam("q", "login")
                        .queryParam("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickets", hasSize(1)))
                .andExpect(jsonPath("$.tickets[0].id").value(openLogin));
    }

    @Test
    void validTransitionReturns200AndPersistsNewStatus() throws Exception {
        String id = createTicket("Ticket", "Body");

        mockMvc.perform(post("/api/tickets/{id}/transitions", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestedStatus":"IN_PROGRESS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(get("/api/tickets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void invalidTransitionReturns409AndDoesNotMutateTicket() throws Exception {
        String id = createTicket("Ticket", "Body");

        mockMvc.perform(post("/api/tickets/{id}/transitions", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestedStatus":"CLOSED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(DomainErrorCodes.ILLEGAL_STATUS_TRANSITION))
                .andExpect(jsonPath("$.currentStatus").value("OPEN"))
                .andExpect(jsonPath("$.requestedStatus").value("CLOSED"));

        mockMvc.perform(get("/api/tickets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void validationFailuresReturn400InvalidRequest() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(DomainErrorCodes.INVALID_REQUEST))
                .andExpect(jsonPath("$.message").isNotEmpty());

        String id = createTicket("Ticket", "Body");
        mockMvc.perform(post("/api/tickets/{id}/comments", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(DomainErrorCodes.INVALID_REQUEST));
    }

    @Test
    void malformedAndMissingStatusAreRejectedWithContractErrors() throws Exception {
        String id = createTicket("Ticket", "Body");

        mockMvc.perform(post("/api/tickets/{id}/transitions", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestedStatus":"open"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(DomainErrorCodes.INVALID_STATUS_VALUE));

        mockMvc.perform(post("/api/tickets/{id}/transitions", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(DomainErrorCodes.INVALID_REQUEST));
    }

    @Test
    void missingTicketReturns404ForGetUpdateCommentAndTransition() throws Exception {
        String missing = "00000000-0000-0000-0000-000000000099";

        mockMvc.perform(get("/api/tickets/{id}", missing))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(DomainErrorCodes.TICKET_NOT_FOUND));

        mockMvc.perform(patch("/api/tickets/{id}", missing)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Updated"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(DomainErrorCodes.TICKET_NOT_FOUND));

        mockMvc.perform(post("/api/tickets/{id}/comments", missing)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"Comment"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(DomainErrorCodes.TICKET_NOT_FOUND));

        mockMvc.perform(post("/api/tickets/{id}/transitions", missing)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestedStatus":"IN_PROGRESS"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(DomainErrorCodes.TICKET_NOT_FOUND));
    }

    @Test
    void clientSuppliedStatusIsRejectedEvenWhenNull() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Ticket","status":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(DomainErrorCodes.STATUS_NOT_UPDATABLE));
    }

    private String createTicket(String title, String description) throws Exception {
        String payload = objectMapper.writeValueAsString(new CreatePayload(title, description));
        MvcResult result = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asText();
    }

    private void transition(String id, String requestedStatus) throws Exception {
        String payload = objectMapper.writeValueAsString(new TransitionPayload(requestedStatus));
        mockMvc.perform(post("/api/tickets/{id}/transitions", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    private record CreatePayload(String title, String description) {
    }

    private record TransitionPayload(String requestedStatus) {
    }
}
