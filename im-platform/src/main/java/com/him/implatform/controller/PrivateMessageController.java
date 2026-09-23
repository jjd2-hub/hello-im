package com.him.implatform.controller;

import com.him.implatform.dto.ChatDeleteDTO;
import com.him.implatform.dto.MessageDeleteDTO;
import com.him.implatform.dto.PrivateMessageDTO;
import com.him.implatform.dto.PrivateMessageHistoryDTO;
import com.him.implatform.result.Result;
import com.him.implatform.service.PrivateMessageService;
import com.him.implatform.vo.PrivateMessageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "私聊消息")
@RestController
@RequestMapping("/message/private")
@RequiredArgsConstructor
public class PrivateMessageController {

    private final PrivateMessageService privateMessageService;

    @Operation(summary = "发送消息", description = "私聊")
    @PostMapping("/send")
    public Result<PrivateMessageVO> sendMessage(@Valid @RequestBody PrivateMessageDTO dto) {
        return Result.success(privateMessageService.sendMessage(dto));
    }

    @Operation(summary = "撤回消息", description = "撤回私聊消息")
    @DeleteMapping("/recall/{id}")
    public Result<PrivateMessageVO> recallMessage(@NotNull(message = "消息id不能为空") @PathVariable Long id) {
        return Result.success(privateMessageService.recallMessage(id));
    }

    @Operation(summary = "拉取离线消息")
    @GetMapping("/loadOfflineMessage")
    public Result<List<PrivateMessageVO>> loadOfflineMessage(@RequestParam Long minId) {
        return Result.success(privateMessageService.loadOfflineMessage(minId));
    }

    @Operation(summary = "消息已读", description = "将消息状态设为已读,messageId为空表示该会话全部已读")
    @PutMapping("/readed")
    public Result<?> readedMessage(@RequestParam Long friendId, @RequestParam(required = false) Long messageId) {
        privateMessageService.readedMessage(friendId, messageId);
        return Result.success();
    }

    @Operation(summary = "查询历史消息", description = "localIds / seqNos / minSeqNo+maxSeqNo 三选一")
    @PostMapping("/history")
    public Result<List<PrivateMessageVO>> loadHistoryMessage(@Valid @RequestBody PrivateMessageHistoryDTO dto) {
        return Result.success(privateMessageService.loadHistoryMessage(dto));
    }

    @Operation(summary = "删除消息", description = "根据id列表删除消息,仅对自己生效")
    @DeleteMapping("/deleteMessage")
    public Result<?> deleteMessage(@Valid @RequestBody MessageDeleteDTO dto) {
        privateMessageService.deleteMessage(dto);
        return Result.success();
    }

    @Operation(summary = "删除会话", description = "删除会话以及会话中所有消息,仅对自己生效")
    @DeleteMapping("/deleteChat")
    public Result<?> deleteChat(@Valid @RequestBody ChatDeleteDTO dto) {
        privateMessageService.deleteChat(dto);
        return Result.success();
    }
}
