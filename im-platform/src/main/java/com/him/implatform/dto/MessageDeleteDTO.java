package com.him.implatform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "删除消息DTO")
public class MessageDeleteDTO {

    @NotNull(message = "会话id不可为空")
    @Schema(description = "会话id,即好友id/群id")
    private Long chatId;

    @NotEmpty(message = "消息id不可为空")
    @Schema(description = "消息id")
    private List<Long> messageIds;
}