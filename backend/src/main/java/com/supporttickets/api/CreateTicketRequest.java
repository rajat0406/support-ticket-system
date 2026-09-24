package com.supporttickets.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

import jakarta.validation.constraints.NotBlank;

public class CreateTicketRequest {

    @NotBlank
    private String title;
    private String description;
    private String priority;
    private String assignee;
    private boolean statusProvided;

    public String title() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String description() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String priority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String assignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    @JsonSetter("status")
    public void setStatus(Object ignored) {
        this.statusProvided = true;
    }

    @JsonIgnore
    public boolean statusProvided() {
        return statusProvided;
    }
}
