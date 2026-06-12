package com.smarthealthdog.backend.dto.diagnosis.celery;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class MessageHeaders {
    private String lang;
    private String task;
    private String id;
    @JsonProperty("root_id")
    private String rootId;
    @JsonProperty("argsrepr")
    private String argsRepr;
    @JsonProperty("kwargsrepr")
    private String kwargsRepr;
    @JsonProperty("ignore_result")
    private boolean ignoreResult;
    private int retries;
    @JsonProperty("timelimit")
    private Object[] timeLimit; // Use Object[] for [null, null]
    @JsonProperty("parent_id")
    private String parentId;
}