package com.him.implatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.him.implatform.entity.MessageDeletion;
import com.him.implatform.enums.ChatType;
import com.him.implatform.enums.DeleteType;
import com.him.implatform.mapper.MessageDeletionMapper;
import com.him.implatform.service.MessageDeletionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageDeletionServiceImpl extends ServiceImpl<MessageDeletionMapper, MessageDeletion>
        implements MessageDeletionService {

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteMessage(Long userId, ChatType chatType, Long chatId, List<Long> messageIds) {
        if (Objects.isNull(messageIds) || messageIds.isEmpty()) {
            return;
        }
        Date now = new Date();
        List<MessageDeletion> deletions = messageIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(messageId -> build(userId, chatType, chatId, messageId, DeleteType.MESSAGE, now))
                .toList();
        if (!deletions.isEmpty()) {
            this.saveBatch(deletions);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteChat(Long userId, ChatType chatType, Long chatId, Long maxMessageId) {
        MessageDeletion deletion = build(userId, chatType, chatId,
                Objects.isNull(maxMessageId) ? 0L : maxMessageId, DeleteType.CHAT, new Date());
        this.save(deletion);
    }

    @Override
    public Set<Long> findDeletedMessageIds(Long userId, ChatType chatType, Long chatId,
                                           Collection<Long> messageIds) {
        Set<Long> deleted = new HashSet<>();
        if (Objects.isNull(messageIds) || messageIds.isEmpty()) {
            return deleted;
        }
        // 1.按会话删除:边界id及之前的消息都算删除(重复删除会话时取最靠后的边界)
        Long boundary = findChatBoundary(userId, chatType, chatId);
        if (Objects.nonNull(boundary)) {
            messageIds.stream()
                    .filter(id -> Objects.nonNull(id) && id <= boundary)
                    .forEach(deleted::add);
        }
        // 2.按消息删除
        LambdaQueryWrapper<MessageDeletion> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(MessageDeletion::getUserId, userId)
                .eq(MessageDeletion::getChatType, chatType.getCode())
                .eq(MessageDeletion::getChatId, chatId)
                .eq(MessageDeletion::getDeleteType, DeleteType.MESSAGE.getCode())
                .in(MessageDeletion::getMessageId, messageIds)
                .select(MessageDeletion::getMessageId);
        this.list(wrapper).forEach(d -> deleted.add(d.getMessageId()));
        return deleted;
    }

    /**
     * 查询该用户在该会话上"按会话删除"的最大边界消息id,没有则返回null
     */
    private Long findChatBoundary(Long userId, ChatType chatType, Long chatId) {
        LambdaQueryWrapper<MessageDeletion> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(MessageDeletion::getUserId, userId)
                .eq(MessageDeletion::getChatType, chatType.getCode())
                .eq(MessageDeletion::getChatId, chatId)
                .eq(MessageDeletion::getDeleteType, DeleteType.CHAT.getCode())
                .orderByDesc(MessageDeletion::getMessageId)
                .last("limit 1");
        MessageDeletion deletion = this.getOne(wrapper);
        return Objects.isNull(deletion) ? null : deletion.getMessageId();
    }

    private MessageDeletion build(Long userId, ChatType chatType, Long chatId, Long messageId,
                                  DeleteType deleteType, Date deleteTime) {
        MessageDeletion deletion = new MessageDeletion();
        deletion.setUserId(userId);
        deletion.setChatType(chatType.getCode());
        deletion.setChatId(chatId);
        deletion.setMessageId(messageId);
        deletion.setDeleteType(deleteType.getCode());
        deletion.setDeleteTime(deleteTime);
        return deletion;
    }
}
