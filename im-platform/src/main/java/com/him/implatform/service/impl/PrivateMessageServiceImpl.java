package com.him.implatform.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.him.imcommon.constant.IMConstant;
import com.him.imcommon.util.BeanUtil;
import com.him.imcommon.util.ConvUtil;
import com.him.implatform.constant.Constant;
import com.him.implatform.constant.RedisKey;
import com.him.implatform.context.UserContext;
import com.him.implatform.dto.ChatDeleteDTO;
import com.him.implatform.dto.MessageDeleteDTO;
import com.him.implatform.dto.PrivateMessageDTO;
import com.him.implatform.dto.PrivateMessageHistoryDTO;
import com.him.implatform.entity.PrivateMessage;
import com.him.implatform.enums.ChatType;
import com.him.implatform.enums.MessageStatus;
import com.him.implatform.enums.MessageType;
import com.him.implatform.enums.ResultCode;
import com.him.implatform.exception.GlobalException;
import com.him.implatform.mapper.PrivateMessageMapper;
import com.him.implatform.service.FriendService;
import com.him.implatform.service.MessageDeletionService;
import com.him.implatform.service.PrivateMessageService;
import com.him.implatform.service.SensitiveWordService;
import com.him.implatform.vo.PrivateMessageVO;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrivateMessageServiceImpl extends ServiceImpl<PrivateMessageMapper, PrivateMessage> implements PrivateMessageService {

    private final FriendService friendService;
    private final MessageDeletionService messageDeletionService;
    private final SensitiveWordService sensitiveWordService;

    @Resource
    @Lazy
    private PrivateMessageService selfService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private RedissonClient redissonClient;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public PrivateMessageVO sendMessage(PrivateMessageDTO dto) {
        this.validMessage(dto);
        Long userId = UserContext.getUserId();
        Boolean isFriends = friendService.isFriend(userId, dto.getRecvId());
        if (Boolean.FALSE.equals(isFriends)) {
            throw new GlobalException(ResultCode.HAS_NO_RELATION_WITH_TARGET.getCode(), "对方不是您好友");
        }
        PrivateMessage message = BeanUtil.copyProperties(dto, PrivateMessage.class);
        message.setConvKey(ConvUtil.buildConvKey(userId, dto.getRecvId()));
        message.setSendId(userId);
        message.setStatus(MessageStatus.PENDING.getCode());
        message.setSendTime(new Date());
        // 敏感词过滤只作用于文字消息:图片/文件等内容是JSON,整体替换会破坏结构
        if (MessageType.TEXT.getCode().equals(dto.getType())) {
            message.setContent(sensitiveWordService.filter(dto.getContent()));
        }
        selfService.saveMessage(message);
        // TODO 推送消息给接收方(im-client/im-server 完成后实现)
        PrivateMessageVO vo = BeanUtil.copyProperties(message, PrivateMessageVO.class);
        vo.setDeleted(false);
        log.info("发送私聊消息,发送id:{},接收id:{},内容:{}", userId, dto.getRecvId(), dto.getContent());
        return vo;
    }

    @Override
    public void saveMessage(PrivateMessage message) {
        if (StrUtil.isEmpty(message.getLocalId())) {
            message.setLocalId(IdWorker.getIdStr());
        }
        message.setSeqNo(getNextSeqNo(message.getConvKey()));
        save(message);
        String key = StrUtil.join(":", RedisKey.IM_PRIVATE_MESSAGE_MAX_ID, message.getConvKey());
        redisTemplate.opsForValue().set(key, message.getId(), Constant.MAX_OFFLINE_MESSAGE_DAYS, TimeUnit.DAYS);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public PrivateMessageVO recallMessage(Long id) {
        Long userId = UserContext.getUserId();
        String userName = UserContext.get().getNickname();
        PrivateMessage recallMessage = this.getById(id);
        if (Objects.isNull(recallMessage)) {
            throw new GlobalException(ResultCode.HAS_NO_THIS_RESOURCE.getCode(), "消息不存在");
        }
        if (!recallMessage.getSendId().equals(userId)) {
            throw new GlobalException(ResultCode.CAN_NOT_ALLOW.getCode(), "这不是您发送的消息");
        }
        if (System.currentTimeMillis() - recallMessage.getSendTime().getTime() > IMConstant.ALLOW_RECALL_SECOND * 1000) {
            throw new GlobalException(ResultCode.TIME_OUT.getCode(), "消息已发送太长时间无法撤回");
        }
        // 记录撤回提示语
        String recallTip = String.format("%s 撤回一条消息", userName);
        recallMessage.setStatus(MessageStatus.RECALL.getCode());
        this.updateById(recallMessage);
        PrivateMessage message = new PrivateMessage();
        message.setLocalId(IdWorker.getIdStr());
        message.setConvKey(recallMessage.getConvKey());
        message.setSendId(userId);
        message.setStatus(MessageStatus.PENDING.getCode());
        message.setSendTime(new Date());
        message.setRecvId(recallMessage.getRecvId());
        message.setType(MessageType.RECALL.getCode());
        Map<String, Object> contentMap = new HashMap<>();
        contentMap.put("id", id);
        contentMap.put("tip", recallTip);
        message.setContent(JSON.toJSONString(contentMap));
        selfService.saveMessage(message);
        // TODO 推送撤回消息给对方(im-client/im-server 完成后实现)
        PrivateMessageVO vo = BeanUtil.copyProperties(message, PrivateMessageVO.class);
        vo.setDeleted(false);
        log.info("撤回私聊消息，发送id:{},接收id:{}，内容:{}", message.getSendId(), message.getRecvId(),
                message.getContent());
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void readedMessage(Long friendId, Long messageId) {
        Long userId = UserContext.getUserId();
        String convKey = ConvUtil.buildConvKey(userId, friendId);
        // 1.把对方发给我的、尚未标记已读的消息置为已读;messageId为空表示该会话全部已读
        LambdaUpdateWrapper<PrivateMessage> wrapper = Wrappers.lambdaUpdate();
        wrapper.eq(PrivateMessage::getConvKey, convKey)
                .eq(PrivateMessage::getSendId, friendId)
                .eq(PrivateMessage::getRecvId, userId)
                .lt(PrivateMessage::getStatus, MessageStatus.READED.getCode());
        if (Objects.nonNull(messageId)) {
            wrapper.le(PrivateMessage::getId, messageId);
        }
        wrapper.set(PrivateMessage::getStatus, MessageStatus.READED.getCode());
        this.update(wrapper);
        // 2.记录该会话已读到的最大消息id,前端据此计算未读数
        long boundary = Objects.nonNull(messageId) ? messageId : findMaxMessageId(convKey);
        advanceReadedPosition(userId, friendId, boundary);
        // TODO 推送已读回执给对方(im-client/im-server 完成后实现)
        log.info("私聊消息已读,用户id:{},好友id:{},已读到:{}", userId, friendId, boundary);
    }

    @Override
    public List<PrivateMessageVO> loadOfflineMessage(Long minId) {
        Long userId = UserContext.getUserId();
        Date minDate = DateUtils.addDays(new Date(), Math.toIntExact(-Constant.MAX_OFFLINE_MESSAGE_DAYS));
        LambdaQueryWrapper<PrivateMessage> wrapper = Wrappers.lambdaQuery();
        wrapper.gt(PrivateMessage::getId, minId).ge(PrivateMessage::getSendTime, minDate);
        // 必须整体加括号:写成 .and(wp->eq(sendId)).or().eq(recvId) 会因为 AND 优先级高于 OR
        // 而变成 (id>? AND send_time>=? AND send_id=?) OR recv_id=?,使接收分支绕过时间与id过滤
        wrapper.and(wp -> wp.eq(PrivateMessage::getSendId, userId).or().eq(PrivateMessage::getRecvId, userId));
        wrapper.orderByDesc(PrivateMessage::getId).last("limit " + Constant.MAX_OFFLINE_MESSAGE_SIZE);
        List<PrivateMessage> messages = new ArrayList<>(this.list(wrapper));
        if (messages.size() >= Constant.MAX_OFFLINE_MESSAGE_SIZE) {
            messages = new ArrayList<>(appendLastMessageInConversation(messages, minId));
        }
        // 按id正序返回,前端顺序追加即可
        messages.sort(Comparator.comparingLong(m -> Objects.requireNonNullElse(m.getId(), 0L)));
        return toVoList(userId, messages);
    }

    @Override
    public List<PrivateMessageVO> loadHistoryMessage(PrivateMessageHistoryDTO dto) {
        Long userId = UserContext.getUserId();
        String convKey = ConvUtil.buildConvKey(userId, dto.getFriendId());
        LambdaQueryWrapper<PrivateMessage> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(PrivateMessage::getConvKey, convKey);
        // 三种条件按优先级互斥使用:本地id列表 > 序号列表 > 序号区间
        if (Objects.nonNull(dto.getLocalIds()) && !dto.getLocalIds().isEmpty()) {
            wrapper.in(PrivateMessage::getLocalId, dto.getLocalIds());
        } else if (Objects.nonNull(dto.getSeqNos()) && !dto.getSeqNos().isEmpty()) {
            wrapper.in(PrivateMessage::getSeqNo, dto.getSeqNos());
        } else {
            if (Objects.nonNull(dto.getMinSeqNo())) {
                wrapper.gt(PrivateMessage::getSeqNo, dto.getMinSeqNo());
            }
            if (Objects.nonNull(dto.getMaxSeqNo()) && dto.getMaxSeqNo() > 0) {
                wrapper.lt(PrivateMessage::getSeqNo, dto.getMaxSeqNo());
            }
        }
        wrapper.orderByDesc(PrivateMessage::getSeqNo).last("limit " + Constant.MAX_HISTORY_MESSAGE_SIZE);
        List<PrivateMessage> messages = new ArrayList<>(this.list(wrapper));
        messages.sort(Comparator.comparingLong(m -> Objects.requireNonNullElse(m.getSeqNo(), 0L)));
        return toVoList(userId, messages);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteMessage(MessageDeleteDTO dto) {
        Long userId = UserContext.getUserId();
        String convKey = ConvUtil.buildConvKey(userId, dto.getChatId());
        // 只允许删除该会话内真实存在的消息,避免伪造id往删除表里塞数据
        List<Long> existIds = findExistMessageIds(convKey, dto.getMessageIds());
        if (existIds.isEmpty()) {
            return;
        }
        messageDeletionService.deleteMessage(userId, ChatType.PRIVATE, dto.getChatId(), existIds);
        // TODO 同步消息给自己其它端(im-client/im-server 完成后实现)
        log.info("删除私聊消息,用户id:{},好友id:{},消息id:{}", userId, dto.getChatId(), existIds);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteChat(ChatDeleteDTO dto) {
        Long userId = UserContext.getUserId();
        String convKey = ConvUtil.buildConvKey(userId, dto.getChatId());
        // 以当前会话最大消息id为界,该id及之前的消息对该用户不可见
        long boundary = findMaxMessageId(convKey);
        messageDeletionService.deleteChat(userId, ChatType.PRIVATE, dto.getChatId(), boundary);
        // TODO 同步消息给自己其它端(im-client/im-server 完成后实现)
        log.info("删除私聊会话,用户id:{},好友id:{},边界消息id:{}", userId, dto.getChatId(), boundary);
    }

    /**
     * 把每个会话的最新一条消息补进来。离线拉取只取最近 N 条,
     * 不补齐的话久未联系的会话在前端就完全看不到消息了
     */
    private List<PrivateMessage> appendLastMessageInConversation(List<PrivateMessage> messages, Long minId) {
        Long userId = UserContext.getUserId();
        List<Long> fIds = friendService.findFriendIds();
        Set<Long> existIds = messages.stream().map(m -> getFriendId(userId, m)).collect(Collectors.toSet());
        fIds.removeAll(existIds);
        if (fIds.isEmpty()) {
            return messages;
        }
        List<String> keys = fIds.stream().map(id -> buildMaxMessageIdKey(userId, id)).toList();
        List<Object> maxMessageIds = redisTemplate.opsForValue().multiGet(keys);
        if (Objects.isNull(maxMessageIds)) {
            return messages;
        }
        List<Long> validIds = maxMessageIds.stream()
                .filter(Objects::nonNull)
                .map(id -> Long.parseLong(id.toString()))
                .filter(id -> id > minId)
                .toList();
        if (validIds.isEmpty()) {
            return messages;
        }
        LambdaQueryWrapper<PrivateMessage> wrapper = Wrappers.lambdaQuery();
        Date minDate = DateUtils.addDays(new Date(), Math.toIntExact(-Constant.MAX_OFFLINE_MESSAGE_DAYS));
        wrapper.ge(PrivateMessage::getSendTime, minDate);
        wrapper.in(PrivateMessage::getId, validIds);
        List<PrivateMessage> lastMessages = this.list(wrapper);
        List<PrivateMessage> result = new ArrayList<>(messages);
        result.addAll(lastMessages);
        return result;
    }

    /**
     * 转换为VO,并按会话维度批量计算"当前用户已删除"标记。
     * 删除记录是按(用户,会话)存的,所以离线消息跨多个会话时要分组查询
     */
    private List<PrivateMessageVO> toVoList(Long userId, List<PrivateMessage> messages) {
        if (messages.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, List<PrivateMessage>> byChat = messages.stream()
                .collect(Collectors.groupingBy(m -> getFriendId(userId, m)));
        Set<Long> deletedIds = new HashSet<>();
        byChat.forEach((chatId, list) -> {
            List<Long> ids = list.stream().map(PrivateMessage::getId).toList();
            deletedIds.addAll(messageDeletionService.findDeletedMessageIds(
                    userId, ChatType.PRIVATE, chatId, ids));
        });
        return messages.stream().map(m -> {
            PrivateMessageVO vo = BeanUtil.copyProperties(m, PrivateMessageVO.class);
            vo.setDeleted(deletedIds.contains(m.getId()));
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 校验这些消息id确实属于该会话,返回其中真实存在的id
     */
    private List<Long> findExistMessageIds(String convKey, List<Long> messageIds) {
        LambdaQueryWrapper<PrivateMessage> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(PrivateMessage::getConvKey, convKey)
                .in(PrivateMessage::getId, messageIds)
                .select(PrivateMessage::getId);
        return this.list(wrapper).stream().map(PrivateMessage::getId).toList();
    }

    /**
     * 会话内最大的消息id,没有消息返回0
     */
    private long findMaxMessageId(String convKey) {
        LambdaQueryWrapper<PrivateMessage> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(PrivateMessage::getConvKey, convKey)
                .orderByDesc(PrivateMessage::getId)
                .last("limit 1")
                .select(PrivateMessage::getId);
        PrivateMessage last = this.getOne(wrapper);
        return Objects.isNull(last) || Objects.isNull(last.getId()) ? 0L : last.getId();
    }

    /**
     * 推进已读位置。已读位置只能前进不能后退,避免乱序请求把未读数算多
     */
    private void advanceReadedPosition(Long userId, Long friendId, long boundary) {
        if (boundary <= 0) {
            return;
        }
        String key = StrUtil.join(":", RedisKey.IM_PRIVATE_READED_POSITION, userId);
        String field = friendId.toString();
        Object current = redisTemplate.opsForHash().get(key, field);
        if (Objects.isNull(current) || Long.parseLong(current.toString()) < boundary) {
            redisTemplate.opsForHash().put(key, field, boundary);
        }
    }

    private String buildMaxMessageIdKey(Long userId, Long id) {
        return StrUtil.join(":", RedisKey.IM_PRIVATE_MESSAGE_MAX_ID, ConvUtil.buildConvKey(userId, id));
    }

    private Long getFriendId(Long userId, PrivateMessage m) {
        return userId.equals(m.getSendId()) ? m.getRecvId() : m.getSendId();
    }

    private Long getNextSeqNo(String convKey) {
        String key = StrUtil.join(":", RedisKey.IM_PRIVATE_MESSAGE_MAX_SEQ, convKey);
        // 计数器缺失时需要回填DB最大值,回填与自增必须在同一把锁内,且锁按会话隔离。
        // 旧实现在锁外先 increment 再 delete(key),并发发送时会把其他线程已取到的值抹掉,
        // 导致同一会话分配出重复 seq_no(库里只是普通索引,不会报错)
        RLock lock = redissonClient.getLock(RedisKey.IM_LOCK_PRIVATE_MESSAGE_MAX_SEQ + ":" + convKey);
        lock.lock();
        try {
            if (!redisTemplate.hasKey(key)) {
                LambdaQueryWrapper<PrivateMessage> wrapper = Wrappers.<PrivateMessage>lambdaQuery();
                wrapper.eq(PrivateMessage::getConvKey, convKey);
                wrapper.orderByDesc(PrivateMessage::getSeqNo).last("limit 1");
                PrivateMessage lastMessage = this.getOne(wrapper);
                long init = (Objects.isNull(lastMessage) || Objects.isNull(lastMessage.getSeqNo()))
                        ? 0L : lastMessage.getSeqNo();
                redisTemplate.opsForValue().setIfAbsent(key, init);
            }
            return redisTemplate.opsForValue().increment(key);
        } finally {
            lock.unlock();
        }
    }

    private void validMessage(PrivateMessageDTO dto) {
        // 文字消息-长度校验
        if (MessageType.TEXT.getCode().equals(dto.getType()) && dto.getContent().length() > Constant.MAX_MESSAGE_LENGTH) {
            throw new GlobalException(ResultCode.FILL_MAX_ALLOW.getCode(),
                    String.format("消息长度不能大于%s个字符", Constant.MAX_MESSAGE_LENGTH));
        }
        try {
            // 非文字消息-保证数据格式是json，防止前端报错
            if (!MessageType.TEXT.getCode().equals(dto.getType())) {
                JSON.parse(dto.getContent());
            }
        } catch (Exception e) {
            throw new GlobalException(ResultCode.FORMAT_FAILED);
        }
    }
}
