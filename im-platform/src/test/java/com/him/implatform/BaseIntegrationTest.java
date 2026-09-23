package com.him.implatform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 集成测试基类:真 MySQL(hello_im_test) + 真 Redis(db 15) + 真 MinIO(box-im-test) + 完整 Spring 容器。
 *
 * <p>它负责两件最容易写错的事:
 * <ol>
 *   <li>环境隔离——靠 application-test.yml 把库/Redis db/bucket 全部与开发环境错开;</li>
 *   <li>用例间隔离——每个用例前清库 + 清 Redis,用例之间不互相影响。</li>
 * </ol>
 *
 * <p>为什么不用 @Transactional 自动回滚:回滚会让唯一约束、并发冲突这类"数据库层面的行为"永远不触发,
 * 测试会假绿。所以这里用显式清库,代价是稍慢一点,但断言是可信的。
 *
 * <p>注意本项目所有接口都返回 HTTP 200,业务结果在响应体的 code 字段里,
 * 因此断言必须打在 body 上(见 {@link #apiOk} / {@link #apiError})。
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {

    protected static final String TEST_PASSWORD = "pass1234";

    /**
     * 会写数据的业务表,每个用例前清空。
     * 顺序上先清"子表"再清"主表",虽然没有外键约束,但保持这个习惯没坏处。
     */
    private static final List<String> BUSINESS_TABLES = List.of(
            "im_private_message",
            "im_group_message",
            "im_message_deletion",
            "im_group_member",
            "im_group",
            "im_friend",
            "im_sensitive_word",
            "im_file_info",
            "im_user");

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void resetEnvironment() {
        // TRUNCATE 会重置自增主键,让每个用例的 id 都从 1 开始,断言更好写
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        BUSINESS_TABLES.forEach(table -> jdbcTemplate.execute("TRUNCATE TABLE " + table));
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        // 测试跑在独立的 Redis db 上,flushDb 不会碰到开发数据
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    // ==================== 通用请求 ====================

    /**
     * 发一个请求并返回完整响应体(HTTP 层必须成功,业务成功与否由调用方断言)
     */
    protected JsonNode call(HttpMethod method, String url, String accessToken, Object body) {
        try {
            MockHttpServletRequestBuilder builder = request(method, url);
            if (accessToken != null) {
                builder.header("accessToken", accessToken);
            }
            if (body != null) {
                builder.contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body));
            }
            MvcResult result = mockMvc.perform(builder)
                    .andExpect(status().isOk())
                    .andReturn();
            String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("请求失败: " + method + " " + url, e);
        }
    }

    /**
     * 断言业务成功(code=200)并返回 data 节点
     */
    protected JsonNode apiOk(HttpMethod method, String url, String accessToken, Object body) {
        JsonNode root = call(method, url, accessToken, body);
        assertEquals(200, root.path("code").asInt(), "接口未返回成功,响应: " + root);
        return root.path("data");
    }

    /**
     * 断言业务返回指定错误码
     */
    protected JsonNode apiError(HttpMethod method, String url, String accessToken, Object body, int expectedCode) {
        JsonNode root = call(method, url, accessToken, body);
        assertEquals(expectedCode, root.path("code").asInt(), "错误码不符,响应: " + root);
        return root;
    }

    /**
     * 不关心返回值,只要求成功
     */
    protected void callOk(HttpMethod method, String url, String accessToken, Object body) {
        apiOk(method, url, accessToken, body);
    }

    // ==================== 账号与会话 ====================

    /**
     * 注册并登录一个用户,返回它的 id 和 accessToken
     */
    protected TestUser createUser(String username) {
        callOk(HttpMethod.POST, "/auth/register", null,
                Map.of("username", username, "password", TEST_PASSWORD, "nickname", username));
        String token = login(username);
        JsonNode self = apiOk(HttpMethod.GET, "/user/self", token, null);
        return new TestUser(self.path("id").asLong(), token);
    }

    protected String login(String username) {
        JsonNode root = call(HttpMethod.POST, "/auth/login", null,
                Map.of("username", username, "password", TEST_PASSWORD));
        assertEquals(200, root.path("code").asInt(), "登录失败,响应: " + root);
        return root.path("data").path("accessToken").asText();
    }

    // ==================== 好友与消息 ====================

    protected void addFriend(String accessToken, Long friendId) {
        callOk(HttpMethod.POST, "/friend/add?friendId=" + friendId, accessToken, null);
    }

    /**
     * 建立一对互为好友的用户,返回 [甲, 乙]
     */
    protected TestUser[] createFriendPair() {
        TestUser alice = createUser("it_alice");
        TestUser bob = createUser("it_bob");
        addFriend(alice.token(), bob.id());
        return new TestUser[]{alice, bob};
    }

    protected JsonNode sendPrivate(String accessToken, Long recvId, String localId, String content) {
        return apiOk(HttpMethod.POST, "/message/private/send", accessToken,
                Map.of("localId", localId, "recvId", recvId, "content", content, "type", 0));
    }

    protected JsonNode createGroup(String accessToken, String name) {
        return apiOk(HttpMethod.POST, "/group/create", accessToken, Map.of("name", name));
    }

    protected JsonNode sendGroup(String accessToken, Long groupId, String localId, String content) {
        return apiOk(HttpMethod.POST, "/message/group/send", accessToken,
                Map.of("localId", localId, "groupId", groupId, "content", content, "type", 0));
    }

    /**
     * 测试用户:id + 访问令牌
     */
    protected record TestUser(Long id, String token) {
    }
}
