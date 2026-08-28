package com.learn.mistakeservice.mybatis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/** 在 PostgreSQL JSONB 字符串数组与 Java {@code List<String>} 之间转换。 */
@MappedTypes(List.class)
@MappedJdbcTypes(value = JdbcType.OTHER, includeNullJdbcType = true)
public class StringListJsonTypeHandler extends BaseTypeHandler<List<String>> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    @Override
    public void setNonNullParameter(
            PreparedStatement statement,
            int index,
            List<String> parameter,
            JdbcType jdbcType
    ) throws SQLException {
        try {
            statement.setObject(
                    index,
                    OBJECT_MAPPER.writeValueAsString(parameter),
                    Types.OTHER
            );
        } catch (JsonProcessingException exception) {
            throw new SQLException("Cannot serialize string list as JSONB", exception);
        }
    }

    @Override
    public List<String> getNullableResult(ResultSet resultSet, String columnName)
            throws SQLException {
        return parse(resultSet.getString(columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet resultSet, int columnIndex)
            throws SQLException {
        return parse(resultSet.getString(columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement statement, int columnIndex)
            throws SQLException {
        return parse(statement.getString(columnIndex));
    }

    private List<String> parse(String value) throws SQLException {
        if (value == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(value, STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Cannot deserialize JSONB as string list", exception);
        }
    }
}
