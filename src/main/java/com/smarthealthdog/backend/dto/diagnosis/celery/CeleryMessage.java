package com.smarthealthdog.backend.dto.diagnosis.celery;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CeleryMessage {
    private String body;
    @JsonProperty("content-encoding")
    private String contentEncoding;
    @JsonProperty("content-type")
    private String contentType;
    private MessageHeaders headers;
    private MessageProperties properties;
}