package co.kr.mmsoft.mmmemberservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IdCheckRequest {
    @JsonProperty("homepageId") //파라미터명이 틀릴 경우 어노테이션으로 조절할 수 잇다.
    private String openId;
}
