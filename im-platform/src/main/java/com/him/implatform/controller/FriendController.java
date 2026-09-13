package com.him.implatform.controller;

import com.him.implatform.dto.FriendDndDTO;
import com.him.implatform.result.Result;
import com.him.implatform.service.FriendService;
import com.him.implatform.vo.FriendVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "好友接口")
@RestController
@RequestMapping("/friend")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @Operation(summary = "好友列表")
    @GetMapping("/list")
    public Result<List<FriendVO>> findFriends(@RequestParam(defaultValue = "0") Long version) {
        return Result.success(friendService.findFriends(version));
    }

    @Operation(summary = "添加好友")
    @PostMapping("/add")
    public Result<?> addFriend(@NotNull(message = "好友id不能为空") @RequestParam Long friendId) {
        // TODO 完善添加好友逻辑
        return Result.success();
    }

    @Operation(summary = "查找好友信息")
    @GetMapping("/find/{friendId}")
    public Result<FriendVO> findFriend(@NotNull(message = "好友id不能为空") @PathVariable Long friendId) {
        // TODO 完善查找好友信息逻辑
        return Result.success();
    }

    @Operation(summary = "删除好友")
    @DeleteMapping("/delete/{friendId}")
    public Result<?> deleteFriend(@NotNull(message = "好友id不能为空") @PathVariable Long friendId) {
        // TODO 完善删除好友逻辑
        return Result.success();
    }

    @Operation(summary = "开启/关闭免打扰模式")
    @PutMapping("/dnd")
    public Result<?> setFriendDnd(@Valid @RequestBody FriendDndDTO friendDndDTO) {
        // TODO 完善开启/关闭免打扰模式
        return Result.success();
    }

}
