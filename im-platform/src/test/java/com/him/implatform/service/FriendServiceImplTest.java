package com.him.implatform.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.him.implatform.context.UserContext;
import com.him.implatform.dto.FriendDndDTO;
import com.him.implatform.entity.Friend;
import com.him.implatform.entity.User;
import com.him.implatform.enums.ResultCode;
import com.him.implatform.exception.GlobalException;
import com.him.implatform.mapper.FriendMapper;
import com.him.implatform.mapper.UserMapper;
import com.him.implatform.service.impl.FriendServiceImpl;
import com.him.implatform.session.UserSession;
import com.him.implatform.vo.FriendVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FriendServiceImpl 单元测试")
class FriendServiceImplTest {

    @Mock
    private FriendMapper friendMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private FriendService selfService;

    @InjectMocks
    private FriendServiceImpl friendService;

    @BeforeEach
    void setUp() throws Exception {
        Field baseMapperField = ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(friendService, friendMapper);

        Field selfServiceField = FriendServiceImpl.class.getDeclaredField("selfService");
        selfServiceField.setAccessible(true);
        selfServiceField.set(friendService, selfService);

        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void cleanUp() {
        UserContext.remove();
    }

    private void setUserContext(Long userId) {
        UserSession session = new UserSession();
        session.setUserId(userId);
        UserContext.set(session);
    }

    private void mockSelectOne(Friend result) {
        when(friendMapper.selectOne(any(), anyBoolean())).thenReturn(result);
    }

    // ==================== findFriends ====================

    @Test
    @DisplayName("查询好友列表 - 返回好友列表")
    void findFriends_shouldReturnList() {
        setUserContext(1L);

        Friend friend = new Friend();
        friend.setId(1L);
        friend.setUserId(1L);
        friend.setFriendId(2L);
        friend.setFriendNickname("张三");
        friend.setFriendHeadImage("img.jpg");
        friend.setIsDnd(false);
        friend.setDeleted(false);
        friend.setVersion(1L);

        when(friendMapper.selectList(any())).thenReturn(List.of(friend));

        List<FriendVO> result = friendService.findFriends(0L);

        assertEquals(1, result.size());
        assertEquals("张三", result.get(0).getNickname());
        // 返回给前端的好友id必须是对方的userId,而不是im_friend表主键
        assertEquals(2L, result.get(0).getId());
    }

    @Test
    @DisplayName("查询好友列表 - 版本号过滤")
    void findFriends_withVersion_shouldFilter() {
        setUserContext(1L);
        when(friendMapper.selectList(any())).thenReturn(List.of());

        List<FriendVO> result = friendService.findFriends(5L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== addFriend ====================

    @Test
    @DisplayName("添加好友 - 不能添加自己")
    void addFriend_addSelf_shouldThrow() {
        setUserContext(1L);

        GlobalException ex = assertThrows(GlobalException.class, () -> friendService.addFriend(1L));
        assertEquals(ResultCode.CAN_NOT_OPERATE_SELF.getCode(), ex.getCode());
        verify(selfService, never()).bindFriend(anyLong(), anyLong());
    }

    @Test
    @DisplayName("添加好友 - 正常添加")
    void addFriend_success() {
        setUserContext(1L);

        friendService.addFriend(2L);

        verify(selfService, times(1)).bindFriend(1L, 2L);
    }

    // ==================== findFriend ====================

    @Test
    @DisplayName("查找好友 - 好友存在")
    void findFriend_exists_shouldReturnVO() {
        setUserContext(1L);

        Friend friend = new Friend();
        friend.setId(1L);
        friend.setUserId(1L);
        friend.setFriendId(2L);
        friend.setFriendNickname("张三");
        friend.setFriendHeadImage("img.jpg");
        friend.setIsDnd(false);
        friend.setDeleted(false);
        friend.setVersion(1L);

        when(friendMapper.selectOne(any(), anyBoolean())).thenReturn(friend);

        FriendVO result = friendService.findFriend(2L);

        assertNotNull(result);
        assertEquals("张三", result.getNickname());
    }

    @Test
    @DisplayName("查找好友 - 好友不存在应抛异常")
    void findFriend_notExists_shouldThrow() {
        setUserContext(1L);
        when(friendMapper.selectOne(any(), anyBoolean())).thenReturn(null);

        GlobalException ex = assertThrows(GlobalException.class, () -> friendService.findFriend(999L));
        assertEquals(ResultCode.HAS_NO_RELATION_WITH_TARGET.getCode(), ex.getCode());
    }

    // ==================== delFriend ====================

    @Test
    @DisplayName("删除好友 - 调用解绑")
    void delFriend_shouldCallUnbind() {
        setUserContext(1L);

        friendService.delFriend(2L);

        verify(selfService, times(1)).unbindFriend(1L, 2L);
    }

}
