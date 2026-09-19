package com.gd.api.core.error;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class ErrorDetailsResponse {

    @JsonProperty("errorCode")
    private String errorCode;

    @JsonProperty("description")
    private String description;
}
