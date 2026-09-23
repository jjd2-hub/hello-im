package com.him.implatform.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.him.imcommon.util.ConvUtil;
import com.him.implatform.context.UserContext;
import com.him.implatform.dto.PrivateMessageHistoryDTO;
import com.him.implatform.entity.PrivateMessage;
import com.him.imcommon.enums.ChatType;
import com.him.implatform.mapper.PrivateMessageMapper;
import com.him.implatform.service.impl.PrivateMessageServiceImpl;
import com.him.implatform.session.UserSession;
import com.him.implatform.vo.PrivateMessageVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PrivateMessageServiceImpl 单元测试")
class PrivateMessageServiceImplTest {

    private static final Long ME = 1L;

    @Mock
    private PrivateMessageMapper privateMessageMapper;

    @Mock
    private FriendService friendService;

    @Mock
    private MessageDeletionService messageDeletionService;

    @Mock
    private SensitiveWordService sensitiveWordService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private RedissonClient redissonClient;

    @InjectMocks
    private PrivateMessageServiceImpl privateMessageService;

    /**
     * LambdaQueryWrapper 需要 MyBatis-Plus 的 TableInfo 才能把方法引用解析成列名,
     * 纯单测环境没有启动 Spring,这里手动初始化一次
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PrivateMessage.class);
    }

    @BeforeEach
    void setUp() throws Exception {
        Field baseMapperField = ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(privateMessageService, privateMessageMapper);

        UserSession session = new UserSession();
        session.setUserId(ME);
        session.setNickname("我");
        UserContext.set(session);
    }

    @AfterEach
    void cleanUp() {
        UserContext.remove();
    }

    private PrivateMessage message(Long id, Long seqNo, Long sendId, Long recvId) {
        PrivateMessage message = new PrivateMessage();
        message.setId(id);
        message.setSeqNo(seqNo);
        message.setSendId(sendId);
        message.setRecvId(recvId);
        message.setConvKey(ConvUtil.buildConvKey(sendId, recvId));
        message.setContent("内容" + id);
        message.setType(0);
        message.setStatus(0);
        message.setSendTime(new Date());
        return message;
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<PrivateMessage> captureQuery() {
        ArgumentCaptor<LambdaQueryWrapper<PrivateMessage>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(privateMessageMapper).selectList(captor.capture());
        return captor.getValue();
    }

    // ==================== loadOfflineMessage ====================

    @Test
    @DisplayName("拉取离线消息 - 返回VO而不是null,并按id正序")
    void loadOfflineMessage_shouldReturnVoListAsc() {
        PrivateMessage m1 = message(1L, 1L, ME, 2L);
        PrivateMessage m2 = message(2L, 2L, 3L, ME);
        // 库里按id倒序返回
        when(privateMessageMapper.selectList(any())).thenReturn(List.of(m2, m1));
        when(messageDeletionService.findDeletedMessageIds(eq(ME), eq(ChatType.PRIVATE), any(), any()))
                .thenReturn(Set.of());

        List<PrivateMessageVO> result = privateMessageService.loadOfflineMessage(0L);

        assertNotNull(result, "旧实现这里返回null");
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getId(), "应按id升序");
        assertEquals(2L, result.get(1).getId());
    }

    @Test
    @DisplayName("拉取离线消息 - 按会话维度分别标记已删除")
    void loadOfflineMessage_shouldMarkDeletedPerConversation() {
        PrivateMessage m1 = message(1L, 1L, ME, 2L);   // 会话好友=2
        PrivateMessage m2 = message(2L, 2L, 3L, ME);   // 会话好友=3
        when(privateMessageMapper.selectList(any())).thenReturn(List.of(m1, m2));
        when(messageDeletionService.findDeletedMessageIds(eq(ME), eq(ChatType.PRIVATE), eq(2L), any()))
                .thenReturn(Set.of(1L));
        when(messageDeletionService.findDeletedMessageIds(eq(ME), eq(ChatType.PRIVATE), eq(3L), any()))
                .thenReturn(Set.of());

        List<PrivateMessageVO> result = privateMessageService.loadOfflineMessage(0L);

        assertTrue(result.get(0).getDeleted(), "会话2里的消息1已删除");
        assertFalse(result.get(1).getDeleted(), "会话3里的消息2未删除");
    }

    @Test
    @DisplayName("拉取离线消息 - 收发条件必须整体加括号,不能被OR绕过")
    void loadOfflineMessage_sendRecvConditionMustBeGrouped() {
        when(privateMessageMapper.selectList(any())).thenReturn(List.of());
        when(messageDeletionService.findDeletedMessageIds(anyLong(), any(), anyLong(), any()))
                .thenReturn(Set.of());

        privateMessageService.loadOfflineMessage(0L);

        String sql = captureQuery().getCustomSqlSegment();
        // 期望:(send_id = ? OR recv_id = ?) 作为一个整体;
        // 若写成 AND (send_id = ?) OR recv_id = ?,recv分支会绕过id与时间过滤
        assertTrue(sql.matches("(?s).*\\(send_id = [^()]*OR recv_id = [^()]*\\).*"),
                "send_id 与 recv_id 必须在同一个括号内,实际SQL: " + sql);
        assertTrue(sql.contains("send_id") && sql.contains("recv_id"), "实际SQL: " + sql);
    }

    // ==================== loadHistoryMessage ====================

    @Test
    @DisplayName("查询历史消息 - 会话key必须由ConvUtil生成")
    void loadHistoryMessage_shouldUseConvUtilConvKey() {
        when(privateMessageMapper.selectList(any())).thenReturn(List.of());
        when(messageDeletionService.findDeletedMessageIds(anyLong(), any(), anyLong(), any()))
                .thenReturn(Set.of());

        PrivateMessageHistoryDTO dto = new PrivateMessageHistoryDTO();
        dto.setFriendId(200L);
        privateMessageService.loadHistoryMessage(dto);

        String expectedConvKey = ConvUtil.buildConvKey(ME, 200L);
        LambdaQueryWrapper<PrivateMessage> wrapper = captureQuery();
        // 参数是渲染SQL时才收集的,先取一次SQL片段
        String sql = wrapper.getCustomSqlSegment();
        assertTrue(sql.contains("conv_key"), "查询条件里应包含conv_key,实际SQL: " + sql);
        assertTrue(wrapper.getParamNameValuePairs().containsValue(expectedConvKey),
                "查询条件应使用ConvUtil生成的conv_key: " + expectedConvKey
                        + ",实际参数: " + wrapper.getParamNameValuePairs());
    }

    @Test
    @DisplayName("查询历史消息 - 按seqNo正序返回并带删除标记")
    void loadHistoryMessage_shouldReturnAscWithDeletedFlag() {
        PrivateMessage m1 = message(10L, 1L, ME, 2L);
        PrivateMessage m2 = message(11L, 2L, 2L, ME);
        when(privateMessageMapper.selectList(any())).thenReturn(List.of(m2, m1));
        when(messageDeletionService.findDeletedMessageIds(eq(ME), eq(ChatType.PRIVATE), eq(2L), any()))
                .thenReturn(Set.of(10L));

        PrivateMessageHistoryDTO dto = new PrivateMessageHistoryDTO();
        dto.setFriendId(2L);

        List<PrivateMessageVO> result = privateMessageService.loadHistoryMessage(dto);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getSeqNo(), "应按seqNo升序");
        assertEquals(2L, result.get(1).getSeqNo());
        assertTrue(result.get(0).getDeleted());
        assertFalse(result.get(1).getDeleted());
    }
}
