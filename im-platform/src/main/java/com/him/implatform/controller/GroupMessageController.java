package com.him.implatform.controller;

import com.him.implatform.dto.ChatDeleteDTO;
import com.him.implatform.dto.GroupMessageDTO;
import com.him.implatform.dto.GroupMessageHistoryDTO;
import com.him.implatform.dto.MessageDeleteDTO;
import com.him.implatform.entity.GroupMessage;
import com.him.implatform.result.Result;
import com.him.implatform.service.GroupMessageService;
import com.him.implatform.vo.GroupMessageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "群聊消息")
@RestController
@RequestMapping("/message/group")
@RequiredArgsConstructor
public class GroupMessageController {

    private final GroupMessageService groupMessageService;

    @Operation(summary = "发送群聊消息")
    @PostMapping("/send")
    public Result<GroupMessageVO> sendMessage(@Valid @RequestBody GroupMessageDTO dto){
        // TODO
        return Result.success();
    }

    @Operation(summary = "撤回消息")
    @DeleteMapping("/recall/{id}")
    public Result<GroupMessageVO> deleteMessage(@NotNull(message = "消息id不能为空") @PathVariable("id") Long id){
        return Result.success();
    }

    @Operation(summary = "拉取离线消息")
    @GetMapping("/loadOfflineMessage")
    public Result<List<GroupMessageVO>> loadOfflineMessage(@RequestParam Long minId){
        // TODO
        return Result.success();
    }

    @Operation(summary = "设置消息已读")
    @PutMapping("/readed")
    public Result<?> readedMessage(@RequestParam Long groupId,@RequestParam(required = false) Long messageId){
        // TODO
        return Result.success();
    }

    @Operation(summary = "获取已读用户id")
    @GetMapping("findReadedUsers")
    public Result<List<Long>> findReadedUsers(@RequestParam Long groupId){
        // TODO
        return Result.success();
    }

    @Operation(summary = "删除消息")
    @DeleteMapping("/deleteMessage")
    public Result<?> deleteMessage(@Valid @RequestBody MessageDeleteDTO dto){
        // TODO
        return Result.success();
    }

    @Operation(summary = "删除会话")
    @DeleteMapping("/deleteChat")
    public Result<?> deleteChat(@Valid @RequestBody ChatDeleteDTO dto){
        // TODO
        return Result.success();
    }

    @Operation(summary = "/history")
    @PostMapping("/history")
    public Result<?> loadHistoryMessage(@Valid @RequestBody GroupMessageHistoryDTO dto){
        // TODO
        return Result.success();
    }

}
