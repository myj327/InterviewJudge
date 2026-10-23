/*
 Navicat Premium Dump SQL

 Source Server         : doj
 Source Server Type    : MySQL
 Source Server Version : 90400 (9.4.0)
 Source Host           : localhost:3306
 Source Schema         : doj_submission

 Target Server Type    : MySQL
 Target Server Version : 90400 (9.4.0)
 File Encoding         : 65001

 Date: 25/01/2026 16:43:29
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for submission
-- ----------------------------
DROP TABLE IF EXISTS `submission`;
CREATE TABLE `submission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '提交记录主键',
  `attempt_id` bigint NOT NULL,
  `idempotency_key` varchar(64) NOT NULL,
  `user_id` bigint NOT NULL COMMENT '用户ID（来源于 doj_user.user）',
  `problem_id` bigint NOT NULL COMMENT '题目ID（来源于 doj_problem.problem）',
  `user_name` varchar(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户名称',
  `problem_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '题目名称',
  `language` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'cpp' COMMENT '编程语言',
  `code` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '提交的代码文本内容',
  `exit_value` int DEFAULT NULL COMMENT '程序退出码',
  `status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '判题状态',
  `judge_stage` varchar(24) NOT NULL DEFAULT 'PENDING',
  `message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '判题详细信息',
  `time` double DEFAULT NULL COMMENT '运行时间（单位：秒）',
  `memory` bigint DEFAULT NULL COMMENT '内存使用（单位：KB）',
  `submit_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_submission_attempt_idempotency` (`attempt_id`,`idempotency_key`)
) ENGINE=InnoDB AUTO_INCREMENT=58 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='代码提交记录表';

-- ----------------------------
-- Table structure for review_report
-- ----------------------------
DROP TABLE IF EXISTS `review_report`;
CREATE TABLE `review_report` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `submission_id` bigint NOT NULL,
  `prompt_version` varchar(24) NOT NULL,
  `status` varchar(24) NOT NULL,
  `report_json` json NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_report_submission_prompt` (`submission_id`,`prompt_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
