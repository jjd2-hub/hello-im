package com.him.implatform.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.him.implatform.entity.SensitiveWord;
import com.him.implatform.mapper.SensitiveWordMapper;
import com.him.implatform.service.impl.SensitiveWordServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SensitiveWordServiceImpl 单元测试")
class SensitiveWordServiceImplTest {

    @Mock
    private SensitiveWordMapper sensitiveWordMapper;

    @InjectMocks
    private SensitiveWordServiceImpl sensitiveWordService;

    /**
     * LambdaQueryWrapper 需要 MyBatis-Plus 的 TableInfo 才能把方法引用解析成列名
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SensitiveWord.class);
    }

    @BeforeEach
    void setUp() throws Exception {
        Field baseMapperField = ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(sensitiveWordService, sensitiveWordMapper);
    }

    private SensitiveWord word(String content) {
        SensitiveWord sensitiveWord = new SensitiveWord();
        sensitiveWord.setContent(content);
        sensitiveWord.setEnabled(true);
        return sensitiveWord;
    }

    @Test
    @DisplayName("命中敏感词 - 替换为等长星号")
    void filter_shouldMaskMatchedWord() {
        when(sensitiveWordMapper.selectList(any())).thenReturn(List.of(word("赌博")));

        String result = sensitiveWordService.filter("这里在讨论赌博的事情");

        assertEquals("这里在讨论**的事情", result);
    }

    @Test
    @DisplayName("多个敏感词 - 全部替换")
    void filter_shouldMaskAllMatchedWords() {
        when(sensitiveWordMapper.selectList(any())).thenReturn(List.of(word("赌博"), word("暴力")));

        String result = sensitiveWordService.filter("赌博和暴力都不行");

        assertEquals("**和**都不行", result);
    }

    @Test
    @DisplayName("未命中 - 原样返回")
    void filter_noMatch_shouldReturnOriginal() {
        when(sensitiveWordMapper.selectList(any())).thenReturn(List.of(word("赌博")));

        String content = "今天天气不错";
        assertEquals(content, sensitiveWordService.filter(content));
    }

    @Test
    @DisplayName("没有配置敏感词 - 原样返回")
    void filter_noConfiguredWord_shouldReturnOriginal() {
        when(sensitiveWordMapper.selectList(any())).thenReturn(List.of());

        assertEquals("随便什么内容", sensitiveWordService.filter("随便什么内容"));
    }

    @Test
    @DisplayName("空内容 - 原样返回且不查库")
    void filter_emptyContent_shouldReturnOriginal() {
        assertNull(sensitiveWordService.filter(null));
        assertEquals("", sensitiveWordService.filter(""));
    }
}
