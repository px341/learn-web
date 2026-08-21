package com.learn.auth.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;

/**
 * PostgreSQL UUID 与 Java UUID 的 MyBatis 类型转换器。
 *
 * <p>PostgreSQL JDBC 驱动原生支持 {@link UUID}，因此写入时使用 SQL OTHER 类型，
 * 读取时直接请求驱动返回 UUID，避免在 Mapper 中把主键降级为 String。</p>
 */
@MappedTypes(UUID.class)
@MappedJdbcTypes(value = JdbcType.OTHER, includeNullJdbcType = true)
public class UuidTypeHandler extends BaseTypeHandler<UUID> {

    @Override
    public void setNonNullParameter(
            PreparedStatement statement,
            int parameterIndex,
            UUID parameter,
            JdbcType jdbcType
    ) throws SQLException {
        statement.setObject(parameterIndex, parameter, Types.OTHER);
    }

    @Override
    public UUID getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return resultSet.getObject(columnName, UUID.class);
    }

    @Override
    public UUID getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getObject(columnIndex, UUID.class);
    }

    @Override
    public UUID getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return statement.getObject(columnIndex, UUID.class);
    }
}
