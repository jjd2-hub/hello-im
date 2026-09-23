package com.him.implatform.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.him.implatform.entity.SensitiveWord;
import com.him.implatform.mapper.SensitiveWordMapper;
import com.him.implatform.service.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveWordServiceImpl extends ServiceImpl<SensitiveWordMapper, SensitiveWord>
        implements SensitiveWordService {

    /**
     * 敏感词列表本地缓存时长。敏感词变动不频繁,没必要每条消息都查库
     */
    private static final long CACHE_MILLIS = 60_000L;

    private volatile List<String> cachedWords = List.of();

    private volatile long cachedAt = 0L;

    @Override
    public String filter(String content) {
        if (StrUtil.isEmpty(content)) {
            return content;
        }
        List<String> words = getEnabledWords();
        if (words.isEmpty()) {
            return content;
        }
        String result = content;
        for (String word : words) {
            if (result.contains(word)) {
                result = result.replace(word, "*".repeat(word.length()));
            }
        }
        return result;
    }

    /**
     * 取启用中的敏感词,带60秒本地缓存
     */
    private List<String> getEnabledWords() {
        long now = System.currentTimeMillis();
        if (now - cachedAt > CACHE_MILLIS) {
            synchronized (this) {
                if (now - cachedAt > CACHE_MILLIS) {
                    LambdaQueryWrapper<SensitiveWord> wrapper = Wrappers.lambdaQuery();
                    wrapper.eq(SensitiveWord::getEnabled, true).select(SensitiveWord::getContent);
                    cachedWords = this.list(wrapper).stream()
                            .map(SensitiveWord::getContent)
                            .filter(StrUtil::isNotEmpty)
                            .toList();
                    cachedAt = now;
                    log.debug("刷新敏感词缓存,数量:{}", cachedWords.size());
                }
            }
        }
        return cachedWords;
    }
}
