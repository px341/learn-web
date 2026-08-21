package com.learn.auth.mapper;

import com.learn.auth.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

/**
 * 用户认证数据访问接口。
 *
 * <p>Mapper 返回内部 UserEntity，其中包含 passwordHash；该对象不得直接作为接口响应。</p>
 */
@Mapper
public interface UserMapper {

    /**
     * 根据已去除首尾空格并转为小写的邮箱查询用户。
     */
    Optional<UserEntity> selectByEmail(@Param("email") String email);

    /**
     * 插入用户。passwordHash 必须已在 Service 中使用 BCrypt 编码。
     */
    int insert(UserEntity user);
}
