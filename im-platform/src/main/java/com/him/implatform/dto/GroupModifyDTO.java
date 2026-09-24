package com.him.implatform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/**
 * 修改群聊入参。
 *
 * <p>这个接口同时承担两件事,权限不同:
 * <ul>
 *   <li><b>任何成员</b>都能改自己的"群内昵称备注"和"群备注名"({@code remarkNickName}/{@code remarkGroupName})</li>
 *   <li><b>只有群主</b>能改群本身的资料({@code name}/{@code headImage}/{@code notice})</li>
 * </ul>
 * 所以 {@code name} 这里是可选的:非群主只改备注时不传群名也应该成功。
 */
@Data
@Schema(description = "修改群聊DTO")
public class GroupModifyDTO {

    @Schema(description = "群id")
    @NotNull(message = "群id不可为空")
    private Long id;

    @Schema(description = "群名称,仅群主可改;不传表示不改")
    @Length(max = 20, message = "群名称长度不能大于20")
    private String name;

    @Schema(description = "群头像,仅群主可改")
    private String headImage;

    @Schema(description = "群头像缩略图,仅群主可改")
    private String headImageThumb;

    @Schema(description = "群公告,仅群主可改")
    @Length(max = 1024, message = "群公告长度不能大于1024")
    private String notice;

    @Schema(description = "我在本群的显示昵称")
    @Length(max = 20, message = "显示昵称长度不能大于20")
    private String remarkNickName;

    @Schema(description = "我对该群的备注名")
    @Length(max = 20, message = "群备注长度不能大于20")
    private String remarkGroupName;
}
