-- ============================================================
-- 升级脚本 01：唯一约束 + 查询索引
-- 说明：create.sql 只对新建库生效，已存在的库需要手动执行本脚本。
-- 执行顺序：先跑「第 1 步 自检」，确认无重复数据后再跑「第 2 步 加索引」。
-- 有外键引用的库请自行确认索引替换不会影响外键。
-- ============================================================

-- ---------- 第 1 步：重复数据自检（必须全部为空结果） ----------
-- 下面任何一条查出数据，都需要先人工清理，否则唯一索引会创建失败。

-- 群成员：同一人不应在同一群出现两行（旧代码 saveOrUpdateBatch 并发下可能产生）
SELECT group_id, user_id, COUNT(*) AS c
FROM im_group_member
GROUP BY group_id, user_id
HAVING c > 1;

-- 私聊消息：同一会话内序号应唯一（旧代码 seq_no 分配有竞态）
SELECT conv_key, seq_no, COUNT(*) AS c
FROM im_private_message
GROUP BY conv_key, seq_no
HAVING c > 1;

-- 客户端幂等键：同一发送者的 local_id 应唯一
SELECT send_id, local_id, COUNT(*) AS c
FROM im_private_message
WHERE local_id IS NOT NULL
GROUP BY send_id, local_id
HAVING c > 1;

-- 群消息：同一群内序号应唯一
SELECT group_id, seq_no, COUNT(*) AS c
FROM im_group_message
GROUP BY group_id, seq_no
HAVING c > 1;

-- 敏感词：内容应唯一
SELECT content, COUNT(*) AS c
FROM im_sensitive_word
GROUP BY content
HAVING c > 1;


-- ---------- 第 2 步：加索引 ----------

-- 好友：增量同步按 (user_id, version) 过滤
ALTER TABLE `im_friend` ADD KEY `idx_user_version` (`user_id`, `version`);

-- 私聊消息：会话序号唯一（并发下重复序号会直接报错，而不是静默写坏数据）
ALTER TABLE `im_private_message` DROP INDEX `idx_conv_key_seq_no`;
ALTER TABLE `im_private_message` ADD UNIQUE KEY `uk_conv_key_seq_no` (`conv_key`, `seq_no`);
-- 发送幂等：同一次发送重试不会落两条
ALTER TABLE `im_private_message` ADD UNIQUE KEY `uk_send_local_id` (`send_id`, `local_id`);
-- 离线拉取按 recv_id + id 走索引
ALTER TABLE `im_private_message` DROP INDEX `idx_recv_id`;
ALTER TABLE `im_private_message` ADD KEY `idx_recv_id` (`recv_id`, `id`);

-- 群消息：群内序号唯一
ALTER TABLE `im_group_message` DROP INDEX `idx_group_id_seq_no`;
ALTER TABLE `im_group_message` ADD UNIQUE KEY `uk_group_id_seq_no` (`group_id`, `seq_no`);

-- 群成员：一人一群仅一行；两个版本号增量同步走覆盖索引
ALTER TABLE `im_group_member` DROP INDEX `idx_group_id`;
ALTER TABLE `im_group_member` DROP INDEX `idx_user_id`;
ALTER TABLE `im_group_member` ADD UNIQUE KEY `uk_group_id_user_id` (`group_id`, `user_id`);
ALTER TABLE `im_group_member` ADD KEY `idx_group_id_version` (`group_id`, `version`);
ALTER TABLE `im_group_member` ADD KEY `idx_user_id_version` (`user_id`, `version`);

-- 消息删除记录：按会话维度查询
ALTER TABLE `im_message_deletion` DROP INDEX `idx_user_id`;
ALTER TABLE `im_message_deletion` ADD KEY `idx_user_chat` (`user_id`, `chat_type`, `chat_id`);

-- 敏感词：内容唯一
ALTER TABLE `im_sensitive_word` ADD UNIQUE KEY `uk_content` (`content`);
