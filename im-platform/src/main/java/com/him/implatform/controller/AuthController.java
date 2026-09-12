package com.him.implatform.controller;

import com.him.implatform.dto.LoginDTO;
import com.him.implatform.dto.RegisterDTO;
import com.him.implatform.result.Result;
import com.him.implatform.service.UserService;
import com.him.implatform.vo.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证接口")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<?> register(@Valid @RequestBody RegisterDTO dto) {
        userService.register(dto);
        return Result.success();
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(userService.login(dto));
    }

    @Operation(summary = "刷新token")
    @PutMapping("/refreshToken")
    public Result<LoginVO> refreshToken(@RequestHeader("refreshToken") String token) {
        return Result.success(userService.refreshToken(token));
    }
}
