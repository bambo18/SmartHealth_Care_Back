package com.smarthealthdog.backend.dto.diagnosis.celery;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@Builder
public class MessageProperties {
    @JsonProperty("correlation_id")
    private String correlationId;
    @JsonProperty("delivery_mode")
    private int deliveryMode;
    @JsonProperty("delivery_tag")
    private String deliveryTag;
    private int priority;
    @JsonProperty("body_encoding")
    private String bodyEncoding;
    @JsonProperty("delivery_info")
    private DeliveryInfo deliveryInfo;
}