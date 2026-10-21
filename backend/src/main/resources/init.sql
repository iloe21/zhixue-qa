-- ============================================================
-- 基于RAG的智能学习问答系统 - 数据库初始化脚本
-- 对应设计文档《需求分析与系统设计.md》第三章（9 张表）
-- 执行方式: mysql -uroot -p --default-character-set=utf8mb4 < init.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS rag_learning
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE rag_learning;

-- 按外键依赖倒序删除，保证脚本可重复执行
DROP TABLE IF EXISTS exercise_question;
DROP TABLE IF EXISTS exercise;
DROP TABLE IF EXISTS message;
DROP TABLE IF EXISTS conversation_kb;
DROP TABLE IF EXISTS conversation;
DROP TABLE IF EXISTS chunk_meta;
DROP TABLE IF EXISTS document;
DROP TABLE IF EXISTS knowledge_base;
DROP TABLE IF EXISTS `user`;

-- ① 用户表
CREATE TABLE `user` (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  username    VARCHAR(50)  NOT NULL COMMENT '登录名',
  password    VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希',
  nickname    VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
  email       VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  role        VARCHAR(20)  NOT NULL DEFAULT 'student' COMMENT 'student/admin',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB COMMENT='用户表';
-- 注：初始管理员账户不在 SQL 中插入（BCrypt 哈希必须由程序生成，避免假哈希坑），
-- 任务 #5 实现认证模块时由启动初始化器创建 admin/admin123。

-- ② 知识库表
CREATE TABLE knowledge_base (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  name        VARCHAR(100) NOT NULL COMMENT '知识库名称',
  description VARCHAR(500) DEFAULT NULL COMMENT '描述',
  visibility  VARCHAR(10)  NOT NULL DEFAULT 'private' COMMENT 'private/public，public仅admin可建',
  owner_id    BIGINT       DEFAULT NULL COMMENT '所有者',
  doc_count   INT          NOT NULL DEFAULT 0 COMMENT '文档数（冗余计数）',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_owner (owner_id),
  KEY idx_visibility (visibility),
  CONSTRAINT fk_kb_owner FOREIGN KEY (owner_id) REFERENCES `user`(id) ON DELETE SET NULL
) ENGINE=InnoDB COMMENT='知识库表';

-- ③ 文档表
CREATE TABLE document (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  kb_id        BIGINT       NOT NULL COMMENT '所属知识库',
  filename     VARCHAR(255) NOT NULL COMMENT '原始文件名',
  file_path    VARCHAR(500) DEFAULT NULL COMMENT '磁盘存储路径（清洗后）',
  file_type    VARCHAR(10)  DEFAULT NULL COMMENT 'pdf/docx/txt/md',
  file_size    BIGINT       DEFAULT NULL COMMENT '字节',
  chunk_count  INT          NOT NULL DEFAULT 0 COMMENT '片段数',
  parse_status TINYINT      NOT NULL DEFAULT 0 COMMENT '0解析中 1成功 2失败',
  error_msg    VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
  created_by   BIGINT       DEFAULT NULL COMMENT '上传人',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_kb (kb_id),
  CONSTRAINT fk_doc_kb FOREIGN KEY (kb_id) REFERENCES knowledge_base(id) ON DELETE CASCADE,
  CONSTRAINT fk_doc_creator FOREIGN KEY (created_by) REFERENCES `user`(id) ON DELETE SET NULL
) ENGINE=InnoDB COMMENT='文档表';

-- ④ 片段元数据表（id 与向量库 Document id 对齐，溯源用）
CREATE TABLE chunk_meta (
  id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '与向量库 Document id 一致',
  doc_id      BIGINT   NOT NULL COMMENT '所属文档',
  kb_id       BIGINT   NOT NULL COMMENT '所属知识库（冗余，便于按库过滤）',
  chunk_index INT      NOT NULL COMMENT '片段序号',
  content     TEXT     NOT NULL COMMENT '片段原文',
  char_count  INT      DEFAULT NULL COMMENT '字符数',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_doc_index (doc_id, chunk_index),
  KEY idx_kb (kb_id),
  CONSTRAINT fk_chunk_doc FOREIGN KEY (doc_id) REFERENCES document(id) ON DELETE CASCADE,
  CONSTRAINT fk_chunk_kb FOREIGN KEY (kb_id) REFERENCES knowledge_base(id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='文本片段元数据表';

-- ⑤ 会话表
CREATE TABLE conversation (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  user_id    BIGINT       NOT NULL COMMENT '发起用户',
  title      VARCHAR(100) DEFAULT NULL COMMENT '默认取首问前20字，可重命名',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  CONSTRAINT fk_conv_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='会话表';

-- ⑥ 会话-知识库范围关联表
CREATE TABLE conversation_kb (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL,
  kb_id           BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_conv_kb (conversation_id, kb_id),
  CONSTRAINT fk_ck_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE,
  CONSTRAINT fk_ck_kb FOREIGN KEY (kb_id) REFERENCES knowledge_base(id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='会话知识库范围表';

-- ⑦ 消息表
CREATE TABLE message (
  id              BIGINT      NOT NULL AUTO_INCREMENT,
  conversation_id BIGINT      NOT NULL,
  role            VARCHAR(10) NOT NULL COMMENT 'user/assistant',
  content         TEXT        NOT NULL COMMENT '消息内容',
  citations       JSON        DEFAULT NULL COMMENT 'assistant消息引用列表 [{chunkId,docId,docName,chunkIndex,score,snippet}]',
  created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_conversation (conversation_id),
  CONSTRAINT fk_msg_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='消息表';

-- ⑧ 练习表
CREATE TABLE exercise (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  user_id      BIGINT       NOT NULL COMMENT '创建用户',
  kb_id        BIGINT       DEFAULT NULL COMMENT '出题知识库',
  title        VARCHAR(100) DEFAULT NULL COMMENT '如《Java程序设计》练习1',
  choice_count INT          NOT NULL DEFAULT 0 COMMENT '选择题数量',
  blank_count  INT          NOT NULL DEFAULT 0 COMMENT '填空题数量',
  score        INT          DEFAULT NULL COMMENT '得分（作答后写入）',
  total_score  INT          DEFAULT NULL COMMENT '满分',
  status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0待作答 1已作答',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  CONSTRAINT fk_ex_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE,
  CONSTRAINT fk_ex_kb FOREIGN KEY (kb_id) REFERENCES knowledge_base(id) ON DELETE SET NULL
) ENGINE=InnoDB COMMENT='练习表';

-- ⑨ 题目表
CREATE TABLE exercise_question (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  exercise_id     BIGINT       NOT NULL,
  type            TINYINT      NOT NULL COMMENT '1选择题 2填空题',
  question        TEXT         NOT NULL COMMENT '题干',
  options         JSON         DEFAULT NULL COMMENT '选择题选项 [{"key":"A","text":"..."}]',
  answer          VARCHAR(500) NOT NULL COMMENT '正确答案，填空题多答案用|分隔',
  explanation     TEXT         DEFAULT NULL COMMENT '解析',
  score           INT          NOT NULL DEFAULT 5 COMMENT '本题分值',
  source_chunk_id BIGINT       DEFAULT NULL COMMENT '出题依据片段',
  user_answer     VARCHAR(500) DEFAULT NULL COMMENT '学生答案',
  is_correct      TINYINT      DEFAULT NULL COMMENT '判分结果',
  PRIMARY KEY (id),
  KEY idx_exercise (exercise_id),
  CONSTRAINT fk_eq_exercise FOREIGN KEY (exercise_id) REFERENCES exercise(id) ON DELETE CASCADE,
  CONSTRAINT fk_eq_chunk FOREIGN KEY (source_chunk_id) REFERENCES chunk_meta(id) ON DELETE SET NULL
) ENGINE=InnoDB COMMENT='题目表';
