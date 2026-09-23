package com.him.implatform.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.him.imcommon.util.ConvUtil;
import com.him.implatform.BaseIntegrationTest;
import com.him.implatform.constant.Constant;
import com.him.implatform.constant.RedisKey;
import com.him.implatform.entity.PrivateMessage;
import com.him.implatform.mapper.PrivateMessageMapper;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpMethod;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 私聊消息 集成测试。
 *
 * <p>这里放的都是"单元测试抓不到"的用例:单元测试里 Redis 和 MySQL 都是假的,
 * 所以键是否对得上、SQL 条件是否正确、唯一约束是否生效、并发下会不会分配出重复序号,
 * 只有真的连上中间件才能验证。
 */
@DisplayName("私聊消息 - 集成测试")
class PrivateMessageApiIT extends BaseIntegrationTest {

    @Autowired
    private PrivateMessageMapper privateMessageMapper;

    // ==================== 端到端主流程 ====================

    @Test
    @DisplayName("发送消息 - seqNo会话内递增且sendId是发送者")
    void sendMessage_shouldAssignSeqNoAndSendId() {
        TestUser[] pair = createFriendPair();
        TestUser alice = pair[0];
        TestUser bob = pair[1];

        JsonNode first = sendPrivate(alice.token(), bob.id(), "it-1", "第一条");
        JsonNode second = sendPrivate(alice.token(), bob.id(), "it-2", "第二条");

        assertEquals(1L, first.path("seqNo").asLong());
        assertEquals(2L, second.path("seqNo").asLong());
        // 曾经这里把 seqNo 写进了 sendId,导致 seq_no 为 null 插不进库
        assertEquals(alice.id(), first.path("sendId").asLong());
        assertEquals(bob.id(), first.path("recvId").asLong());
    }

    @Test
    @DisplayName("已读流程 - 接收方标记已读后,发送方查到status=3")
    void readedFlow_shouldUpdateStatus() {
        TestUser[] pair = createFriendPair();
        TestUser alice = pair[0];
        TestUser bob = pair[1];
        sendPrivate(alice.token(), bob.id(), "it-read-1", "在吗");

        // 接收方把它标记为已读
        callOk(HttpMethod.PUT, "/message/private/readed?friendId=" + alice.id(), bob.token(), null);

        JsonNode history = apiOk(HttpMethod.POST, "/message/private/history", alice.token(),
                Map.of("friendId", bob.id(), "minSeqNo", 0));
        assertEquals(1, history.size());
        assertEquals(3, history.get(0).path("status").asInt(), "状态应为已读(3)");
    }

    // ==================== 单测抓不到的:Redis 键一致性 ====================

    @Test
    @DisplayName("会话key一致 - 落库与缓存必须用同一个conv_key")
    void convKey_shouldBeConsistentBetweenDatabaseAndCache() {
        TestUser[] pair = createFriendPair();
        TestUser alice = pair[0];
        TestUser bob = pair[1];

        sendPrivate(alice.token(), bob.id(), "it-ck-1", "hello");

        String convKey = ConvUtil.buildConvKey(alice.id(), bob.id());

        // 1.库里存的 conv_key 必须能被 ConvUtil 生成的键查到
        List<PrivateMessage> saved = privateMessageMapper.selectList(
                Wrappers.<PrivateMessage>lambdaQuery().eq(PrivateMessage::getConvKey, convKey));
        assertEquals(1, saved.size(), "库里应能用 ConvUtil 生成的 conv_key 查到消息");

        // 2.发送时写入的"会话最新消息id"缓存,也必须用同一个键
        //    (曾经写入用 max-min 横杠、读取用 min_max 下划线,键对不上,缓存永远读不到)
        Object cached = redisTemplate.opsForValue()
                .get(RedisKey.IM_PRIVATE_MESSAGE_MAX_ID + ":" + convKey);
        assertNotNull(cached, "发送后应能用 ConvUtil 生成的 conv_key 读到最新消息id缓存");
    }

    // ==================== 单测抓不到的:并发下 seq_no 分配 ====================

    @Test
    @DisplayName("并发发送 - seq_no必须唯一且从1开始连续")
    void concurrentSend_shouldAssignUniqueAndContinuousSeqNo() throws Exception {
        TestUser[] pair = createFriendPair();
        TestUser alice = pair[0];
        TestUser bob = pair[1];

        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                String localId = "it-conc-" + i;
                results.add(pool.submit(() -> {
                    ready.countDown();
                    // 一起放行,尽量制造真实的并发
                    start.await();
                    return call(HttpMethod.POST, "/message/private/send", alice.token(),
                            Map.of("localId", localId, "recvId", bob.id(), "content", "并发消息", "type", 0))
                            .path("code").asInt();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "线程未就绪");
            start.countDown();
            for (Future<Integer> result : results) {
                assertEquals(200, result.get(60, TimeUnit.SECONDS), "并发发送应全部成功");
            }
        } finally {
            pool.shutdownNow();
        }

        List<PrivateMessage> messages = privateMessageMapper.selectList(
                Wrappers.<PrivateMessage>lambdaQuery()
                        .eq(PrivateMessage::getConvKey, ConvUtil.buildConvKey(alice.id(), bob.id())));
        assertEquals(threads, messages.size());

        List<Long> seqNos = messages.stream().map(PrivateMessage::getSeqNo).sorted().toList();
        assertEquals(threads, new HashSet<>(seqNos).size(), "seq_no 出现重复: " + seqNos);
        for (int i = 0; i < threads; i++) {
            assertEquals((long) (i + 1), seqNos.get(i), "seq_no 应为 1..n 连续,实际: " + seqNos);
        }
    }

    @Test
    @DisplayName("离线消息 - 最近消息占满上限时,要按会话补齐最新一条")
    void loadOfflineMessage_shouldTopUpLastMessageOfOtherConversation() {
        TestUser alice = createUser("it_alice");
        TestUser bob = createUser("it_bob");
        TestUser carol = createUser("it_carol");
        addFriend(alice.token(), bob.id());
        addFriend(alice.token(), carol.id());

        // 1.carol 先发一条,它最旧,不会出现在"最近N条"里
        sendPrivate(carol.token(), alice.id(), "it-carol-1", "carol的消息");

        // 2.bob 再连发 MAX_OFFLINE_MESSAGE_SIZE 条,把这次拉取的上限占满
        int limit = Constant.MAX_OFFLINE_MESSAGE_SIZE.intValue();
        for (int i = 0; i < limit; i++) {
            sendPrivate(bob.token(), alice.id(), "it-bob-" + i, "bob的消息" + i);
        }

        JsonNode data = apiOk(HttpMethod.GET, "/message/private/loadOfflineMessage?minId=0", alice.token(), null);
        List<String> contents = new ArrayList<>();
        data.forEach(node -> contents.add(node.path("content").asText()));

        assertTrue(contents.contains("carol的消息"),
                "占满上限时应通过'会话最新消息id'缓存补上carol那条。" +
                        "这个缓存写入时用 message.getConvKey()、读取时用 buildMaxMessageIdKey()," +
                        "两处键格式只要不一致就会静默漏掉消息。实际返回: " + contents);
    }

    // ==================== 单测抓不到的:SQL 条件正确性 ====================

    @Test
    @DisplayName("离线消息 - 超过保留期的消息不能下发")
    void loadOfflineMessage_shouldExcludeExpiredMessages() {
        TestUser[] pair = createFriendPair();
        TestUser alice = pair[0];
        TestUser bob = pair[1];

        // 直接插一条 61 天前"别人发给我"的消息(超过 MAX_OFFLINE_MESSAGE_DAYS)
        PrivateMessage expired = new PrivateMessage();
        expired.setLocalId("it-old-1");
        expired.setSeqNo(100L);
        expired.setSendId(alice.id());
        expired.setRecvId(bob.id());
        expired.setConvKey(ConvUtil.buildConvKey(alice.id(), bob.id()));
        expired.setContent("很久以前的消息");
        expired.setType(0);
        expired.setStatus(0);
        expired.setSendTime(DateUtils.addDays(new Date(), -Constant.MAX_OFFLINE_MESSAGE_DAYS.intValue() - 1));
        privateMessageMapper.insert(expired);

        // 再发一条新的
        sendPrivate(alice.token(), bob.id(), "it-new-1", "刚发的消息");

        JsonNode data = apiOk(HttpMethod.GET, "/message/private/loadOfflineMessage?minId=0", bob.token(), null);
        List<String> contents = new ArrayList<>();
        data.forEach(node -> contents.add(node.path("content").asText()));

        assertTrue(contents.contains("刚发的消息"), "新消息应下发,实际: " + contents);
        // 若 (send_id=? OR recv_id=?) 没有整体加括号,接收分支会绕过时间过滤把过期消息带出来
        assertFalse(contents.contains("很久以前的消息"), "超过保留期的消息不该下发,实际: " + contents);
    }

    // ==================== 单测抓不到的:唯一约束 ====================

    @Test
    @DisplayName("唯一索引 - 同一发送者重复localId应被拒绝(防重复提交)")
    void duplicateLocalId_shouldBeRejectedByUniqueIndex() {
        TestUser[] pair = createFriendPair();
        TestUser alice = pair[0];
        TestUser bob = pair[1];

        privateMessageMapper.insert(newMessage(alice, bob, "it-dup-local", 1L));

        DuplicateKeyException ex = assertThrows(DuplicateKeyException.class,
                () -> privateMessageMapper.insert(newMessage(alice, bob, "it-dup-local", 2L)));
        assertTrue(ex.getMessage().contains("uk_send_local_id"),
                "应由 uk_send_local_id 拦下,实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("唯一索引 - 同一会话重复seqNo应被拒绝(序号分配的兜底)")
    void duplicateSeqNo_shouldBeRejectedByUniqueIndex() {
        TestUser[] pair = createFriendPair();
        TestUser alice = pair[0];
        TestUser bob = pair[1];

        privateMessageMapper.insert(newMessage(alice, bob, "it-dup-seq-1", 7L));

        DuplicateKeyException ex = assertThrows(DuplicateKeyException.class,
                () -> privateMessageMapper.insert(newMessage(alice, bob, "it-dup-seq-2", 7L)));
        assertTrue(ex.getMessage().contains("uk_conv_key_seq_no"),
                "应由 uk_conv_key_seq_no 拦下,实际: " + ex.getMessage());
    }

    /**
     * 直接构造一条待入库的消息(绕过 service,便于测数据库约束)
     */
    private PrivateMessage newMessage(TestUser from, TestUser to, String localId, Long seqNo) {
        PrivateMessage message = new PrivateMessage();
        message.setLocalId(localId);
        message.setSeqNo(seqNo);
        message.setSendId(from.id());
        message.setRecvId(to.id());
        message.setConvKey(ConvUtil.buildConvKey(from.id(), to.id()));
        message.setContent("约束测试");
        message.setType(0);
        message.setStatus(0);
        message.setSendTime(new Date());
        return message;
    }
}
