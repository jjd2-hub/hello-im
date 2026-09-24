package com.him.implatform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/**
 * 修改个人资料入参。
 *
 * <p>为什么不直接复用 {@code UserVO}:VO 里有 {@code type}(用户类型)、
 * {@code isBanned}、{@code reason}、{@code online} 这些<b>只有服务端有权决定</b>的字段。
 * 用 VO 当入参时,这些字段就变成了"客户端可以传",一旦某处顺手做了属性拷贝,
 * 就变成了越权漏洞(这个项目历史上真的踩过:GroupVO 当入参导致能篡改 ownerId/dissolve)。
 *
 * <p>所以入参只声明"允许用户改的字段",服务端字段天然不可能被传入。
 */
@Data
@Schema(description = "修改个人资料DTO")
public class UserUpdateDTO {

    @Schema(description = "用户id,只能改自己")
    @NotNull(message = "用户id不能为空")
    private Long id;

    @Schema(description = "用户昵称")
    @NotEmpty(message = "用户昵称不能为空")
    @Length(max = 20, message = "用户昵称不能大于20字符")
    private String nickname;

    @Schema(description = "性别 0:男 1:女")
    private Integer sex;

    @Schema(description = "个性签名")
    @Length(max = 128, message = "个性签名不能大于128个字符")
    private String signature;

    @Schema(description = "头像")
    private String headImage;

    @Schema(description = "头像缩略图")
    private String headImageThumb;
}
