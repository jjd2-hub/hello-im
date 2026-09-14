package com.him.implatform.controller;

import com.him.implatform.dto.GroupDndDTO;
import com.him.implatform.dto.GroupInviteDTO;
import com.him.implatform.dto.GroupMemberRemoveDTO;
import com.him.implatform.result.Result;
import com.him.implatform.service.GroupService;
import com.him.implatform.vo.GroupMemberVO;
import com.him.implatform.vo.GroupVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "群聊")
@RestController
@RequestMapping("/group")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    // TODO 重复提交注解，以及本controller接口实现
    @Operation(summary = "创建群聊", description = "创建群聊")
    @PostMapping("/create")
    public Result<GroupVO> createGroup(@Valid @RequestBody GroupVO vo) {
        return Result.success();
    }

    @Operation(summary = "修改群聊信息", description = "修改群聊信息")
    @PutMapping("/modify")
    public Result<GroupVO> modifyGroup(@Valid @RequestBody GroupVO vo) {
        return Result.success();
    }

    @Operation(summary = "解散群聊", description = "解散群聊")
    @DeleteMapping("/delete/{groupId}")
    public Result deleteGroup(@NotNull(message = "群聊id不能为空") @PathVariable Long groupId) {
        return Result.success();
    }


    @Operation(summary = "查询群聊", description = "查询单个群聊信息")
    @GetMapping("/find/{groupId}")
    public Result<GroupVO> findGroup(@NotNull(message = "群聊id不能为空") @PathVariable Long groupId) {
        return Result.success();
    }

    @Operation(summary = "查询群聊列表", description = "查询群聊列表")
    @GetMapping("/list")
    public Result<List<GroupVO>> findGroups(@RequestParam(defaultValue = "0") Long version) {
        return Result.success();
    }


    @Operation(summary = "邀请进群", description = "邀请好友进群")
    @PostMapping("/invite")
    public Result invite(@Valid @RequestBody GroupInviteDTO dto) {
        return Result.success();
    }

    @Operation(summary = "查询群聊成员", description = "查询群聊成员")
    @GetMapping("/members/{groupId}")
    public Result<List<GroupMemberVO>> findGroupMembers(@NotNull(message = "群聊id不能为空") @PathVariable Long groupId,
                                                        @RequestParam(defaultValue = "0") Long version) {
        return Result.success();
    }

    @Operation(summary = "查询在线群聊成员id", description = "查询在线群聊成员id")
    @GetMapping("/members/online/{groupId}")
    public Result<List<Long>> findOnlineMemberIds(@NotNull(message = "群聊id不能为空") @PathVariable Long groupId){
        return Result.success();
    }


    @Operation(summary = "将成员移出群聊", description = "将成员移出群聊")
    @DeleteMapping("/members/remove")
    public Result<?> removeMembers(@Valid @RequestBody GroupMemberRemoveDTO dto) {
        return Result.success();
    }


    @Operation(summary = "退出群聊", description = "退出群聊")
    @DeleteMapping("/quit/{groupId}")
    public Result<?> quitGroup(@NotNull(message = "群聊id不能为空") @PathVariable Long groupId) {
        return Result.success();
    }

    @Operation(summary = "开启/关闭免打扰", description = "开启/关闭免打扰")
    @PutMapping("/dnd")
    public Result<?> setGroupDnd(@Valid @RequestBody GroupDndDTO dto) {
        return Result.success();
    }

}