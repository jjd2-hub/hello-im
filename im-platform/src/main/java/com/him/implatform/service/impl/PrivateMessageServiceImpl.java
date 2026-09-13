package com.him.implatform.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.him.implatform.entity.PrivateMessage;
import com.him.implatform.mapper.PrivateMessageMapper;
import com.him.implatform.service.PrivateMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrivateMessageServiceImpl extends ServiceImpl<PrivateMessageMapper, PrivateMessage> implements PrivateMessageService {
}
