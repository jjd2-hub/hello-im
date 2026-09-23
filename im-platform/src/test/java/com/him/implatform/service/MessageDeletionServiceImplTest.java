package com.him.implatform.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.him.implatform.entity.MessageDeletion;
import com.him.implatform.enums.ChatType;
import com.him.implatform.mapper.MessageDeletionMapper;
import com.him.implatform.service.impl.MessageDeletionServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
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

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MessageDeletionServiceImpl 单元测试")
class MessageDeletionServiceImplTest {

    @Mock
    private MessageDeletionMapper messageDeletionMapper;

    @InjectMocks
    private MessageDeletionServiceImpl messageDeletionService;

    /**
     * LambdaQueryWrapper 需要 MyBatis-Plus 的 TableInfo 才能把方法引用解析成列名
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MessageDeletion.class);
    }

    @BeforeEach
    void setUp() throws Exception {
        Field baseMapperField = ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(messageDeletionService, messageDeletionMapper);
    }

    private MessageDeletion deletion(Long messageId) {
        MessageDeletion deletion = new MessageDeletion();
        deletion.setMessageId(messageId);
        return deletion;
    }

    // ==================== findDeletedMessageIds ====================

    @Test
    @DisplayName("按会话删除 - 边界及之前算已删除,边界之后不算")
    void findDeletedMessageIds_chatBoundary() {
        // findChatBoundary 走 getOne -> baseMapper.selectOne
        when(messageDeletionMapper.selectOne(any(), anyBoolean())).thenReturn(deletion(100L));
        // 按消息删除的查询无记录
        when(messageDeletionMapper.selectList(any())).thenReturn(List.of());

        Set<Long> deleted = messageDeletionService.findDeletedMessageIds(
                1L, ChatType.PRIVATE, 2L, List.of(50L, 100L, 101L, 200L));

        assertEquals(Set.of(50L, 100L), deleted, "边界100及之前应删除,101/200应保留");
    }

    @Test
    @DisplayName("按消息删除 - 命中删除记录的才算已删除")
    void findDeletedMessageIds_singleMessages() {
        when(messageDeletionMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(messageDeletionMapper.selectList(any())).thenReturn(List.of(deletion(7L)));

        Set<Long> deleted = messageDeletionService.findDeletedMessageIds(
                1L, ChatType.GROUP, 9L, List.of(7L, 8L));

        assertEquals(Set.of(7L), deleted);
    }

    @Test
    @DisplayName("两种删除并存 - 取并集")
    void findDeletedMessageIds_chatAndSingleCombined() {
        when(messageDeletionMapper.selectOne(any(), anyBoolean())).thenReturn(deletion(100L));
        when(messageDeletionMapper.selectList(any())).thenReturn(List.of(deletion(500L)));

        Set<Long> deleted = messageDeletionService.findDeletedMessageIds(
                1L, ChatType.PRIVATE, 2L, List.of(100L, 300L, 500L));

        assertEquals(Set.of(100L, 500L), deleted, "100来自会话边界,500来自单条删除");
    }

    @Test
    @DisplayName("没有任何删除记录时返回空集合")
    void findDeletedMessageIds_noRecord() {
        when(messageDeletionMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(messageDeletionMapper.selectList(any())).thenReturn(List.of());

        Set<Long> deleted = messageDeletionService.findDeletedMessageIds(
                1L, ChatType.PRIVATE, 2L, List.of(1L, 2L));

        assertTrue(deleted.isEmpty());
    }

    @Test
    @DisplayName("消息id为空时直接返回空集合且不查库")
    void findDeletedMessageIds_emptyInput_shouldNotQuery() {
        Set<Long> deleted = messageDeletionService.findDeletedMessageIds(
                1L, ChatType.PRIVATE, 2L, List.of());

        assertTrue(deleted.isEmpty());
        verify(messageDeletionMapper, never()).selectOne(any(), anyBoolean());
        verify(messageDeletionMapper, never()).selectList(any());
    }

    // ==================== deleteChat ====================

    @Test
    @DisplayName("删除会话 - 落一条按会话删除的记录")
    void deleteChat_shouldSaveBoundary() {
        when(messageDeletionMapper.insert(any(MessageDeletion.class))).thenReturn(1);

        messageDeletionService.deleteChat(1L, ChatType.PRIVATE, 2L, 88L);

        ArgumentCaptor<MessageDeletion> captor = ArgumentCaptor.forClass(MessageDeletion.class);
        verify(messageDeletionMapper).insert(captor.capture());
        MessageDeletion saved = captor.getValue();
        assertEquals(1L, saved.getUserId());
        assertEquals(ChatType.PRIVATE.getCode(), saved.getChatType());
        assertEquals(2L, saved.getChatId());
        assertEquals(88L, saved.getMessageId());
        assertEquals(2, saved.getDeleteType(), "deleteType=2 表示按会话删除");
    }

    @Test
    @DisplayName("删除会话 - 没有消息时边界记为0")
    void deleteChat_noMessage_shouldSaveZero() {
        when(messageDeletionMapper.insert(any(MessageDeletion.class))).thenReturn(1);

        messageDeletionService.deleteChat(1L, ChatType.GROUP, 3L, null);

        ArgumentCaptor<MessageDeletion> captor = ArgumentCaptor.forClass(MessageDeletion.class);
        verify(messageDeletionMapper).insert(captor.capture());
        assertEquals(0L, captor.getValue().getMessageId());
    }
}
