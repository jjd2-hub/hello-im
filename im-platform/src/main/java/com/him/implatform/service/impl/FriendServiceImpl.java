package com.him.implatform.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.him.implatform.constant.RedisKey;
import com.him.implatform.context.UserContext;
import com.him.implatform.dto.FriendDndDTO;
import com.him.implatform.entity.Friend;
import com.him.implatform.entity.User;
import com.him.implatform.enums.ResultCode;
import com.him.implatform.exception.GlobalException;
import com.him.implatform.mapper.FriendMapper;
import com.him.implatform.mapper.UserMapper;
import com.him.implatform.service.FriendService;
import com.him.implatform.vo.FriendVO;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class FriendServiceImpl extends ServiceImpl<FriendMapper, Friend> implements FriendService {

    @Resource
    @Lazy
    private FriendService selfService;

    private final RedisTemplate<String,Object> redisTemplate;
    private final UserMapper userMapper;

    @Override
    public List<FriendVO> findFriends(Long version) {
        Long userId= UserContext.getUserId();
        LambdaQueryWrapper<Friend> wrapper= Wrappers.lambdaQuery();
        wrapper.eq(Friend::getUserId,userId);
        // 增量同步处理方式：版本号
        wrapper.gt(version>0,Friend::getVersion,version);
        List<Friend> friends=this.list(wrapper);
        return friends.stream().map(this::convert).toList();
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
        LambdaQueryWrapper<Friend> wrapper= Wrappers.lambdaQuery();
        wrapper.eq(Friend::getUserId,userId).eq(Friend::getFriendId,friendId);
        Friend friend=this.getOne(wrapper);
        if(Objects.isNull(friend)){
            friend=new Friend();
        }
        friend.setVersion(this.getNextVersion());
        friend.setUserId(userId);
        friend.setFriendId(friendId);
        User friendInfo=userMapper.selectById(friendId);
        friend.setFriendNickname(friendInfo.getNickname());
        friend.setFriendHeadImage(friendInfo.getHeadImageThumb());
        friend.setDeleted(false);
        this.saveOrUpdate(friend);
        // TODO 推送好友消息
        // sendAddFriendMessage(userId,friendId,friend);
    }

    @Override
    public Long getNextVersion() {
        String key= StrUtil.join(":", RedisKey.IM_FRIEND_MAX_VERSION);
        if(redisTemplate.hasKey(key)){
            return redisTemplate.opsForValue().increment(key);
        }else{
            LambdaQueryWrapper<Friend> wrapper= Wrappers.lambdaQuery();
            wrapper.orderByDesc(Friend::getVersion).last("limit 1");
            Friend friend=this.getOne(wrapper);
            Long version=Objects.isNull(friend)?1:friend.getVersion()+1;
            redisTemplate.opsForValue().set(key,version);
            return version;
        }
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
        return convert(friend);
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
        LambdaUpdateWrapper<Friend> wrapper= Wrappers.lambdaUpdate();
        wrapper.eq(Friend::getUserId,userId).eq(Friend::getFriendId,friendId);
        wrapper.set(Friend::getDeleted,true).set(Friend::getVersion,getNextVersion());
        this.update(wrapper);
        // TODO
        // sendDelFriendMessage(userId,friendId);
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


    private FriendVO convert(Friend f) {
        FriendVO vo=new FriendVO();
        vo.setId(f.getId());
        vo.setHeadImage(f.getFriendHeadImage());
        vo.setNickname(f.getFriendNickname());
        vo.setDeleted(f.getDeleted());
        vo.setIsDnd(f.getIsDnd());
        vo.setVersion(f.getVersion());
        return vo;
    }
}
