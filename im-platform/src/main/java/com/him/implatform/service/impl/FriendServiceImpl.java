package com.him.implatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.him.imcommon.constant.RedisKey;
import com.him.implatform.context.UserContext;
import com.him.implatform.converter.FriendConverter;
import com.him.implatform.dto.FriendDndDTO;
import com.him.implatform.entity.Friend;
import com.him.implatform.entity.User;
import com.him.implatform.enums.ResultCode;
import com.him.implatform.exception.GlobalException;
import com.him.implatform.mapper.FriendMapper;
import com.him.implatform.mapper.UserMapper;
import com.him.implatform.redis.DistributedCounter;
import com.him.implatform.service.FriendService;
import com.him.implatform.vo.FriendVO;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FriendServiceImpl extends ServiceImpl<FriendMapper, Friend> implements FriendService {

    @Resource
    @Lazy
    private FriendService selfService;

    private final UserMapper userMapper;
    private final DistributedCounter counter;

    @Override
    public List<FriendVO> findFriends(Long version) {
        Long userId= UserContext.getUserId();
        LambdaQueryWrapper<Friend> wrapper= Wrappers.lambdaQuery();
        wrapper.eq(Friend::getUserId,userId);
        // 增量同步处理方式：版本号
        wrapper.gt(version>0,Friend::getVersion,version);
        List<Friend> friends=this.list(wrapper);
        return FriendConverter.toVoList(friends);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void addFriend(Long friendId) {
        Long userId= UserContext.getUserId();
        if(friendId.equals(userId)){
            throw new GlobalException(ResultCode.CAN_NOT_OPERATE_SELF.getCode(),"不能添加自己为好友");
        }
        // 自身代理对象
        selfService.bindFriend(userId,friendId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void bindFriend(Long userId, Long friendId) {
        User user = userMapper.selectById(userId);
        User friendInfo = userMapper.selectById(friendId);
        if (Objects.isNull(user) || Objects.isNull(friendInfo)) {
            throw new GlobalException(ResultCode.USER_NOT_EXISTS);
        }
        // 好友关系必须双向写入:只写单向会导致对方看不到我,也无法回复消息
        Long version = this.getNextVersion();
        saveOrUpdateOneWay(userId, friendId, friendInfo, version);
        saveOrUpdateOneWay(friendId, userId, user, version);
        // TODO 推送好友消息
        // sendAddFriendMessage(userId,friendId,friend);
    }

    /**
     * 幂等写入一条单向好友记录(不存在则新增,存在则复活并刷新昵称头像)
     *
     * @param userId     记录归属人
     * @param friendId   对方用户id
     * @param friendInfo 对方用户信息
     * @param version    本次变更版本号,双向记录共用同一个版本
     */
    private void saveOrUpdateOneWay(Long userId, Long friendId, User friendInfo, Long version) {
        LambdaQueryWrapper<Friend> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(Friend::getUserId, userId).eq(Friend::getFriendId, friendId);
        Friend friend = this.getOne(wrapper);
        if (Objects.isNull(friend)) {
            friend = new Friend();
        }
        friend.setVersion(version);
        friend.setUserId(userId);
        friend.setFriendId(friendId);
        friend.setFriendNickname(friendInfo.getNickname());
        friend.setFriendHeadImage(friendInfo.getHeadImageThumb());
        friend.setDeleted(false);
        this.saveOrUpdate(friend);
    }

    @Override
    public Long getNextVersion() {
        // 计数器缺失时用DB最大值回填;回填与自增必须在同一把锁内完成(实现见 DistributedCounter)
        return counter.next(
                RedisKey.IM_FRIEND_MAX_VERSION,
                RedisKey.IM_LOCK_FRIEND_MAX_VERSION,
                () -> {
                    LambdaQueryWrapper<Friend> wrapper = Wrappers.lambdaQuery();
                    wrapper.orderByDesc(Friend::getVersion).last("limit 1");
                    Friend last = this.getOne(wrapper);
                    return Objects.isNull(last) || Objects.isNull(last.getVersion()) ? 0L : last.getVersion();
                });
    }

    @Override
    public FriendVO findFriend(Long friendId) {
        Long userId= UserContext.getUserId();
        LambdaQueryWrapper<Friend> wrapper= Wrappers.lambdaQuery();
        wrapper.eq(Friend::getUserId,userId).eq(Friend::getFriendId,friendId);
        Friend friend=this.getOne(wrapper);
        if(Objects.isNull(friend)){
            throw new GlobalException(ResultCode.HAS_NO_RELATION_WITH_TARGET.getCode(),"对方不是您好友");
        }
        return FriendConverter.toVo(friend);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delFriend(Long friendId) {
        Long userId= UserContext.getUserId();
        selfService.unbindFriend(userId,friendId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void unbindFriend(Long userId, Long friendId) {
        // 与绑定对称:双向都要标记删除,否则对方列表里仍留着我
        Long version = getNextVersion();
        markDeleted(userId, friendId, version);
        markDeleted(friendId, userId, version);
        // TODO
        // sendDelFriendMessage(userId,friendId);
    }

    /**
     * 软删除一条单向好友记录
     */
    private void markDeleted(Long userId, Long friendId, Long version) {
        LambdaUpdateWrapper<Friend> wrapper = Wrappers.lambdaUpdate();
        wrapper.eq(Friend::getUserId, userId).eq(Friend::getFriendId, friendId);
        wrapper.set(Friend::getDeleted, true).set(Friend::getVersion, version);
        this.update(wrapper);
    }

    @Override
    public void setDnd(FriendDndDTO dto) {
        Long userId= UserContext.getUserId();
        LambdaUpdateWrapper<Friend> wrapper= Wrappers.lambdaUpdate();
        wrapper.eq(Friend::getUserId,userId).eq(Friend::getFriendId,dto.getFriendId());
        wrapper.set(Friend::getIsDnd,dto.getIsDnd()).set(Friend::getVersion,getNextVersion());
        this.update(wrapper);
        // TODO
        // sendSyncDndMessage(dto.getFriendId(),dto.getIsDnd());
    }

    @Override
    public List<Friend> findByFriendIds(List<Long> friendIds) {
        Long userId= UserContext.getUserId();
        LambdaQueryWrapper<Friend> wrapper= Wrappers.lambdaQuery();
        wrapper.eq(Friend::getUserId,userId).in(Friend::getFriendId,friendIds);
        wrapper.eq(Friend::getDeleted,false);
        return this.list(wrapper);
    }

    @Override
    public Boolean isFriend(Long userId, Long recvId) {
        LambdaQueryWrapper<Friend> wrapper= Wrappers.lambdaQuery();
        wrapper.eq(Friend::getUserId,userId).eq(Friend::getFriendId,recvId);
        wrapper.eq(Friend::getDeleted,false);
        return this.exists(wrapper);
    }

    @Override
    public List<Long> findFriendIds() {
        Long userId = UserContext.getUserId();
        LambdaQueryWrapper<Friend> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(Friend::getUserId, userId);
        wrapper.eq(Friend::getDeleted, false);
        wrapper.select(Friend::getFriendId);
        List<Friend> friends = this.list(wrapper);
        return friends.stream().map(Friend::getFriendId).collect(Collectors.toList());
    }

}
