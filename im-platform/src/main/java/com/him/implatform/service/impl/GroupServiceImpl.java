package com.him.implatform.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.him.implatform.entity.Group;
import com.him.implatform.mapper.GroupMapper;
import com.him.implatform.service.GroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@CacheConfig()
public class GroupServiceImpl extends ServiceImpl<GroupMapper, Group> implements GroupService {
}
