package com.him.implatform.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.hutool.core.util.StrUtil;
import lombok.Data;
import java.util.Date;

@Data
@TableName("im_group_member")
public class GroupMember {
    @TableId
    private Long id;
    private Long groupId;
    private Long userId;
    private String userNickName;
    private String remarkNickName;
    private String headImage;
    private String remarkGroupName;
    private Boolean isDnd;
    private Boolean quit;
    private Date quitTime;
    private Date createTime;
    private Long version;

    public String getShowNickName() {
        return StrUtil.blankToDefault(remarkNickName, userNickName);
    }
}
