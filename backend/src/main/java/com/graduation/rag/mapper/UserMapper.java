package com.graduation.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.graduation.rag.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper（MyBatis-Plus BaseMapper 自带单表 CRUD）
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
