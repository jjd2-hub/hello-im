package com.him.implatform.controller;

import com.him.imcommon.util.BeanUtil;
import com.him.implatform.context.UserContext;
import com.him.implatform.dto.ModifyPwdDTO;
import com.him.implatform.entity.User;
import com.him.implatform.result.Result;
import com.him.implatform.service.UserService;
import com.him.implatform.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "用户相关")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "获取用户信息")
    @GetMapping("/self")
    public Result<UserVO> findSelfInfo(){
        User user=userService.getById(UserContext.getUserId());
        UserVO userVO= BeanUtil.copyProperties(user,UserVO.class);
        return Result.success(userVO);
    }

    @Operation(summary = "查找用户",description = "根据id查找")
    @GetMapping("/find/{id}")
    public Result<UserVO> findById(@NotNull @PathVariable("id") Long id){
        return Result.success(userService.findUserById(id));
    }

    @Operation(summary = "修改用户信息",description = "仅允许修改登录用户信息")
    @PutMapping("/update")
    public Result<?> update(@Valid @RequestBody UserVO vo){
        userService.update(vo);
        return Result.success();
    }

    @Operation(summary = "查找用户2",description = "根据用户名或昵称查找用户")
    @GetMapping("/findByName")
    public Result<List<UserVO>> findByName(@RequestParam String name){
        return Result.success(userService.findUserByName(name));
    }

    @Operation(summary = "修改密码",description = "修改登录用户密码")
    @PutMapping("/modifyPwd")
    public Result<?> modifyPwd(@Valid @RequestBody ModifyPwdDTO dto){
        userService.modifyPwd(dto);
        return Result.success();
    }
}
