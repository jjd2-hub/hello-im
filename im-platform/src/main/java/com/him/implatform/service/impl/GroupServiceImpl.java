package com.him.implatform.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.him.imcommon.util.BeanUtil;
import com.him.implatform.constant.Constant;
import com.him.implatform.constant.RedisKey;
import com.him.implatform.context.UserContext;
import com.him.implatform.dto.GroupDndDTO;
import com.him.implatform.dto.GroupInviteDTO;
import com.him.implatform.dto.GroupMemberRemoveDTO;
import com.him.implatform.entity.Friend;
import com.him.implatform.entity.Group;
import com.him.implatform.entity.GroupMember;
import com.him.implatform.entity.User;
import com.him.implatform.enums.ResultCode;
import com.him.implatform.exception.GlobalException;
import com.him.implatform.mapper.GroupMapper;
import com.him.implatform.service.FriendService;
import com.him.implatform.service.GroupMemberService;
import com.him.implatform.service.GroupService;
import com.him.implatform.service.UserService;
import com.him.implatform.vo.GroupMemberVO;
import com.him.implatform.vo.GroupVO;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@CacheConfig()
public class GroupServiceImpl extends ServiceImpl<GroupMapper, Group> implements GroupService {

    private final UserService userService;
    private final RedisTemplate<String, Object> redisTemplate;
    @Lazy
    @Resource
    private GroupMemberService groupMemberService;
    @Resource
    private FriendService friendService;


    @Override
    public GroupVO createGroup(GroupVO vo) {
        Long userId = UserContext.getUserId();
        User user = userService.getById(userId);
        Group group = BeanUtil.copyProperties(vo, Group.class);
        group.setOwnerId(userId);
        this.save(group);
        GroupMember member = new GroupMember();
        member.setUserId(userId);
        member.setGroupId(group.getId());
        member.setHeadImage(user.getHeadImageThumb());
        member.setUserNickName(user.getNickname());
        member.setRemarkNickName(vo.getRemarkNickName());
        member.setRemarkGroupName(vo.getRemarkGroupName());
        groupMemberService.save(member);
        GroupVO groupVO = this.findById(group.getId());
        // TODO
        // sendAddGroupMessage()
        log.info("创建群聊,id:{},名称:{}", group.getId(), group.getName());
        return groupVO;
    }

    @Override
    public GroupVO findById(Long id) {
        Long userId = UserContext.getUserId();
        Group group = super.getById(id);
        if (Objects.isNull(group)) {
            throw new GlobalException(ResultCode.HAS_NO_THIS_RESOURCE.getCode(), "没有该群聊");
        }
        GroupMember member = groupMemberService.findByGroupAndUserId(id, userId);
        if (Objects.isNull(member)) {
            throw new GlobalException(ResultCode.YOU_NOT_IN_GROUP);
        }
        return convert(group, member);
    }

    @Override
    public GroupVO modifyGroup(GroupVO vo) {
        Long userId = UserContext.getUserId();
        Group group = this.getAndCheckById(vo.getId());
        GroupMember member = groupMemberService.findByGroupAndUserId(group.getId(), userId);
        if (Objects.isNull(member) || member.getQuit()) {
            throw new GlobalException(ResultCode.YOU_NOT_IN_GROUP);
        }
        member.setRemarkNickName(vo.getRemarkNickName());
        member.setRemarkGroupName(vo.getRemarkGroupName());
        member.setVersion(groupMemberService.getNextVersion());
        groupMemberService.updateById(member);
        if (group.getOwnerId().equals(userId)) {
            group = BeanUtil.copyProperties(vo, Group.class);
            this.updateById(group);
        }
        log.info("修改群聊,id:{},名称:{}", group.getId(), group.getName());
        return convert(group, member);
    }

    @Override
    public Group getAndCheckById(Long id) {
        Group group = super.getById(id);
        if (Objects.isNull(group)) {
            throw new GlobalException(ResultCode.HAS_NO_THIS_RESOURCE.getCode(), "群聊不存在");
        }
        if (group.getDissolve()) {
            throw new GlobalException(ResultCode.CAN_NOT_ALLOW.getCode(), "群聊已解散");
        }
        if (group.getIsBanned()) {
            throw new GlobalException(ResultCode.CAN_NOT_ALLOW.getCode(), "群聊已封禁");
        }
        return group;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteGroup(Long groupId) {
        Long userId = UserContext.getUserId();
        Group group = super.getById(groupId);
        if (!group.getOwnerId().equals(userId)) {
            throw new GlobalException(ResultCode.CAN_NOT_ALLOW.getCode(), "只有群主才有权限解除群聊");
        }
        List<Long> userIds = groupMemberService.findUserIdsByGroupId(groupId);
        group.setDissolve(true);
        this.updateById(group);
        groupMemberService.removeByGroupId(groupId);
        String key = StrUtil.join(":", RedisKey.IM_GROUP_READED_POSITION, groupId);
        redisTemplate.delete(key);
        String content = String.format("'%s'解散了群聊", UserContext.get().getNickname());
        // TODO
        // 推送同步消息
        log.info("群聊解散,id:{},名称:{}", group.getId(), group.getName());
    }

    @Override
    public List<GroupVO> findGroups(Long version) {
        Long userId = UserContext.getUserId();
        List<GroupMember> groupMembers;
        if (version > 0) {
            LambdaQueryWrapper<GroupMember> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(GroupMember::getUserId, userId).eq(GroupMember::getVersion, version);
            groupMembers = groupMemberService.list(wrapper);
        } else {
            groupMembers = groupMemberService.findByUserId(userId);
            // 60天内退的群可能存在退群前的离线消息，一并返回做前端缓存
            Date minQuitTime = DateUtils.addDays(new Date(), Math.toIntExact(-Constant.MAX_OFFLINE_MESSAGE_DAYS));
            groupMembers.addAll(groupMemberService.findQuitMembers(userId, minQuitTime));
        }
        if (groupMembers.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> ids = groupMembers.stream().map(GroupMember::getGroupId).toList();
        LambdaQueryWrapper<Group> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Group::getId, ids);
        List<Group> groups = this.list(wrapper);
        Map<Long, GroupMember> map = groupMembers.stream().collect(Collectors.toMap(GroupMember::getGroupId, o -> o));
        return groups.stream().map(group -> convert(group, map.get(group.getId()))).toList();
    }

    @Override
    public void invite(GroupInviteDTO dto) {
        Long UserId = UserContext.getUserId();
        Group group = this.getAndCheckById(UserId);
        GroupMember member = groupMemberService.findByGroupAndUserId(dto.getGroupId(), UserId);
        if (Objects.isNull(group) || member.getQuit()) {
            throw new GlobalException(ResultCode.YOU_NOT_IN_GROUP);
        }
        List<GroupMember> members = groupMemberService.findByGroupId(dto.getGroupId(), 0L);
        long size = members.stream().filter(m -> !m.getQuit()).count();
        if (dto.getFriendIds().size() + size > Constant.MAX_GROUP_MEMBER) {
            throw new GlobalException(ResultCode.FILL_MAX_ALLOW.getCode(), "群聊人员不能大于" + Constant.MAX_GROUP_MEMBER);
        }
        List<Friend> friends = friendService.findByFriendIds(dto.getFriendIds());
        if (dto.getFriendIds().size() != friends.size()) {
            throw new GlobalException(ResultCode.HAS_NO_RELATION_WITH_TARGET.getCode(), "部分用户不是您的好友");
        }
        List<GroupMember> groupMembers = friends.stream().map(f -> {
            Optional<GroupMember> optional =
                    members.stream().filter(m -> m.getUserId().equals(f.getFriendId())).findFirst();
            GroupMember groupMember = optional.orElseGet(GroupMember::new);
            groupMember.setGroupId(dto.getGroupId());
            groupMember.setUserId(f.getFriendId());
            groupMember.setUserNickName(f.getFriendNickname());
            groupMember.setHeadImage(f.getFriendHeadImage());
            groupMember.setCreateTime(new Date());
            groupMember.setQuit(false);
            return groupMember;
        }).toList();
        if (!groupMembers.isEmpty()) {
            boolean ok = groupMemberService.saveOrUpdateBatch(group.getId(), groupMembers);
            if (!ok) {
                throw new GlobalException(ResultCode.PROGRAM_ERROR);
            }
        }
        // TODO
        // 推送被邀请人，推送进入群聊消息
        log.info("邀请进入群聊，群聊id:{},群聊名称:{},被邀请用户id:{}", group.getId(), group.getName(),
                dto.getFriendIds());
    }

    @Override
    public List<GroupMemberVO> findGroupMembers(Long groupId, Long version) {
        Group group = this.getById(groupId);
        List<GroupMember> members = groupMemberService.findByGroupId(groupId, version);
        List<Long> userIds = members.stream().map(GroupMember::getUserId).distinct().toList();
        return members.stream().map(m -> {
            GroupMemberVO vo = BeanUtil.copyProperties(m, GroupMemberVO.class);
            vo.setShowGroupName(StrUtil.blankToDefault(m.getRemarkGroupName(), group.getName()));
            return vo;
        }).toList();
    }

    @Override
    public void removeGroupMembers(GroupMemberRemoveDTO dto) {
        Long userId = UserContext.getUserId();
        Group group = this.getAndCheckById(dto.getGroupId());
        if (!group.getOwnerId().equals(userId)) {
            throw new GlobalException(ResultCode.FILL_MAX_ALLOW.getCode(), "您没有权限");
        }
        if (dto.getUserIds().contains(group.getOwnerId())) {
            throw new GlobalException(ResultCode.FILL_MAX_ALLOW.getCode(), "不允许移除群主");
        }
        if (dto.getUserIds().contains(userId)) {
            throw new GlobalException(ResultCode.FILL_MAX_ALLOW.getCode(), "不允许移除自己");
        }
        List<Long> userIds = groupMemberService.findUserIdsByGroupId(dto.getGroupId());
        boolean ok = groupMemberService.removeByGroupAndUserIds(dto.getGroupId(), dto.getUserIds());
        if (!ok) {
            throw new GlobalException(ResultCode.PROGRAM_ERROR);
        }
        String key = StrUtil.join(":", RedisKey.IM_GROUP_READED_POSITION, dto.getGroupId());
        dto.getUserIds().forEach(id -> redisTemplate.opsForHash().delete(key, id.toString()));
        // TODO 推送通知和同步消息
        log.info("踢出群聊，群聊id:{},群聊名称:{},用户id:{}", group.getId(), group.getName(), dto.getUserIds());
    }

    @Override
    public void quitGroup(Long groupId) {
        Long userId = UserContext.getUserId();
        Group group = this.getById(groupId);
        if (group.getOwnerId().equals(userId)) {
            throw new GlobalException(ResultCode.FILL_MAX_ALLOW.getCode(), "您没有权限");
        }
        groupMemberService.removeByGroupAndUserId(groupId);
        String key = StrUtil.join(":", RedisKey.IM_GROUP_READED_POSITION, groupId);
        redisTemplate.opsForHash().delete(key, groupId.toString());
        // TODO 推送信息群聊提示、同步消息
        log.info("退出群聊，群聊id:{},群聊名称:{},用户id:{}", group.getId(), group.getName(), userId);
    }

    @Override
    public void setDnd(GroupDndDTO dto) {
        Long userId = UserContext.getUserId();
        groupMemberService.setDnd(dto.getGroupId(),userId,dto.getIsDnd());
        // TODO 推送同步消息
    }

    private GroupVO convert(Group group, GroupMember member) {
        GroupVO vo = BeanUtil.copyProperties(group, GroupVO.class);
        vo.setRemarkGroupName(member.getRemarkGroupName());
        vo.setRemarkNickName(member.getRemarkNickName());
        vo.setShowNickName(member.getShowNickName());
        vo.setShowGroupName(StrUtil.blankToDefault(member.getRemarkGroupName(), group.getName()));
        vo.setQuit(member.getQuit());
        vo.setIsDnd(member.getIsDnd());
        return vo;
    }
}
