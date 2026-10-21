package com.graduation.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.graduation.rag.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {
}
