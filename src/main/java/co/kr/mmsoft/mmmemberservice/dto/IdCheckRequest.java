package co.kr.mmsoft.mmmemberservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IdCheckRequest {
    @JsonProperty("openId")
    private String openId;
}
