# Spring Boot 测试笔记

## 一、测试分层

| 层 | 注解 | 加载范围 | 速度 | 用途 |
|---|---|---|---|---|
| 单元测试 | `@ExtendWith(MockitoExtension.class)` | 无 Spring 容器 | 最快 | 测 Service/工具类内部逻辑 |
| Controller 测试 | `@WebMvcTest(XxxController.class)` | 只加载 Web 层 | 快 | 测接口路由、参数校验、响应格式 |
| 集成测试 | `@SpringBootTest` | 启动完整容器 | 慢 | 测真实数据库/Redis 交互 |

## 二、Mock 三件套

```java
@ExtendWith(MockitoExtension.class)  // 启用 Mockito
class XxxTest {

    @Mock            // 创建假对象（所有方法返回默认值）
    private SomeService someService;

    @InjectMocks     // 创建被测对象，自动把 @Mock 注入进去
    private XxxServiceImpl xxxService;
}
```

## 三、Mock 行为设定

```java
// 有返回值的方法
when(mock.doSomething(any())).thenReturn("结果");

// void 方法
doThrow(new XxxException()).when(mock).doSomething(any());

// 链式调用
when(mock.doA()).thenReturn(a);
when(mock.doB()).thenReturn(b);
```

## 四、断言

```java
assertEquals(期望值, 实际值);        // 相等
assertNotNull(值);                   // 不为 null
assertNull(值);                      // 为 null
assertTrue(条件);                     // 为 true
assertThrows(异常.class, () -> {});  // 抛异常
```

## 五、参数匹配器

```java
any()                    // 任意值
anyString()              // 任意字符串
anyLong()                // 任意 long
eq("固定值")              // 精确匹配
contains("子串")          // 包含
```

## 六、验证调用

```java
verify(mock, times(1)).method();      // 调用了 1 次
verify(mock, never()).method();       // 从未调用
verify(mock, atLeastOnce()).method(); // 至少调用 1 次
```

## 七、Controller 测试 (WebMvcTest)

```java
@WebMvcTest(XxxController.class)
class XxxControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean    // import org.springframework.boot.test.mock.mockito.MockBean;
    private XxxService xxxService;

    @Test
    void test() throws Exception {
        mockMvc.perform(get("/api/xxx")                    // 发请求
                        .param("key", "value")             // 查询参数
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonString))              // 请求体
                .andExpect(status().isOk())                // 断言状态码
                .andExpect(jsonPath("$.code").value(200))  // 断言 JSON
                .andExpect(jsonPath("$.data.name").value("xxx"));
    }
}
```

### MockBean 版本差异
- Spring Boot 3.3.x 及以下：`import org.springframework.boot.test.mock.mockito.MockBean`
- Spring Boot 3.4.x 及以上：`import org.springframework.test.context.bean.override.mockito.MockitoBean`

## 八、常见坑

| 问题 | 原因 | 解决 |
|---|---|---|
| `UnnecessaryStubbingException` | setUp 里 mock 了但某些测试没用到 | 加 `@MockitoSettings(strictness = Strictness.LENIENT)` |
| void 方法用 `when().thenThrow()` 报红 | void 方法不能用 `when()` | 改用 `doThrow().when(mock).method()` |
| `selectOne` mock 不生效 | MyBatisPlus 的 selectOne 有两个重载 | 用 `when(mapper.selectOne(any(), anyBoolean()))` |
| `@MockBean` 爆红 | 包路径写错 | 检查版本，用正确的 import |
| 测试读取真实配置 | 缺少测试配置文件 | 在 `src/test/resources` 放 `application.yml` |

## 九、建议覆盖的用例

### Service 测试（按业务分支）
- 正常流程
- 入参为空/null
- 数据不存在
- 数据已存在（重复）
- 权限/状态异常（封禁、过期）
- 外部依赖失败（DB、MinIO 抛异常）

### Controller 测试（按接口契约）
- 正常请求 → 200 + 正确 JSON
- 参数缺失/格式错 → 触发校验异常
- Service 抛异常 → 全局异常处理器返回正确错误码
