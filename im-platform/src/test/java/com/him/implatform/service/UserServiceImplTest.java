package com.him.implatform.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.him.implatform.config.props.JwtProperties;
import com.him.implatform.dto.LoginDTO;
import com.him.implatform.dto.RegisterDTO;
import com.him.implatform.entity.User;
import com.him.implatform.enums.ResultCode;
import com.him.implatform.exception.GlobalException;
import com.him.implatform.mapper.UserMapper;
import com.him.implatform.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * UserServiceImpl 单元测试
 * 使用 Mockito 模拟数据库等外部依赖，不需要启动 Spring
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl 单元测试")
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private UserServiceImpl userService;

    /**
     * 通过反射将 mock 的 mapper 注入到 ServiceImpl 的 baseMapper 字段
     * 因为 ServiceImpl 的 getOne()、save() 等方法内部调用 baseMapper
     */
    @BeforeEach
    void setUp() throws Exception {
        Field baseMapperField = ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(userService, userMapper);
    }

    // ==================== register 注册测试 ====================

    @Test
    @DisplayName("注册 - 正常注册成功")
    void register_success() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("newuser");
        dto.setPassword("1234");
        dto.setNickname("新用户");

        // 模拟数据库查不到这个用户
        when(userMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        // 模拟密码加密
        when(passwordEncoder.encode("1234")).thenReturn("$2a$10$encoded");
        // 模拟插入成功，返回插入成功的行数，因为只有一个所以是1
        when(userMapper.insert(any(User.class))).thenReturn(1);

        assertDoesNotThrow(() -> userService.register(dto));

        // 验证调用了insert
        verify(userMapper, times(1)).insert(any(User.class));
    }

    @Test
    @DisplayName("注册 - 用户名已存在应抛异常")
    void register_usernameExists_shouldThrowException() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("existinguser");
        dto.setPassword("1234");
        dto.setNickname("测试");

        // 模拟数据库已有这个用户
        User existingUser = new User();
        existingUser.setUsername("existinguser");
        when(userMapper.selectOne(any(), anyBoolean())).thenReturn(existingUser);

        GlobalException ex = assertThrows(GlobalException.class, () -> userService.register(dto));
        assertEquals(ResultCode.USERNAME_ALREADY_REGISTER.getCode(), ex.getCode());

        // 验证没有调用insert
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    @DisplayName("注册 - 昵称为空时默认用用户名")
    void register_emptyNickname_shouldUseUsername() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("testuser");
        dto.setPassword("1234");
        dto.setNickname("");  // 昵称为空

        when(userMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$encoded");
        when(userMapper.insert(any(User.class))).thenReturn(1);

        userService.register(dto);

        // 验证昵称被设置为用户名
        assertEquals("testuser", dto.getNickname());
    }

    // ==================== login 登录测试 ====================

    @Test
    @DisplayName("登录 - 用户不存在应抛异常")
    void login_userNotFound_shouldThrowException() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("nouser");
        dto.setPassword("1234");

        when(userMapper.selectOne(any(), anyBoolean())).thenReturn(null);

        GlobalException ex = assertThrows(GlobalException.class, () -> userService.login(dto));
        assertEquals(ResultCode.USER_NOT_EXISTS.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("登录 - 密码错误应抛异常")
    void login_wrongPassword_shouldThrowException() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("testuser");
        dto.setPassword("wrongpwd");

        User user = new User();
        user.setUsername("testuser");
        user.setPassword("$2a$10$encoded");
        when(userMapper.selectOne(any(), anyBoolean())).thenReturn(user);
        when(passwordEncoder.matches("wrongpwd", "$2a$10$encoded")).thenReturn(false);

        GlobalException ex = assertThrows(GlobalException.class, () -> userService.login(dto));
        assertEquals(ResultCode.PASSWORD_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("登录 - 用户被封禁应抛异常")
    void login_userBanned_shouldThrowException() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("banneduser");
        dto.setPassword("1234");

        User user = new User();
        user.setUsername("banneduser");
        user.setPassword("$2a$10$encoded");
        user.setIsBanned(true);
        user.setReason("违规操作");
        when(userMapper.selectOne(any(), anyBoolean())).thenReturn(user);
        when(passwordEncoder.matches("1234", "$2a$10$encoded")).thenReturn(true);

        GlobalException ex = assertThrows(GlobalException.class, () -> userService.login(dto));
        assertEquals(ResultCode.USER_BANNED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("登录 - 密码正确应返回token")
    void login_success_shouldReturnToken() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("testuser");
        dto.setPassword("1234");

        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword("$2a$10$encoded");
        user.setIsBanned(false);
        when(userMapper.selectOne(any(), anyBoolean())).thenReturn(user);
        when(passwordEncoder.matches("1234", "$2a$10$encoded")).thenReturn(true);

        // 模拟jwt配置
        when(jwtProperties.getAccessTokenExpireIn()).thenReturn(1800);
        when(jwtProperties.getAccessTokenSecret()).thenReturn("accessSecret");
        when(jwtProperties.getRefreshTokenExpireIn()).thenReturn(604800);
        when(jwtProperties.getRefreshTokenSecret()).thenReturn("refreshSecret");

        var result = userService.login(dto);

        assertNotNull(result.getAccessToken());
        assertNotNull(result.getRefreshToken());
        assertEquals(1800, result.getAccessTokenExpiresIn());
        assertEquals(604800, result.getRefreshTokenExpiresIn());
    }
}
