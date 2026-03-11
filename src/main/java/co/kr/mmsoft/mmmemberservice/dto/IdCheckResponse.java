package co.kr.mmsoft.mmmemberservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor  // Jackson 라이브러리가 역직렬화할 때 필요합니다.
@AllArgsConstructor // 모든 필드를 인자로 받는 생성자를 자동으로 생성합니다.
public class IdCheckResponse {
    private int cnt;
}
