package com.supporttickets.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;

public class UpdateTicketRequest {

    @NotBlank
    private String title;
    private JsonNode description;
    private JsonNode priority;
    private JsonNode assignee;
    private boolean descriptionPresent;
    private boolean priorityPresent;
    private boolean assigneePresent;
    private boolean statusProvided;

    public String title() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @JsonSetter("description")
    public void setDescription(JsonNode description) {
        this.description = description;
        this.descriptionPresent = true;
    }

    @JsonSetter("priority")
    public void setPriority(JsonNode priority) {
        this.priority = priority;
        this.priorityPresent = true;
    }

    @JsonSetter("assignee")
    public void setAssignee(JsonNode assignee) {
        this.assignee = assignee;
        this.assigneePresent = true;
    }

    @JsonSetter("status")
    public void setStatus(Object ignored) {
        this.statusProvided = true;
    }

    @JsonIgnore
    public boolean statusProvided() {
        return statusProvided;
    }

    @JsonIgnore
    public boolean descriptionPresent() {
        return descriptionPresent;
    }

    @JsonIgnore
    public boolean priorityPresent() {
        return priorityPresent;
    }

    @JsonIgnore
    public boolean assigneePresent() {
        return assigneePresent;
    }

    public String descriptionValue() {
        return textOrNull(description);
    }

    public String priorityValue() {
        return textOrNull(priority);
    }

    public String assigneeValue() {
        return textOrNull(assignee);
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }
}
