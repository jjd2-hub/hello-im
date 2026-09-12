package com.him.implatform.dto;

import com.him.implatform.annotation.LogSensitive;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Schema(description = "修改密码DTO")
@Data
public class ModifyPwdDTO {

    @LogSensitive
    @Schema(description = "旧密码")
    @NotEmpty(message = "旧密码不能为空")
    private String oldPwd;

    @LogSensitive
    @Schema(description = "新密码")
    @NotEmpty(message = "新密码不能为空")
    private String newPwd;
}
