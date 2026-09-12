package com.him.implatform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "好友免打扰")
@Data
public class FriendDndDTO {

    @Schema(description = "好友id")
    @NotNull(message = "好友id不能为空")
    private Long friendId;

    @Schema(description = "消息免打扰状态")
    @NotNull(message = "消息免打扰状态不能为空")
    private Boolean isDnd;
}
