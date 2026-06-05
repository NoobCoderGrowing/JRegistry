package io.github.noobcodergrowing.jregistry.Web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ElectionEventDTO {
    private long sequence;
    private String timestamp;
    private String eventType;
    private int nodeId;
    private int targetNodeId;
    private long term;
    private int votesReceived;
    private String message;
}
