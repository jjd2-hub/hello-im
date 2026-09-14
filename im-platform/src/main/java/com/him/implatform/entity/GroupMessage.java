package com.him.implatform.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

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
    private Integer type;
    private Integer status;
    private Date sendTime;
}
