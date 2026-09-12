package com.him.implatform.dto;

import com.him.implatform.annotation.LogSensitive;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Schema(description = "用户注册DTO")
@Data
public class RegisterDTO {

    @Schema(description = "用户名")
    @NotEmpty(message = "用户名不能为空")
    @Length(max = 20, message = "用户名不能大于20字符")
    private String username;

    @Schema(description = "用户密码")
    @NotEmpty(message = "用户密码不能为空")
    @Length(min = 4, max = 20, message = "密码长度必须在4-20字符之间")
    @LogSensitive
    private String password;

    @Schema(description = "用户昵称")
    @NotEmpty(message = "用户昵称不能为空")
    @Length(max = 20, message = "昵称不能大于20字符")
    private String nickname;
}
