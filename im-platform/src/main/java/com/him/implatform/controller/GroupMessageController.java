package com.him.implatform.controller;

import com.him.implatform.dto.ChatDeleteDTO;
import com.him.implatform.dto.GroupMessageDTO;
import com.him.implatform.dto.GroupMessageHistoryDTO;
import com.him.implatform.dto.MessageDeleteDTO;
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
    public Result<GroupMessageVO> sendMessage(@Valid @RequestBody GroupMessageDTO dto) {
        return Result.success(groupMessageService.sendMessage(dto));
    }

    @Operation(summary = "撤回消息")
    @DeleteMapping("/recall/{id}")
    public Result<GroupMessageVO> recallMessage(@NotNull(message = "消息id不能为空") @PathVariable("id") Long id) {
        return Result.success(groupMessageService.recallMessage(id));
    }

    @Operation(summary = "拉取离线消息")
    @GetMapping("/loadOfflineMessage")
    public Result<List<GroupMessageVO>> loadOfflineMessage(@RequestParam Long minId) {
        return Result.success(groupMessageService.loadOfflineMessage(minId));
    }

    @Operation(summary = "设置消息已读", description = "messageId为空表示已读到该群最新")
    @PutMapping("/readed")
    public Result<?> readedMessage(@RequestParam Long groupId, @RequestParam(required = false) Long messageId) {
        groupMessageService.readedMessage(groupId, messageId);
        return Result.success();
    }

    @Operation(summary = "获取已读用户id", description = "查询某条消息已读的成员")
    @GetMapping("/findReadedUsers")
    public Result<List<Long>> findReadedUsers(@RequestParam Long groupId,
                                              @NotNull(message = "消息id不能为空") @RequestParam Long messageId) {
        return Result.success(groupMessageService.findReadedUsers(groupId, messageId));
    }

    @Operation(summary = "查询历史消息", description = "localIds / seqNos / minSeqNo+maxSeqNo 三选一")
    @PostMapping("/history")
    public Result<List<GroupMessageVO>> loadHistoryMessage(@Valid @RequestBody GroupMessageHistoryDTO dto) {
        return Result.success(groupMessageService.loadHistoryMessage(dto));
    }

    @Operation(summary = "删除消息", description = "根据id列表删除消息,仅对自己生效")
    @DeleteMapping("/deleteMessage")
    public Result<?> deleteMessage(@Valid @RequestBody MessageDeleteDTO dto) {
        groupMessageService.deleteMessage(dto);
        return Result.success();
    }

    @Operation(summary = "删除会话", description = "删除会话以及会话中所有消息,仅对自己生效")
    @DeleteMapping("/deleteChat")
    public Result<?> deleteChat(@Valid @RequestBody ChatDeleteDTO dto) {
        groupMessageService.deleteChat(dto);
        return Result.success();
    }
}
