package com.him.implatform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/**
 * 创建群聊入参。
 *
 * <p>只包含"群主可以设置"的字段。群主id、是否封禁、是否解散、创建时间
 * 全部由服务端决定,客户端无从传入 —— 这也是从"用 VO 当入参"改过来的主要动机。
 */
@Data
@Schema(description = "创建群聊DTO")
public class GroupCreateDTO {

    @Schema(description = "群名称")
    @NotEmpty(message = "群名称不可为空")
    @Length(max = 20, message = "群名称长度不能大于20")
    private String name;

    @Schema(description = "群头像")
    private String headImage;

    @Schema(description = "群头像缩略图")
    private String headImageThumb;

    @Schema(description = "群公告")
    @Length(max = 1024, message = "群公告长度不能大于1024")
    private String notice;

    @Schema(description = "创建者在本群的显示昵称")
    @Length(max = 20, message = "显示昵称长度不能大于20")
    private String remarkNickName;

    @Schema(description = "创建者对该群的备注名")
    @Length(max = 20, message = "群备注长度不能大于20")
    private String remarkGroupName;
}
