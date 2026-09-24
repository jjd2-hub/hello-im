package com.him.implatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.him.imcommon.constant.RedisKey;
import com.him.implatform.context.UserContext;
import com.him.implatform.entity.GroupMember;
import com.him.implatform.mapper.GroupMemberMapper;
import com.him.implatform.redis.DistributedCounter;
import com.him.implatform.service.GroupMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class GroupMemberServiceImpl extends ServiceImpl<GroupMemberMapper, GroupMember> implements GroupMemberService {

    private final DistributedCounter counter;

    @Override
    public GroupMember findByGroupAndUserId(Long groupId, Long userId) {
        LambdaQueryWrapper<GroupMember> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(GroupMember::getGroupId, groupId).eq(GroupMember::getUserId, userId);
        return this.getOne(wrapper);
    }

    @Override
    public Long getNextVersion() {
        // 计数器缺失时用DB最大值回填;回填与自增必须在同一把锁内完成(实现见 DistributedCounter)
        return counter.next(
                RedisKey.IM_GROUP_MEMBER_MAX_VERSION,
                RedisKey.IM_LOCK_GROUP_MEMBER_MAX_VERSION,
                () -> {
                    LambdaQueryWrapper<GroupMember> wrapper = Wrappers.lambdaQuery();
                    wrapper.orderByDesc(GroupMember::getVersion).last("limit 1");
                    GroupMember last = this.getOne(wrapper);
                    return Objects.isNull(last) || Objects.isNull(last.getVersion()) ? 0L : last.getVersion();
                });
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

    @Override
    public void addMember(GroupMember member) {
        // 走显式业务方法而不是把通用 save 暴露给调用方,
        // 这样"新增成员要带什么"这件事只在这里定义
        this.save(member);
    }

    @Override
    public void updateMember(GroupMember member) {
        this.updateById(member);
    }

    @Override
    public List<GroupMember> findByUserIdAndVersion(Long userId, Long version) {
        LambdaQueryWrapper<GroupMember> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(GroupMember::getUserId, userId)
                .gt(Objects.nonNull(version) && version > 0, GroupMember::getVersion, version);
        return this.list(wrapper);
    }
}
