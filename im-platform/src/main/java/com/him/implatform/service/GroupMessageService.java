package com.him.implatform.service;

import com.him.implatform.dto.ChatDeleteDTO;
import com.him.implatform.dto.GroupMessageDTO;
import com.him.implatform.dto.GroupMessageHistoryDTO;
import com.him.implatform.dto.MessageDeleteDTO;
import com.him.implatform.entity.GroupMessage;
import com.him.implatform.vo.GroupMessageVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public interface GroupMessageService {

    /**
     * 发送群聊消息
     *
     * @param dto 消息dto
     * @return 发送结果
     */
    GroupMessageVO sendMessage(@Valid GroupMessageDTO dto);

    /**
     * 保存消息(分配群内序号并刷新该群最新消息id缓存)
     *
     * @param message 消息
     */
    void saveMessage(GroupMessage message);

    /**
     * 撤回消息
     *
     * @param id 消息id
     * @return 撤回提示消息
     */
    GroupMessageVO recallMessage(@NotNull(message = "消息id不能为空") Long id);

    /**
     * 拉取离线消息
     *
     * @param minId 前端已有最小消息id
     * @return 离线消息,按id正序
     */
    List<GroupMessageVO> loadOfflineMessage(Long minId);

    /**
     * 查询群聊历史消息:localIds / seqNos / minSeqNo+maxSeqNo 三选一
     *
     * @param dto 查询条件
     * @return 历史消息,按seqNo正序
     */
    List<GroupMessageVO> loadHistoryMessage(@Valid GroupMessageHistoryDTO dto);

    /**
     * 设置消息已读:推进本人在该群的已读位置,并把已全员读完的回执消息置为完成
     *
     * @param groupId   群id
     * @param messageId 已读到的消息id,为空表示已读到该群最新
     */
    void readedMessage(Long groupId, Long messageId);

    /**
     * 查询某条消息的已读用户id
     *
     * @param groupId   群id
     * @param messageId 消息id
     * @return 已读该消息的用户id
     */
    List<Long> findReadedUsers(Long groupId, Long messageId);

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
