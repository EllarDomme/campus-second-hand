-- ============================================================
-- 校园二手物品交易平台 · 建表脚本
-- 版本：v1（在豆包版基础上，按需求逐条复核后修正）
--
-- 与豆包版的差异（详见对话）：
--   1. 保留 employee 独立表（管理员），user.role 只保留 0普通 / 1跑腿员
--   2. product 补：新旧程度、审核状态（捡漏商品需人工审核）
--   3. trade_order 补：交易地点（自提）、取消原因、买家备注
--   4. 新增 conversation 会话表（私信权限需要"聊的是哪件商品"这个上下文）
--   5. 新增 credit_log 信用流水表（"每日加分上限"必须有流水才算得出来）
--   6. 补 3 个唯一约束（评价 / 抢购记录 / 跑腿单），防重复数据
--   7. 补关键索引（私信未读、捡漏专区列表、订单超时扫描、议价过期扫描）
--   8. 投诉：补凭证图片 + 细分投诉原因
--
-- 规范：InnoDB / utf8mb4 / utf8mb4_unicode_ci / 无物理外键 / 每表 id+create_time+update_time
-- ============================================================

CREATE DATABASE IF NOT EXISTS campus_second_hand
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE campus_second_hand;

-- ------------------------------------------------------------
-- 1. employee 管理员表
-- 保留独立表的原因：管理员用账号密码登录，学生用微信 openid 自动注册。
-- 合表会导致 openid/password 都必须可为 NULL，且微信自动注册带来提权风险。
-- 字段与原工程完全一致，保证管理端代码零改动。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `employee`;
CREATE TABLE `employee` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) NOT NULL COMMENT '姓名',
  `username` varchar(32) NOT NULL COMMENT '用户名',
  `password` varchar(64) NOT NULL COMMENT '密码（MD5）',
  `phone` varchar(11) NOT NULL COMMENT '手机号',
  `sex` varchar(2) NOT NULL COMMENT '性别',
  `id_number` varchar(18) NOT NULL COMMENT '身份证号',
  `status` int NOT NULL DEFAULT 1 COMMENT '状态：0禁用，1启用',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员信息';

-- ------------------------------------------------------------
-- 2. user 学生用户表
-- role 只有两种：跑腿员是学生的"附加角色"，管理员不属于此表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `openid` varchar(50) NOT NULL COMMENT '微信openid（登录唯一标识）',
  `nickname` varchar(55) DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像地址',
  `real_name` varchar(55) DEFAULT NULL COMMENT '真实姓名',
  `phone` varchar(11) DEFAULT NULL COMMENT '手机号',
  `student_no` varchar(20) DEFAULT NULL COMMENT '学号（实名认证）',
  `student_card_img` varchar(255) DEFAULT NULL COMMENT '学生证照片url（实名认证凭证）',
  `credit_score` int NOT NULL DEFAULT 100 COMMENT '信用分，默认100',
  `role` tinyint NOT NULL DEFAULT 0 COMMENT '角色：0普通用户，1跑腿员',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0正常，1封禁',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openid` (`openid`),
  KEY `idx_role` (`role`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生用户表';

-- ------------------------------------------------------------
-- 3. category 商品分类表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `category_name` varchar(55) NOT NULL COMMENT '分类名称',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类表';

-- ------------------------------------------------------------
-- 4. product 商品表（普通闲置 + 限时捡漏共用，用 is_flash_sale 区分）
-- 注意两个状态的职责分离：
--   audit_status = 审核状态（捡漏商品需人工审核）
--   status       = 售卖状态（在售/交易中/售罄/下架）
-- 捡漏专区列表必须同时过滤 audit_status=1，否则待审核商品会被用户看到
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `seller_id` bigint NOT NULL COMMENT '卖家用户id',
  `category_id` bigint NOT NULL COMMENT '分类id',
  `title` varchar(100) NOT NULL COMMENT '商品标题',
  `price` decimal(10,2) NOT NULL COMMENT '商品标价',
  `images` varchar(1000) DEFAULT NULL COMMENT '图片url，JSON数组',
  `description` text COMMENT '商品描述：瑕疵、配件等细节',
  `condition_level` tinyint NOT NULL DEFAULT 3 COMMENT '新旧程度：1全新 2几乎全新 3轻微使用痕迹 4明显使用痕迹',
  `is_flash_sale` tinyint NOT NULL DEFAULT 0 COMMENT '是否限时捡漏：0普通 1捡漏',
  `flash_start_time` datetime DEFAULT NULL COMMENT '捡漏抢购开始时间，普通商品为NULL',
  `audit_status` tinyint NOT NULL DEFAULT 1 COMMENT '审核状态：0待审核 1已通过 2已驳回（普通商品敏感词自动通过=1）',
  `audit_note` varchar(255) DEFAULT NULL COMMENT '审核意见/驳回原因',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '售卖状态：0在售 1交易中 2已售罄 3已下架',
  `online_time` datetime DEFAULT NULL COMMENT '本次上架时间（发布/重新上架时刷新，用于算「挂了多久」）',
  `offline_reason` tinyint DEFAULT NULL COMMENT '下架原因：1卖家手动 2超时自动下架',
  `view_count` int NOT NULL DEFAULT 0 COMMENT '浏览次数（每次进详情页 +1，用于展示商品热度）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_seller_id` (`seller_id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_status` (`status`),
  KEY `idx_audit_status` (`audit_status`),
  KEY `idx_flash_list` (`is_flash_sale`,`status`,`flash_start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';

-- ------------------------------------------------------------
-- 5. favorite 收藏表（多对多中间表）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `favorite`;
CREATE TABLE `favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `product_id` bigint NOT NULL COMMENT '商品id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`,`product_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品收藏表';

-- ------------------------------------------------------------
-- 6. bargain 议价表（仅普通商品）
-- 单轮制：卖家还价后买家只能接受/拒绝，不再支持多轮还价
-- expire_time 用于"5 过期"状态（定时任务扫描 bargain_status + expire_time）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `bargain`;
CREATE TABLE `bargain` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `seller_id` bigint NOT NULL COMMENT '卖家id',
  `buyer_id` bigint NOT NULL COMMENT '买家id',
  `product_id` bigint NOT NULL COMMENT '商品id',
  `buyer_offer` decimal(10,2) NOT NULL COMMENT '买家出价',
  `seller_counter_offer` decimal(10,2) DEFAULT NULL COMMENT '卖家还价',
  `bargain_status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0待处理 1买家出价 2卖家还价 3已接受 4已拒绝 5已过期',
  `order_id` bigint DEFAULT NULL COMMENT '议价成功生成的订单id',
  `expire_time` datetime DEFAULT NULL COMMENT '议价有效期截止时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_buyer_seller` (`buyer_id`,`seller_id`),
  KEY `idx_status_expire` (`bargain_status`,`expire_time`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='议价记录表';

-- ------------------------------------------------------------
-- 7. trade_order 交易订单表（三种来源共用）
-- order_source: 1普通下单 2议价生成 3捡漏抢购
-- idx_status_expire 服务于超时扫描：where status=0 and expire_time < now
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `trade_order`;
CREATE TABLE `trade_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_no` varchar(32) NOT NULL COMMENT '业务订单编号（对外展示）',
  `seller_id` bigint NOT NULL COMMENT '卖家id',
  `buyer_id` bigint NOT NULL COMMENT '买家id',
  `product_id` bigint NOT NULL COMMENT '商品id',
  `real_price` decimal(10,2) NOT NULL COMMENT '实际成交价（与商品标价解耦）',
  `deal_type` tinyint NOT NULL DEFAULT 0 COMMENT '交易方式：0自提 1跑腿',
  `order_source` tinyint NOT NULL DEFAULT 1 COMMENT '订单来源：1普通下单 2议价生成 3捡漏抢购',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0待确认 1待交易 2已完成 3已取消 4超时取消',
  `pay_status` tinyint NOT NULL DEFAULT 0 COMMENT '支付状态：0未支付 1已支付 2已退款',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
  `pay_no` varchar(64) DEFAULT NULL COMMENT '支付流水号（模拟通道生成；唯一，重复回调靠它挡住）',
  `pickup_place` varchar(255) DEFAULT NULL COMMENT '交易地点（自提时填：宿舍区/教学楼/校门口）',
  `remark` varchar(255) DEFAULT NULL COMMENT '买家备注',
  `expire_time` datetime DEFAULT NULL COMMENT '订单超时截止时间（捡漏10分钟/普通30分钟）',
  `finish_time` datetime DEFAULT NULL COMMENT '交易完成时间',
  `cancel_reason` varchar(255) DEFAULT NULL COMMENT '取消原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_buyer_id` (`buyer_id`),
  KEY `idx_seller_id` (`seller_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_status_pay_expire` (`status`,`pay_status`,`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易订单表';

-- ------------------------------------------------------------
-- 7.5 payment_record 支付流水表【S3-1 新增】
-- 记录每一次支付/退款动作：一方面用于对账，另一方面是「回调幂等」的兜底——
-- 同一个支付通知重复到达时，第二次插入流水会撞 uk_pay_no 唯一键，从而避免重复处理。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `payment_record`;
CREATE TABLE `payment_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '交易订单id',
  `order_no` varchar(32) NOT NULL COMMENT '订单编号（冗余一份，对账时不用再关联订单表）',
  `pay_no` varchar(64) NOT NULL COMMENT '支付流水号',
  `amount` decimal(10,2) NOT NULL COMMENT '金额',
  `pay_type` tinyint NOT NULL COMMENT '类型：1支付 2退款',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0处理中 1成功 2失败',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pay_no` (`pay_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_pay_type` (`pay_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付流水表';

-- ------------------------------------------------------------
-- 8. delivery_order 跑腿订单表（从属交易订单，1:1）
-- 不冗余存买卖双方与商品，通过 trade_order_id 关联获取
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `delivery_order`;
CREATE TABLE `delivery_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `trade_order_id` bigint NOT NULL COMMENT '关联主交易订单id',
  `runner_user_id` bigint DEFAULT NULL COMMENT '跑腿员用户id（未接单为NULL）',
  `pick_address` varchar(255) NOT NULL COMMENT '取货地址',
  `receive_address` varchar(255) NOT NULL COMMENT '收货地址',
  `delivery_fee` decimal(10,2) NOT NULL COMMENT '跑腿费用（下单时算好的计费快照）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0待接单 1已接单 2取货中 3已送达 4已取消',
  `accept_time` datetime DEFAULT NULL COMMENT '接单时间',
  `finish_time` datetime DEFAULT NULL COMMENT '送达时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trade_order` (`trade_order_id`),
  KEY `idx_runner_user_id` (`runner_user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='跑腿订单表';

-- ------------------------------------------------------------
-- 9. review 评价表
-- uk_order_from：一个订单的同一方向只能评价一次（防刷评）
-- is_default：区分"真实评价"与"逾期系统默认好评"
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `review`;
CREATE TABLE `review` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '所属订单id',
  `product_id` bigint NOT NULL COMMENT '商品id',
  `from_user_id` bigint NOT NULL COMMENT '评价人id',
  `to_user_id` bigint NOT NULL COMMENT '被评价人id',
  `star` tinyint NOT NULL COMMENT '星级1~5',
  `content` varchar(500) DEFAULT NULL COMMENT '评价文字',
  `is_default` tinyint NOT NULL DEFAULT 0 COMMENT '是否系统默认好评：0真实评价 1逾期默认',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_from` (`order_id`,`from_user_id`),
  KEY `idx_to_user_id` (`to_user_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评价表';

-- ------------------------------------------------------------
-- 10. complaint 投诉/举报工单表
-- complaint_type:   0商品举报（无需下单） / 1交易纠纷（订单内）
-- complaint_reason: 细分原因，对应需求里列的四类
-- images:           凭证图片（需求要求"上传图片证据"）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `complaint`;
CREATE TABLE `complaint` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `product_id` bigint NOT NULL COMMENT '关联商品id',
  `order_id` bigint DEFAULT NULL COMMENT '关联订单id，商品举报可为NULL',
  `complainant_user_id` bigint NOT NULL COMMENT '投诉人id',
  `respondent_user_id` bigint NOT NULL COMMENT '被投诉人id',
  `complaint_type` tinyint NOT NULL COMMENT '工单类型：0商品举报 1交易纠纷',
  `complaint_reason` tinyint NOT NULL COMMENT '投诉原因：1货不对板 2虚假描述 3对方失联 4跑腿纠纷 9其他',
  `content` text NOT NULL COMMENT '投诉内容',
  `images` varchar(1000) DEFAULT NULL COMMENT '凭证图片url，JSON数组',
  `handle_status` tinyint NOT NULL DEFAULT 0 COMMENT '处理状态：0待处理 1处理中 2已完结',
  -- S3-10b 补的字段：原设计只有 handle_status + handle_note，
  -- 但"投诉是否成立"是核心业务状态（决定扣不扣信用分、也决定统计口径），
  -- 塞进备注里就永远算不出"本月投诉成立率"。属于数据库评审报告里那类"落地才发现的缺口"。
  `handle_result` tinyint DEFAULT NULL COMMENT '处理结果：1投诉成立 2投诉不成立，NULL=未处理',
  `handle_note` varchar(500) DEFAULT NULL COMMENT '管理员处理备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_complainant` (`complainant_user_id`),
  KEY `idx_respondent` (`respondent_user_id`),
  KEY `idx_handle_status` (`handle_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投诉举报工单表';

-- ------------------------------------------------------------
-- 11. flash_sale_record 捡漏抢购参与记录
-- uk_product_user：同一用户对同一捡漏商品只允许一条记录（防重复入库）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `flash_sale_record`;
CREATE TABLE `flash_sale_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `product_id` bigint NOT NULL COMMENT '捡漏商品id',
  `user_id` bigint NOT NULL COMMENT '参与抢购的用户id',
  `is_success` tinyint NOT NULL DEFAULT 0 COMMENT '是否抢到：0未抢到 1抢购成功',
  `order_id` bigint DEFAULT NULL COMMENT '抢购成功生成的订单id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '参与时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_user` (`product_id`,`user_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_product_success` (`product_id`,`is_success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='捡漏抢购参与记录表';

-- ------------------------------------------------------------
-- 12. conversation 私信会话表【新增】
-- 为什么要它：私信权限（捡漏预热期关闭、抢购成功后仅抢到者 10 分钟内可沟通）
-- 是"会话级"的属性，必须有商品维度和生命周期才能校验。
-- can_send_until：窗口期截止时间；普通商品为 NULL 表示不限期
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `conversation`;
CREATE TABLE `conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `product_id` bigint NOT NULL COMMENT '会话围绕的商品id（权限校验的业务上下文）',
  `buyer_id` bigint NOT NULL COMMENT '买家id',
  `seller_id` bigint NOT NULL COMMENT '卖家id',
  `order_id` bigint DEFAULT NULL COMMENT '关联订单id（由订单产生的会话）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '会话状态：0开放 1已关闭',
  `can_send_until` datetime DEFAULT NULL COMMENT '可发送截止时间（捡漏10分钟窗口），NULL=不限期',
  `last_message_time` datetime DEFAULT NULL COMMENT '最后一条消息时间（会话列表排序用）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_buyer_seller` (`product_id`,`buyer_id`,`seller_id`),
  KEY `idx_buyer_id` (`buyer_id`),
  KEY `idx_seller_id` (`seller_id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私信会话表';

-- ------------------------------------------------------------
-- 13. message 私信消息表
-- conversation_id：消息必属于某个会话（权限、历史分页、未读数都靠它）
-- from_user_id/to_user_id 保留：查询"我发的/我收的"更方便，属于有意的冗余
-- idx_conversation 服务历史消息分页；idx_to_read 服务"我的未读数"
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `message`;
CREATE TABLE `message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `conversation_id` bigint NOT NULL COMMENT '所属会话id',
  `from_user_id` bigint NOT NULL COMMENT '发送人id',
  `to_user_id` bigint NOT NULL COMMENT '接收人id',
  `content` varchar(1000) NOT NULL COMMENT '消息内容',
  `msg_type` tinyint NOT NULL DEFAULT 0 COMMENT '消息类型：0普通文本 1系统提示',
  `is_read` tinyint NOT NULL DEFAULT 0 COMMENT '是否已读：0未读 1已读',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_conversation` (`conversation_id`,`id`),
  KEY `idx_to_read` (`to_user_id`,`is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私信消息表';

-- ------------------------------------------------------------
-- 14. credit_log 信用分变更流水表【新增】
-- 为什么必须有：需求规定"加分有每日上限，防止刷分"。
-- 只有 user.credit_score 一个总分时，算不出"今天已经加了多少分"。
-- 同时它也是扣分溯源和投诉举证的依据。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `credit_log`;
CREATE TABLE `credit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `change_score` int NOT NULL COMMENT '本次变更分值（加分为正，扣分为负）',
  `score_after` int NOT NULL COMMENT '变更后信用分（便于对账）',
  `reason_type` tinyint NOT NULL COMMENT '变更原因：1完成交易 2收到好评 3收到差评 4交易爽约 5发布虚假商品 6投诉核实成立 7实名认证 8平台任务',
  `ref_id` bigint DEFAULT NULL COMMENT '关联业务id（订单id/投诉id等）',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`create_time`),
  KEY `idx_reason_type` (`reason_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='信用分变更流水表';
