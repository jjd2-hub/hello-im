package com.him.implatform.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.him.implatform.entity.GroupMessage;
import com.him.implatform.mapper.GroupMessageMapper;
import com.him.implatform.service.GroupMessageService;
import org.springframework.stereotype.Service;

@Service
public class GroupMessageServiceImpl extends ServiceImpl<GroupMessageMapper, GroupMessage> implements GroupMessageService {
}
