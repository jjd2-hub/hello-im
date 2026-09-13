package com.him.implatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.him.implatform.context.UserContext;
import com.him.implatform.entity.Friend;
import com.him.implatform.enums.ResultCode;
import com.him.implatform.exception.GlobalException;
import com.him.implatform.mapper.FriendMapper;
import com.him.implatform.service.FriendService;
import com.him.implatform.vo.FriendVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class FriendServiceImpl extends ServiceImpl<FriendMapper, Friend> implements FriendService {
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
