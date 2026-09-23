package com.him.implatform.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.him.imcommon.constant.IMConstant;
import com.him.imcommon.util.BeanUtil;
import com.him.implatform.constant.Constant;
import com.him.implatform.constant.RedisKey;
import com.him.implatform.context.UserContext;
import com.him.implatform.dto.ChatDeleteDTO;
import com.him.implatform.dto.GroupMessageDTO;
import com.him.implatform.dto.GroupMessageHistoryDTO;
import com.him.implatform.dto.MessageDeleteDTO;
import com.him.implatform.entity.GroupMember;
import com.him.implatform.entity.GroupMessage;
import com.him.implatform.enums.ChatType;
import com.him.implatform.enums.MessageStatus;
import com.him.implatform.enums.MessageType;
import com.him.implatform.enums.ResultCode;
import com.him.implatform.exception.GlobalException;
import com.him.implatform.mapper.GroupMessageMapper;
import com.him.implatform.service.GroupMemberService;
import com.him.implatform.service.GroupMessageService;
import com.him.implatform.service.GroupService;
import com.him.implatform.service.MessageDeletionService;
import com.him.implatform.service.SensitiveWordService;
import com.him.implatform.vo.GroupMessageVO;
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
public class GroupMessageServiceImpl extends ServiceImpl<GroupMessageMapper, GroupMessage>
        implements GroupMessageService {

    private final GroupService groupService;
    private final GroupMemberService groupMemberService;
    private final MessageDeletionService messageDeletionService;
    private final SensitiveWordService sensitiveWordService;

    @Resource
    @Lazy
    private GroupMessageService selfService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private RedissonClient redissonClient;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public GroupMessageVO sendMessage(GroupMessageDTO dto) {
        Long userId = UserContext.getUserId();
        // 群必须存在且未解散/未封禁
        groupService.getAndCheckById(dto.getGroupId());
        // 发送者必须是群成员
        GroupMember member = checkMember(dto.getGroupId(), userId);
        validMessage(dto);
        GroupMessage message = new GroupMessage();
        message.setLocalId(dto.getLocalId());
        message.setGroupId(dto.getGroupId());
        message.setSendId(userId);
        message.setSendNickName(member.getShowNickName());
        message.setType(dto.getType());
        message.setStatus(MessageStatus.PENDING.getCode());
        message.setReceipt(Boolean.TRUE.equals(dto.getReceipt()));
        message.setReceiptOk(false);
        message.setAtUserIds(GroupMessage.joinUserIds(dto.getAtUserIds()));
        message.setSendTime(new Date());
        // 敏感词过滤只作用于文字消息:图片/文件等内容是JSON,整体替换会破坏结构
        if (MessageType.TEXT.getCode().equals(dto.getType())) {
            message.setContent(sensitiveWordService.filter(dto.getContent()));
        } else {
            message.setContent(dto.getContent());
        }
        selfService.saveMessage(message);
        // TODO 推送群聊消息给群内其他成员(im-client/im-server 完成后实现)
        GroupMessageVO vo = convert(message);
        vo.setReadedCount(0);
        vo.setDeleted(false);
        log.info("发送群聊消息,群id:{},发送id:{},内容:{}", dto.getGroupId(), userId, dto.getContent());
        return vo;
    }

    @Override
    public void saveMessage(GroupMessage message) {
        if (StrUtil.isEmpty(message.getLocalId())) {
            message.setLocalId(IdWorker.getIdStr());
        }
        message.setSeqNo(getNextSeqNo(message.getGroupId()));
        save(message);
        // 缓存群内最新消息id,离线拉取时给没有消息的群补一条最新消息
        String key = StrUtil.join(":", RedisKey.IM_GROUP_MESSAGE_MAX_ID, message.getGroupId());
        redisTemplate.opsForValue().set(key, message.getId(), Constant.MAX_OFFLINE_MESSAGE_DAYS, TimeUnit.DAYS);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public GroupMessageVO recallMessage(Long id) {
        Long userId = UserContext.getUserId();
        GroupMessage recallMessage = this.getById(id);
        if (Objects.isNull(recallMessage)) {
            throw new GlobalException(ResultCode.HAS_NO_THIS_RESOURCE.getCode(), "消息不存在");
        }
        if (!recallMessage.getSendId().equals(userId)) {
            throw new GlobalException(ResultCode.CAN_NOT_ALLOW.getCode(), "这不是您发送的消息");
        }
        if (System.currentTimeMillis() - recallMessage.getSendTime().getTime() > IMConstant.ALLOW_RECALL_SECOND * 1000) {
            throw new GlobalException(ResultCode.TIME_OUT.getCode(), "消息已发送太长时间无法撤回");
        }
        recallMessage.setStatus(MessageStatus.RECALL.getCode());
        this.updateById(recallMessage);
        // 生成一条"xxx撤回一条消息"的提示消息
        GroupMember member = groupMemberService.findByGroupAndUserId(recallMessage.getGroupId(), userId);
        String userName = Objects.isNull(member) ? UserContext.get().getNickname() : member.getShowNickName();
        String recallTip = String.format("%s 撤回一条消息", userName);
        GroupMessage tipMessage = new GroupMessage();
        tipMessage.setLocalId(IdWorker.getIdStr());
        tipMessage.setGroupId(recallMessage.getGroupId());
        tipMessage.setSendId(userId);
        tipMessage.setSendNickName(recallMessage.getSendNickName());
        tipMessage.setType(MessageType.RECALL.getCode());
        tipMessage.setStatus(MessageStatus.PENDING.getCode());
        tipMessage.setReceipt(false);
        tipMessage.setReceiptOk(false);
        tipMessage.setAtUserIds("");
        tipMessage.setSendTime(new Date());
        Map<String, Object> contentMap = new HashMap<>();
        contentMap.put("id", id);
        contentMap.put("tip", recallTip);
        tipMessage.setContent(JSON.toJSONString(contentMap));
        selfService.saveMessage(tipMessage);
        // TODO 推送撤回消息给群成员(im-client/im-server 完成后实现)
        GroupMessageVO vo = convert(tipMessage);
        vo.setReadedCount(0);
        vo.setDeleted(false);
        log.info("撤回群聊消息,群id:{},发送id:{},原消息id:{}", recallMessage.getGroupId(), userId, id);
        return vo;
    }

    @Override
    public List<GroupMessageVO> loadOfflineMessage(Long minId) {
        Long userId = UserContext.getUserId();
        // 只拉取自己仍在的群,退群后的消息不再下发
        List<Long> groupIds = groupMemberService.findByUserId(userId).stream()
                .map(GroupMember::getGroupId)
                .distinct()
                .collect(Collectors.toList());
        if (groupIds.isEmpty()) {
            return new ArrayList<>();
        }
        Date minDate = DateUtils.addDays(new Date(), Math.toIntExact(-Constant.MAX_OFFLINE_MESSAGE_DAYS));
        LambdaQueryWrapper<GroupMessage> wrapper = Wrappers.lambdaQuery();
        wrapper.in(GroupMessage::getGroupId, groupIds)
                .gt(GroupMessage::getId, minId)
                .ge(GroupMessage::getSendTime, minDate);
        wrapper.orderByDesc(GroupMessage::getId).last("limit " + Constant.MAX_OFFLINE_MESSAGE_SIZE);
        List<GroupMessage> messages = new ArrayList<>(this.list(wrapper));
        if (messages.size() >= Constant.MAX_OFFLINE_MESSAGE_SIZE) {
            messages = new ArrayList<>(appendLastMessageInGroup(messages, groupIds, minId));
        }
        messages.sort(Comparator.comparingLong(m -> Objects.requireNonNullElse(m.getId(), 0L)));
        return toVoList(userId, messages);
    }

    @Override
    public List<GroupMessageVO> loadHistoryMessage(GroupMessageHistoryDTO dto) {
        Long userId = UserContext.getUserId();
        // 历史消息只有群成员能查
        checkMember(dto.getGroupId(), userId);
        LambdaQueryWrapper<GroupMessage> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(GroupMessage::getGroupId, dto.getGroupId());
        // 三种条件按优先级互斥使用:本地id列表 > 序号列表 > 序号区间
        if (Objects.nonNull(dto.getLocalIds()) && !dto.getLocalIds().isEmpty()) {
            wrapper.in(GroupMessage::getLocalId, dto.getLocalIds());
        } else if (Objects.nonNull(dto.getSeqNos()) && !dto.getSeqNos().isEmpty()) {
            wrapper.in(GroupMessage::getSeqNo, dto.getSeqNos());
        } else {
            if (Objects.nonNull(dto.getMinSeqNo())) {
                wrapper.gt(GroupMessage::getSeqNo, dto.getMinSeqNo());
            }
            if (Objects.nonNull(dto.getMaxSeqNo()) && dto.getMaxSeqNo() > 0) {
                wrapper.lt(GroupMessage::getSeqNo, dto.getMaxSeqNo());
            }
        }
        wrapper.orderByDesc(GroupMessage::getSeqNo).last("limit " + Constant.MAX_HISTORY_MESSAGE_SIZE);
        List<GroupMessage> messages = new ArrayList<>(this.list(wrapper));
        messages.sort(Comparator.comparingLong(m -> Objects.requireNonNullElse(m.getSeqNo(), 0L)));
        return toVoList(userId, messages);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void readedMessage(Long groupId, Long messageId) {
        Long userId = UserContext.getUserId();
        checkMember(groupId, userId);
        // 推进本人在该群的已读位置(已读到的最大消息id)
        long boundary = Objects.nonNull(messageId) ? messageId : findMaxMessageId(groupId);
        if (boundary <= 0) {
            return;
        }
        setReadedPosition(groupId, userId, boundary);
        // 回执消息:群内所有人都读过之后才算完成
        updateReceiptOk(groupId, boundary);
        // TODO 推送已读回执给发送者(im-client/im-server 完成后实现)
        log.info("群聊消息已读,群id:{},用户id:{},已读到:{}", groupId, userId, boundary);
    }

    @Override
    public List<Long> findReadedUsers(Long groupId, Long messageId) {
        Long userId = UserContext.getUserId();
        checkMember(groupId, userId);
        GroupMessage message = this.getById(messageId);
        if (Objects.isNull(message) || !groupId.equals(message.getGroupId())) {
            throw new GlobalException(ResultCode.HAS_NO_THIS_RESOURCE.getCode(), "消息不存在");
        }
        // 已读位置 >= 该消息id的成员即为已读
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(readedKey(groupId));
        return entries.entrySet().stream()
                .filter(e -> Objects.nonNull(e.getValue())
                        && Long.parseLong(e.getValue().toString()) >= messageId)
                .map(e -> Long.parseLong(e.getKey().toString()))
                .sorted()
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteMessage(MessageDeleteDTO dto) {
        Long userId = UserContext.getUserId();
        checkMember(dto.getChatId(), userId);
        // 只允许删除该群内真实存在的消息,避免伪造id往删除表里塞数据
        List<Long> existIds = findExistMessageIds(dto.getChatId(), dto.getMessageIds());
        if (existIds.isEmpty()) {
            return;
        }
        messageDeletionService.deleteMessage(userId, ChatType.GROUP, dto.getChatId(), existIds);
        // TODO 同步消息给自己其它端(im-client/im-server 完成后实现)
        log.info("删除群聊消息,用户id:{},群id:{},消息id:{}", userId, dto.getChatId(), existIds);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteChat(ChatDeleteDTO dto) {
        Long userId = UserContext.getUserId();
        checkMember(dto.getChatId(), userId);
        // 以当前群内最大消息id为界,该id及之前的消息对该用户不可见
        long boundary = findMaxMessageId(dto.getChatId());
        messageDeletionService.deleteChat(userId, ChatType.GROUP, dto.getChatId(), boundary);
        // TODO 同步消息给自己其它端(im-client/im-server 完成后实现)
        log.info("删除群聊会话,用户id:{},群id:{},边界消息id:{}", userId, dto.getChatId(), boundary);
    }

    /**
     * 补上每个群的最新一条消息。离线拉取只取最近 N 条,
     * 不补齐的话久未活跃的群在前端就完全看不到消息了
     */
    private List<GroupMessage> appendLastMessageInGroup(List<GroupMessage> messages, List<Long> groupIds, Long minId) {
        Set<Long> existGroupIds = messages.stream().map(GroupMessage::getGroupId).collect(Collectors.toSet());
        List<Long> missingGroupIds = groupIds.stream().filter(id -> !existGroupIds.contains(id)).toList();
        if (missingGroupIds.isEmpty()) {
            return messages;
        }
        List<String> keys = missingGroupIds.stream()
                .map(id -> StrUtil.join(":", RedisKey.IM_GROUP_MESSAGE_MAX_ID, id))
                .toList();
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
        LambdaQueryWrapper<GroupMessage> wrapper = Wrappers.lambdaQuery();
        Date minDate = DateUtils.addDays(new Date(), Math.toIntExact(-Constant.MAX_OFFLINE_MESSAGE_DAYS));
        wrapper.ge(GroupMessage::getSendTime, minDate).in(GroupMessage::getId, validIds);
        List<GroupMessage> result = new ArrayList<>(messages);
        result.addAll(this.list(wrapper));
        return result;
    }

    /**
     * 转换为VO,并按群维度计算删除标记与已读人数。
     * 删除记录和已读位置都是按群维护的,离线消息跨多个群时按群分组查询
     */
    private List<GroupMessageVO> toVoList(Long userId, List<GroupMessage> messages) {
        if (messages.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, List<GroupMessage>> byGroup = messages.stream()
                .collect(Collectors.groupingBy(GroupMessage::getGroupId));
        Set<Long> deletedIds = new HashSet<>();
        Map<Long, List<Long>> readedPositions = new HashMap<>();
        byGroup.forEach((groupId, list) -> {
            List<Long> ids = list.stream().map(GroupMessage::getId).toList();
            deletedIds.addAll(messageDeletionService.findDeletedMessageIds(userId, ChatType.GROUP, groupId, ids));
            readedPositions.put(groupId, loadSortedReadedPositions(groupId));
        });
        return messages.stream().map(m -> {
            GroupMessageVO vo = convert(m);
            vo.setDeleted(deletedIds.contains(m.getId()));
            List<Long> positions = readedPositions.getOrDefault(m.getGroupId(), List.of());
            long id = Objects.requireNonNullElse(m.getId(), 0L);
            long readed = countReaded(positions, id);
            // 发送者本人也算已读,避免自己发的消息显示为0人已读
            if (Objects.equals(m.getSendId(), userId) && id > 0) {
                readed = Math.max(readed, 1);
            }
            vo.setReadedCount((int) readed);
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 取该群所有成员的已读位置并升序排列,便于二分统计已读人数
     */
    private List<Long> loadSortedReadedPositions(Long groupId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(readedKey(groupId));
        return entries.values().stream()
                .filter(Objects::nonNull)
                .map(v -> Long.parseLong(v.toString()))
                .sorted()
                .toList();
    }

    /**
     * 统计已读位置 >= messageId 的成员数
     */
    private int countReaded(List<Long> sortedPositions, long messageId) {
        if (messageId <= 0 || sortedPositions.isEmpty()) {
            return 0;
        }
        int low = 0;
        int high = sortedPositions.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (sortedPositions.get(mid) >= messageId) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }
        return sortedPositions.size() - low;
    }

    /**
     * 把所有成员都已读的回执消息标记为已完成
     */
    private void updateReceiptOk(Long groupId, long boundary) {
        LambdaQueryWrapper<GroupMessage> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(GroupMessage::getGroupId, groupId)
                .eq(GroupMessage::getReceipt, true)
                .eq(GroupMessage::getReceiptOk, false)
                .le(GroupMessage::getId, boundary);
        List<GroupMessage> messages = this.list(wrapper);
        if (messages.isEmpty()) {
            return;
        }
        long memberCount = groupMemberService.findUserIdsByGroupId(groupId).size();
        if (memberCount <= 0) {
            return;
        }
        List<Long> positions = loadSortedReadedPositions(groupId);
        for (GroupMessage message : messages) {
            if (Objects.isNull(message.getId())) {
                continue;
            }
            if (countReaded(positions, message.getId()) >= memberCount) {
                message.setReceiptOk(true);
                this.updateById(message);
                // TODO 推送"消息已全部已读"回执给发送者(im-client/im-server 完成后实现)
                log.info("群聊回执消息已全部已读,群id:{},消息id:{}", groupId, message.getId());
            }
        }
    }

    /**
     * 推进已读位置。已读位置只能前进不能后退,避免乱序请求把未读数算多
     */
    private void setReadedPosition(Long groupId, Long userId, long boundary) {
        String key = readedKey(groupId);
        String field = userId.toString();
        Object current = redisTemplate.opsForHash().get(key, field);
        if (Objects.isNull(current) || Long.parseLong(current.toString()) < boundary) {
            redisTemplate.opsForHash().put(key, field, boundary);
        }
    }

    private String readedKey(Long groupId) {
        return StrUtil.join(":", RedisKey.IM_GROUP_READED_POSITION, groupId);
    }

    /**
     * 校验当前用户是该群的有效成员并返回成员信息
     */
    private GroupMember checkMember(Long groupId, Long userId) {
        GroupMember member = groupMemberService.findByGroupAndUserId(groupId, userId);
        if (Objects.isNull(member) || Boolean.TRUE.equals(member.getQuit())) {
            throw new GlobalException(ResultCode.YOU_NOT_IN_GROUP);
        }
        return member;
    }

    /**
     * 校验这些消息id确实属于该群,返回其中真实存在的id
     */
    private List<Long> findExistMessageIds(Long groupId, List<Long> messageIds) {
        LambdaQueryWrapper<GroupMessage> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(GroupMessage::getGroupId, groupId)
                .in(GroupMessage::getId, messageIds)
                .select(GroupMessage::getId);
        return this.list(wrapper).stream().map(GroupMessage::getId).toList();
    }

    /**
     * 群内最大的消息id,没有消息返回0
     */
    private long findMaxMessageId(Long groupId) {
        LambdaQueryWrapper<GroupMessage> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(GroupMessage::getGroupId, groupId)
                .orderByDesc(GroupMessage::getId)
                .last("limit 1")
                .select(GroupMessage::getId);
        GroupMessage last = this.getOne(wrapper);
        return Objects.isNull(last) || Objects.isNull(last.getId()) ? 0L : last.getId();
    }

    private Long getNextSeqNo(Long groupId) {
        String key = StrUtil.join(":", RedisKey.IM_GROUP_MESSAGE_MAX_SEQ, groupId);
        // 回填DB最大值与自增必须在同一把锁内,且锁按群隔离,否则并发发送会分配出重复序号
        RLock lock = redissonClient.getLock(RedisKey.IM_LOCK_GROUP_MESSAGE_MAX_SEQ + ":" + groupId);
        lock.lock();
        try {
            if (!redisTemplate.hasKey(key)) {
                LambdaQueryWrapper<GroupMessage> wrapper = Wrappers.lambdaQuery();
                wrapper.eq(GroupMessage::getGroupId, groupId);
                wrapper.orderByDesc(GroupMessage::getSeqNo).last("limit 1");
                GroupMessage lastMessage = this.getOne(wrapper);
                long init = (Objects.isNull(lastMessage) || Objects.isNull(lastMessage.getSeqNo()))
                        ? 0L : lastMessage.getSeqNo();
                redisTemplate.opsForValue().setIfAbsent(key, init);
            }
            return redisTemplate.opsForValue().increment(key);
        } finally {
            lock.unlock();
        }
    }

    private GroupMessageVO convert(GroupMessage message) {
        GroupMessageVO vo = BeanUtil.copyProperties(message, GroupMessageVO.class);
        vo.setAtUserIds(GroupMessage.parseUserIds(message.getAtUserIds()));
        return vo;
    }

    private void validMessage(GroupMessageDTO dto) {
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
