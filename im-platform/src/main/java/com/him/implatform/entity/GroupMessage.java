package com.him.implatform.entity;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Data
@TableName("im_group_message")
public class GroupMessage {
    @TableId
    private Long id;
    private String localId;
    private Long groupId;
    private Long seqNo;
    private Long sendId;
    private String sendNickName;
    private String content;
    /**
     * 被@的用户id列表,库里以逗号分隔存储
     */
    private String atUserIds;
    /**
     * 是否回执消息(需要统计已读人数)
     */
    private Boolean receipt;
    /**
     * 回执是否已完成(群内所有人都已读)
     */
    private Boolean receiptOk;
    private Integer type;
    private Integer status;
    private Date sendTime;

    /**
     * 把@用户id列表拼成库里存储的逗号分隔字符串
     */
    public static String joinUserIds(List<Long> userIds) {
        if (Objects.isNull(userIds) || userIds.isEmpty()) {
            return "";
        }
        return userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(String::valueOf)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    /**
     * 把库里存储的逗号分隔字符串解析成@用户id列表
     */
    public static List<Long> parseUserIds(String atUserIds) {
        if (StrUtil.isBlank(atUserIds)) {
            return List.of();
        }
        return Arrays.stream(atUserIds.split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .map(Long::valueOf)
                .toList();
    }
}
