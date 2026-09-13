package com.him.implatform.dto;

import com.him.implatform.annotation.LogSensitive;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Schema(description = "用户登录DTO")
@Data
public class LoginDTO {

    @Schema(description = "用户名")
    @NotEmpty(message = "用户名不能为空")
    private String username;

    @Schema(description = "用户密码")
    @NotEmpty(message = "用户密码不能为空")
    @LogSensitive
    private String password;
}
