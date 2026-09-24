package com.him.implatform.validator;

import com.alibaba.fastjson.JSON;
import com.him.implatform.constant.Constant;
import com.him.implatform.dto.MessageContent;
import com.him.implatform.enums.MessageType;
import com.him.implatform.enums.ResultCode;
import com.him.implatform.exception.GlobalException;
import com.him.implatform.service.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 消息校验与内容处理策略。
 *
 * <p>为什么要单独成包:这套规则原本在 {@code PrivateMessageServiceImpl} 和
 * {@code GroupMessageServiceImpl} 里各抄了一份(逐字相同),加一条新规则要改两处,
 * 漏一处就会出现"私聊拦了、群聊没拦"这类不一致。
 *
 * <p>目前承担两件事:
 * <ol>
 *   <li><b>纯校验</b>({@link #validate}):文字消息限长、非文字消息必须是合法 JSON。无外部依赖。</li>
 *   <li><b>内容处理策略</b>({@link #filterSensitive}):决定"哪些类型的消息需要做敏感词过滤"。</li>
 * </ol>
 *
 * <p>关于依赖:第 2 项依赖了 {@code SensitiveWordService}(它是领域服务)。
 * 这样做的代价是 validator 不是零依赖,收益是"过滤策略"只存在一处 ——
 * 之前"只过滤文字消息"这个判断散在两个 Service 里,很容易被改歪。
 * 如果将来想让 validator 完全无依赖,可以把第 2 项拆成一个纯函数,
 * 由调用方传入过滤实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageValidator {

    private final SensitiveWordService sensitiveWordService;

    /**
     * 校验待发消息的内容。不合法直接抛业务异常。
     *
     * @param message 待发消息
     */
    public void validate(MessageContent message) {
        String content = message.getContent();
        if (content == null) {
            throw new GlobalException(ResultCode.FORMAT_FAILED.getCode(), "发送内容不可为空");
        }
        if (isText(message.getType())) {
            if (content.length() > Constant.MAX_MESSAGE_LENGTH) {
                throw new GlobalException(ResultCode.FILL_MAX_ALLOW.getCode(),
                        String.format("消息长度不能大于%s个字符", Constant.MAX_MESSAGE_LENGTH));
            }
            return;
        }
        // 非文字消息:内容必须是合法 JSON,否则前端渲染时会报错
        try {
            JSON.parse(content);
        } catch (Exception e) {
            throw new GlobalException(ResultCode.FORMAT_FAILED);
        }
    }

    /**
     * 按内容类型决定是否做敏感词过滤。
     *
     * <p><b>只过滤纯文字消息</b>:图片/文件等类型的 content 是 JSON,
     * 整体做字符串替换会把 JSON 结构破坏掉(比如 URL 里含敏感词)。
     *
     * @param type    消息类型
     * @param content 原始内容
     * @return 过滤后的内容(不需要过滤时原样返回)
     */
    public String filterSensitive(Integer type, String content) {
        if (!isText(type) || content == null) {
            return content;
        }
        return sensitiveWordService.filter(content);
    }

    /**
     * 是否是需要做敏感词过滤/长度限制的纯文字消息
     */
    public boolean isText(Integer type) {
        return MessageType.TEXT.getCode().equals(type);
    }
}
