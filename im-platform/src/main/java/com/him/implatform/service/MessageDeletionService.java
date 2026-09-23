package com.him.implatform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.him.implatform.entity.MessageDeletion;
import com.him.implatform.enums.ChatType;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 消息删除记录。删除是"仅对自己生效"的,所以不物理删除消息,而是记录谁删了哪些消息,
 * 查询历史/离线消息时再把这些消息标记为 deleted 返回,其它端据此同步删除。
 */
public interface MessageDeletionService extends IService<MessageDeletion> {

    /**
     * 按消息删除:记录用户删掉了会话中的哪几条消息
     *
     * @param userId     用户id
     * @param chatType   会话类型
     * @param chatId     会话id(好友id或群id)
     * @param messageIds 消息id列表
     */
    void deleteMessage(Long userId, ChatType chatType, Long chatId, List<Long> messageIds);

    /**
     * 按会话删除:以当前会话最大消息id为界,该id及之前的消息对该用户不可见
     *
     * @param userId        用户id
     * @param chatType      会话类型
     * @param chatId        会话id
     * @param maxMessageId  删除时的最大消息id,没有消息时传0
     */
    void deleteChat(Long userId, ChatType chatType, Long chatId, Long maxMessageId);

    /**
     * 从给定消息中挑出该用户已删除的那些id(含按会话删除的边界内的消息)
     *
     * @param userId     用户id
     * @param chatType   会话类型
     * @param chatId     会话id
     * @param messageIds 待判定的消息id
     * @return 应标记为已删除的消息id集合
     */
    Set<Long> findDeletedMessageIds(Long userId, ChatType chatType, Long chatId, Collection<Long> messageIds);
}
