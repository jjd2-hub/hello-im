package com.him.implatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.him.implatform.constant.RedisKey;
import com.him.implatform.context.UserContext;
import com.him.implatform.entity.GroupMember;
import com.him.implatform.mapper.GroupMemberMapper;
import com.him.implatform.service.GroupMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class GroupMemberServiceImpl extends ServiceImpl<GroupMemberMapper, GroupMember> implements GroupMemberService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public GroupMember findByGroupAndUserId(Long groupId, Long userId) {
        LambdaQueryWrapper<GroupMember> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(GroupMember::getGroupId, groupId).eq(GroupMember::getUserId, userId);
        return this.getOne(wrapper);
    }

    @Override
    public Long getNextVersion() {
        String key = RedisKey.IM_GROUP_MEMBER_MAX_VERSION;
        if (redisTemplate.hasKey(key)) {
            return (Long) redisTemplate.opsForValue().increment(key);
        } else {
            LambdaQueryWrapper<GroupMember> wrapper = Wrappers.lambdaQuery();
            wrapper.orderByDesc(GroupMember::getVersion).last("limit 1");
            GroupMember groupMember = this.getOne(wrapper);
            Long version = Objects.isNull(groupMember) ? 1L : groupMember.getVersion() + 1;
            redisTemplate.opsForValue().set(key, version);
            return version;
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
        LambdaQueryWrapper<GroupMember> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(GroupMember::getGroupId, groupId).eq(GroupMember::getQuit, false);
        wrapper.select(GroupMember::getUserId);
        List<GroupMember> members = this.list(wrapper);
        return members.stream().map(GroupMember::getUserId).toList();
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
