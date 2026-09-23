package com.him.implatform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.him.implatform.entity.SensitiveWord;

/**
 * 敏感词处理
 */
public interface SensitiveWordService extends IService<SensitiveWord> {

    /**
     * 把内容中命中的敏感词替换成等长的星号。
     * 注意:只应用于纯文本内容,不要用于图片/文件等 JSON 内容,否则可能破坏 JSON 结构。
     *
     * @param content 原始内容
     * @return 替换后的内容
     */
    String filter(String content);
}
