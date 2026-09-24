package com.him.implatform.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GroupMessage @用户列表编解码")
class GroupMessageTest {

    @Test
    @DisplayName("joinUserIds - 拼接为逗号分隔并去重")
    void joinUserIds_shouldJoinAndDedup() {
        assertEquals("2,3", GroupMessage.joinUserIds(List.of(2L, 3L, 2L)));
    }

    @Test
    @DisplayName("joinUserIds - 空列表返回空串")
    void joinUserIds_emptyOrNull_shouldReturnEmpty() {
        assertEquals("", GroupMessage.joinUserIds(null));
        assertEquals("", GroupMessage.joinUserIds(List.of()));
    }

    @Test
    @DisplayName("joinUserIds - 过滤null元素")
    void joinUserIds_shouldFilterNull() {
        assertEquals("2", GroupMessage.joinUserIds(Arrays.asList(null, 2L)));
    }

    @Test
    @DisplayName("parseUserIds - 解析回列表")
    void parseUserIds_shouldParse() {
        assertEquals(List.of(2L, 3L), GroupMessage.parseUserIds("2,3"));
    }

    @Test
    @DisplayName("parseUserIds - 空值返回空列表")
    void parseUserIds_blank_shouldReturnEmpty() {
        assertTrue(GroupMessage.parseUserIds(null).isEmpty());
        assertTrue(GroupMessage.parseUserIds("").isEmpty());
        assertTrue(GroupMessage.parseUserIds("  ").isEmpty());
    }

    @Test
    @DisplayName("编解码往返一致")
    void joinThenParse_shouldRoundTrip() {
        List<Long> userIds = List.of(5L, 6L, 7L);
        assertEquals(userIds, GroupMessage.parseUserIds(GroupMessage.joinUserIds(userIds)));
    }
}
