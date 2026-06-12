package com.smarthealthdog.backend.dto.diagnosis.celery;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DeliveryInfo {
    private String exchange;
    @JsonProperty("routing_key")
    private String routingKey;
}