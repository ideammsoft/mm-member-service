package co.kr.mmsoft.mmmemberservice.mybatis.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Pds {
    private Integer pdsId;
    private String  title;
    private String  content;
    private String  osInfo;
    private String  version;
    private String  fileSize;
    private Integer downloadCount;
    private String  thumbnail;
    private String  downloadUrl;
    private String  category;
    private Integer sortOrder;
    private String  publishedAt;
    private String  isActive;
    private String  regDate;
    private String  updateDate;
}
