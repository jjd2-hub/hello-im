package com.him.implatform.service;

import com.him.implatform.dto.ChatDeleteDTO;
import com.him.implatform.dto.MessageDeleteDTO;
import com.him.implatform.dto.PrivateMessageDTO;
import com.him.implatform.dto.PrivateMessageHistoryDTO;
import com.him.implatform.entity.PrivateMessage;
import com.him.implatform.vo.PrivateMessageVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public interface PrivateMessageService {
    /**
     * 发送消息
     * @param dto 消息dto
     * @return 结果
     */
    PrivateMessageVO sendMessage(@Valid PrivateMessageDTO dto);

    /**
     * 保存信息
     * @param message 信息
     */
    void saveMessage(PrivateMessage message);

    /**
     * 撤回消息
     * @param id 消息id
     * @return 撤回消息
     */
    PrivateMessageVO recallMessage(@NotNull(message = "消息id不能为空") Long id);

    /**
     * 已读消息:把对方发来的消息置为已读,并推进该会话的已读位置
     *
     * @param friendId  友人id
     * @param messageId 已读到的消息id,为空表示该会话全部已读
     */
    void readedMessage(Long friendId, Long messageId);

    /**
     * 拉取离线消息
     * @param minId 前端已有最小消息id
     * @return 未读消息
     */
    List<PrivateMessageVO> loadOfflineMessage(Long minId);

    /**
     * 查询历史消息:localIds / seqNos / minSeqNo+maxSeqNo 三选一
     *
     * @param dto 查询条件
     * @return 历史消息,按seqNo正序
     */
    List<PrivateMessageVO> loadHistoryMessage(@Valid PrivateMessageHistoryDTO dto);

    /**
     * 删除消息(仅对自己生效)
     *
     * @param dto 会话id + 消息id列表
     */
    void deleteMessage(@Valid MessageDeleteDTO dto);

    /**
     * 删除会话以及会话中已有消息(仅对自己生效)
     *
     * @param dto 会话id
     */
    void deleteChat(@Valid ChatDeleteDTO dto);
}
