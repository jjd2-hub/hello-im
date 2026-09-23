package com.him.implatform.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.him.implatform.context.UserContext;
import com.him.implatform.dto.GroupMessageHistoryDTO;
import com.him.implatform.entity.GroupMember;
import com.him.implatform.entity.GroupMessage;
import com.him.imcommon.enums.ChatType;
import com.him.implatform.enums.ResultCode;
import com.him.implatform.exception.GlobalException;
import com.him.implatform.mapper.GroupMessageMapper;
import com.him.implatform.service.impl.GroupMessageServiceImpl;
import com.him.implatform.session.UserSession;
import com.him.implatform.vo.GroupMessageVO;
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
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GroupMessageServiceImpl 单元测试")
class GroupMessageServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long GROUP_ID = 7L;

    @Mock
    private GroupMessageMapper groupMessageMapper;

    @Mock
    private GroupService groupService;

    @Mock
    private GroupMemberService groupMemberService;

    @Mock
    private MessageDeletionService messageDeletionService;

    @Mock
    private SensitiveWordService sensitiveWordService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private RedissonClient redissonClient;

    @InjectMocks
    private GroupMessageServiceImpl groupMessageService;

    /**
     * LambdaQueryWrapper 需要 MyBatis-Plus 的 TableInfo 才能把方法引用解析成列名
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, GroupMessage.class);
    }

    @BeforeEach
    void setUp() throws Exception {
        Field baseMapperField = ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(groupMessageService, groupMessageMapper);

        // @InjectMocks 已用构造器注入了final字段,这里再手动补上@Autowired的字段
        Field redisTemplateField = GroupMessageServiceImpl.class.getDeclaredField("redisTemplate");
        redisTemplateField.setAccessible(true);
        redisTemplateField.set(groupMessageService, redisTemplate);

        UserSession session = new UserSession();
        session.setUserId(USER_ID);
        UserContext.set(session);

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(anyString())).thenReturn(Map.of());
    }

    @AfterEach
    void cleanUp() {
        UserContext.remove();
    }

    private GroupMember member(Long userId) {
        GroupMember member = new GroupMember();
        member.setGroupId(GROUP_ID);
        member.setUserId(userId);
        member.setUserNickName("用户" + userId);
        member.setQuit(false);
        return member;
    }

    private GroupMessage message(Long id, Long seqNo, Long sendId) {
        GroupMessage message = new GroupMessage();
        message.setId(id);
        message.setSeqNo(seqNo);
        message.setGroupId(GROUP_ID);
        message.setSendId(sendId);
        message.setSendNickName("用户" + sendId);
        message.setType(0);
        message.setStatus(0);
        message.setContent("内容" + id);
        message.setReceipt(false);
        message.setReceiptOk(false);
        message.setSendTime(new Date());
        return message;
    }

    // ==================== loadHistoryMessage ====================

    @Test
    @DisplayName("查询历史消息 - 非群成员应被拒绝")
    void loadHistoryMessage_notMember_shouldThrow() {
        when(groupMemberService.findByGroupAndUserId(GROUP_ID, USER_ID)).thenReturn(null);

        GroupMessageHistoryDTO dto = new GroupMessageHistoryDTO();
        dto.setGroupId(GROUP_ID);

        GlobalException ex = assertThrows(GlobalException.class,
                () -> groupMessageService.loadHistoryMessage(dto));
        assertEquals(ResultCode.YOU_NOT_IN_GROUP.getCode(), ex.getCode());
        verify(groupMessageMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("查询历史消息 - 已退群成员应被拒绝")
    void loadHistoryMessage_quitMember_shouldThrow() {
        GroupMember quit = member(USER_ID);
        quit.setQuit(true);
        when(groupMemberService.findByGroupAndUserId(GROUP_ID, USER_ID)).thenReturn(quit);

        GroupMessageHistoryDTO dto = new GroupMessageHistoryDTO();
        dto.setGroupId(GROUP_ID);

        assertThrows(GlobalException.class, () -> groupMessageService.loadHistoryMessage(dto));
    }

    @Test
    @DisplayName("查询历史消息 - 按seqNo正序返回,并带上删除标记/已读人数/@列表")
    void loadHistoryMessage_shouldReturnAscWithFlags() {
        when(groupMemberService.findByGroupAndUserId(GROUP_ID, USER_ID)).thenReturn(member(USER_ID));

        GroupMessage m1 = message(1L, 1L, USER_ID);
        m1.setAtUserIds("2,3");
        GroupMessage m2 = message(2L, 2L, 9L);
        // 数据库按seqNo倒序返回,服务内部应重新升序
        when(groupMessageMapper.selectList(any())).thenReturn(List.of(m2, m1));

        when(messageDeletionService.findDeletedMessageIds(eq(USER_ID), eq(ChatType.GROUP), eq(GROUP_ID), any()))
                .thenReturn(Set.of(2L));
        // 成员9已读到消息2
        when(hashOperations.entries(anyString())).thenReturn(Map.of("9", 2L));

        GroupMessageHistoryDTO dto = new GroupMessageHistoryDTO();
        dto.setGroupId(GROUP_ID);

        List<GroupMessageVO> result = groupMessageService.loadHistoryMessage(dto);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getId(), "应按seqNo升序返回");
        assertEquals(2L, result.get(1).getId());

        GroupMessageVO vo1 = result.get(0);
        assertFalse(vo1.getDeleted(), "消息1没被删除");
        assertEquals(List.of(2L, 3L), vo1.getAtUserIds(), "@用户列表应被解析为List");
        // 成员9读到位置2(>=1),加上发送者自己,共2人
        assertEquals(2, vo1.getReadedCount(), "成员9已读 + 发送者本人");

        GroupMessageVO vo2 = result.get(1);
        assertTrue(vo2.getDeleted(), "消息2在删除集合里应标记为已删除");
        // 消息2的发送者就是成员9,已读位置2>=2,不重复计数
        assertEquals(1, vo2.getReadedCount());
        assertTrue(vo2.getAtUserIds().isEmpty());
    }

    @Test
    @DisplayName("设置已读 - 全员已读(含发送者)时回执置为完成")
    void readedMessage_allReaded_shouldCompleteReceipt() {
        when(groupMemberService.findByGroupAndUserId(GROUP_ID, USER_ID)).thenReturn(member(USER_ID));
        when(groupMemberService.findUserIdsByGroupId(GROUP_ID)).thenReturn(List.of(USER_ID, 9L));

        // 群里有一条由9发出、需要回执、尚未完成的消息
        GroupMessage receiptMessage = message(1L, 1L, 9L);
        receiptMessage.setReceipt(true);
        receiptMessage.setReceiptOk(false);
        when(groupMessageMapper.selectList(any())).thenReturn(List.of(receiptMessage));
        when(groupMessageMapper.updateById(any(GroupMessage.class))).thenReturn(1);

        // 当前用户(1)还没读过;成员9已读到1
        when(hashOperations.get(anyString(), any())).thenReturn(null);
        when(hashOperations.entries(anyString())).thenReturn(Map.of("1", 1L, "9", 1L));

        groupMessageService.readedMessage(GROUP_ID, 1L);

        ArgumentCaptor<GroupMessage> captor = ArgumentCaptor.forClass(GroupMessage.class);
        verify(groupMessageMapper).updateById(captor.capture());
        assertTrue(captor.getValue().getReceiptOk(), "2名成员都已读,回执应完成");
    }

    @Test
    @DisplayName("设置已读 - 还有人没读时回执不能完成")
    void readedMessage_notAllReaded_shouldNotCompleteReceipt() {
        when(groupMemberService.findByGroupAndUserId(GROUP_ID, USER_ID)).thenReturn(member(USER_ID));
        when(groupMemberService.findUserIdsByGroupId(GROUP_ID)).thenReturn(List.of(USER_ID, 8L, 9L));

        GroupMessage receiptMessage = message(1L, 1L, 9L);
        receiptMessage.setReceipt(true);
        receiptMessage.setReceiptOk(false);
        when(groupMessageMapper.selectList(any())).thenReturn(List.of(receiptMessage));

        // 只有1和9读过,成员8没读
        when(hashOperations.get(anyString(), any())).thenReturn(null);
        when(hashOperations.entries(anyString())).thenReturn(Map.of("1", 1L, "9", 1L));

        groupMessageService.readedMessage(GROUP_ID, 1L);

        verify(groupMessageMapper, never()).updateById(any(GroupMessage.class));
    }

    @Test
    @DisplayName("查询历史消息 - 查询条件必须带上groupId")
    void loadHistoryMessage_shouldQueryByGroupId() {
        when(groupMemberService.findByGroupAndUserId(GROUP_ID, USER_ID)).thenReturn(member(USER_ID));
        when(groupMessageMapper.selectList(any())).thenReturn(List.of());

        GroupMessageHistoryDTO dto = new GroupMessageHistoryDTO();
        dto.setGroupId(GROUP_ID);

        groupMessageService.loadHistoryMessage(dto);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<GroupMessage>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(groupMessageMapper).selectList(captor.capture());
        LambdaQueryWrapper<GroupMessage> wrapper = captor.getValue();
        // 参数是渲染SQL时才收集的,先取一次SQL片段
        String sql = wrapper.getCustomSqlSegment();
        assertTrue(sql.contains("group_id"), "查询条件里应包含group_id,实际SQL: " + sql);
        assertTrue(wrapper.getParamNameValuePairs().containsValue(GROUP_ID),
                "查询参数里应包含groupId,实际参数: " + wrapper.getParamNameValuePairs());
    }

    // ==================== findReadedUsers ====================

    @Test
    @DisplayName("查询已读用户 - 返回已读位置不小于该消息的成员")
    void findReadedUsers_shouldReturnMembersReaded() {
        when(groupMemberService.findByGroupAndUserId(GROUP_ID, USER_ID)).thenReturn(member(USER_ID));
        when(groupMessageMapper.selectById(5L)).thenReturn(withGroup(message(5L, 5L, USER_ID)));
        when(hashOperations.entries(anyString())).thenReturn(Map.of("1", 9L, "2", 5L, "3", 4L));

        List<Long> readed = groupMessageService.findReadedUsers(GROUP_ID, 5L);

        assertEquals(List.of(1L, 2L), readed, "位置9和5>=5算已读,位置4不算");
    }

    @Test
    @DisplayName("查询已读用户 - 消息不属于该群应报错")
    void findReadedUsers_messageNotInGroup_shouldThrow() {
        when(groupMemberService.findByGroupAndUserId(GROUP_ID, USER_ID)).thenReturn(member(USER_ID));
        GroupMessage other = message(5L, 5L, USER_ID);
        other.setGroupId(999L);
        when(groupMessageMapper.selectById(5L)).thenReturn(other);

        GlobalException ex = assertThrows(GlobalException.class,
                () -> groupMessageService.findReadedUsers(GROUP_ID, 5L));
        assertEquals(ResultCode.HAS_NO_THIS_RESOURCE.getCode(), ex.getCode());
    }

    private GroupMessage withGroup(GroupMessage message) {
        message.setGroupId(GROUP_ID);
        return message;
    }
}
