package com.him.implatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.him.imcommon.constant.RedisKey;
import com.him.implatform.context.UserContext;
import com.him.implatform.entity.GroupMember;
import com.him.implatform.mapper.GroupMemberMapper;
import com.him.implatform.service.GroupMemberService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class GroupMemberServiceImpl extends ServiceImpl<GroupMemberMapper, GroupMember> implements GroupMemberService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;

    @Override
    public GroupMember findByGroupAndUserId(Long groupId, Long userId) {
        LambdaQueryWrapper<GroupMember> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(GroupMember::getGroupId, groupId).eq(GroupMember::getUserId, userId);
        return this.getOne(wrapper);
    }

    @Override
    public Long getNextVersion() {
        String key = RedisKey.IM_GROUP_MEMBER_MAX_VERSION;
        // 计数器缺失时需要回填DB最大值,回填与自增必须在同一把锁内,否则并发下会分配出重复版本号,
        // 而客户端按 version 增量同步时,重复版本号会导致其中一次变更永久丢失
        RLock lock = redissonClient.getLock(RedisKey.IM_LOCK_GROUP_MEMBER_MAX_VERSION);
        lock.lock();
        try {
            if (!redisTemplate.hasKey(key)) {
                LambdaQueryWrapper<GroupMember> wrapper = Wrappers.lambdaQuery();
                wrapper.orderByDesc(GroupMember::getVersion).last("limit 1");
                GroupMember groupMember = this.getOne(wrapper);
                long init = (Objects.isNull(groupMember) || Objects.isNull(groupMember.getVersion()))
                        ? 0L : groupMember.getVersion();
                redisTemplate.opsForValue().setIfAbsent(key, init);
            }
            return redisTemplate.opsForValue().increment(key);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Long> findUserIdsByGroupId(Long groupId) {
        LambdaQueryWrapper<GroupMember> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(GroupMember::getGroupId, groupId).eq(GroupMember::getQuit, false);
        wrapper.select(GroupMember::getUserId);
        List<GroupMember> groupMemberList = this.list(wrapper);
        return groupMemberList.stream().map(GroupMember::getUserId).toList();
    }

    @Override
    public void removeByGroupId(Long groupId) {
        Long version = getNextVersion();
        LambdaUpdateWrapper<GroupMember> wrapper = Wrappers.lambdaUpdate();
        wrapper.eq(GroupMember::getGroupId, groupId).eq(GroupMember::getQuit, false);
        wrapper.set(GroupMember::getQuit, true).set(GroupMember::getQuitTime, new Date());
        wrapper.set(GroupMember::getVersion, version);
        this.update(wrapper);
    }

    @Override
    public List<GroupMember> findByUserId(Long userId) {
        LambdaQueryWrapper<GroupMember> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(GroupMember::getUserId, userId).eq(GroupMember::getQuit, false);
        return this.list(wrapper);
    }

    @Override
    public List<GroupMember> findQuitMembers(Long userId, Date minQuitTime) {
        LambdaQueryWrapper<GroupMember> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(GroupMember::getUserId, userId).eq(GroupMember::getQuit, true);
        wrapper.ge(GroupMember::getQuitTime, minQuitTime);
        return this.list(wrapper);
    }

    @Override
    public List<GroupMember> findByGroupId(Long groupId, long version) {
        LambdaQueryWrapper<GroupMember> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(GroupMember::getGroupId, groupId);
        wrapper.gt(version > 0, GroupMember::getVersion, version);
        return this.list(wrapper);
    }

    @Override
    public boolean saveOrUpdateBatch(Long id, List<GroupMember> groupMembers) {
        Long version = getNextVersion();
        groupMembers.forEach(m -> m.setVersion(version));
        return super.saveOrUpdateBatch(groupMembers);
    }

    @Override
    public boolean removeByGroupAndUserIds(Long groupId, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return true;
        }
        Long version = getNextVersion();
        LambdaUpdateWrapper<GroupMember> wrapper = Wrappers.lambdaUpdate();
        wrapper.eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getQuit, false)
                .in(GroupMember::getUserId, userIds);
        wrapper.set(GroupMember::getQuit, true).set(GroupMember::getQuitTime, new Date());
        wrapper.set(GroupMember::getVersion, version);
        return this.update(wrapper);
    }

    @Override
    public void removeByGroupAndUserId(Long groupId) {
        Long userId = UserContext.getUserId();
        Long version = getNextVersion();
        LambdaUpdateWrapper<GroupMember> wrapper = Wrappers.lambdaUpdate();
        wrapper.eq(GroupMember::getGroupId, groupId).eq(GroupMember::getUserId, userId);
        wrapper.eq(GroupMember::getQuit, false);
        wrapper.set(GroupMember::getQuit, true).set(GroupMember::getQuitTime, new Date());
        wrapper.set(GroupMember::getVersion, version);
        this.update(wrapper);
    }

    @Override
    public void setDnd(Long groupId, Long userId, boolean isDnd) {
        Long version = getNextVersion();
        LambdaUpdateWrapper<GroupMember> wrapper = Wrappers.lambdaUpdate();
        wrapper.eq(GroupMember::getGroupId, groupId);
        wrapper.eq(GroupMember::getUserId, userId);
        wrapper.set(GroupMember::getIsDnd, isDnd);
        wrapper.set(GroupMember::getVersion, version);
        this.update(wrapper);
    }
}
