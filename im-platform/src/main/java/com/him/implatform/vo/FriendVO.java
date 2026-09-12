package com.him.implatform.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "好友信息VO")
@Data
public class FriendVO {

    @Schema(description = "好友id")
    @NotNull(message = "好友id不能为空")
    private Long id;

    @Schema(description = "好友昵称")
    @NotNull(message = "好友昵称不能为空")
    private String nickname;

    @Schema(description = "好友头像")
    private String headImage;

    @Schema(description = "是否开启免打扰")
    private Boolean isDnd;

    @Schema(description = "是否已删除")
    private Boolean deleted;

    @Schema(description = "版本号")
    private Long version;
}
