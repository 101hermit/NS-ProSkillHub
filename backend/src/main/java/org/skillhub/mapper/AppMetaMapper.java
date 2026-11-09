package org.skillhub.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 系统元信息（表 {@code app_meta}）：schema 版本、库版本、导入时间。 */
@Mapper
public interface AppMetaMapper {

    @Select("SELECT meta_value FROM app_meta WHERE meta_key = #{key}")
    String get(@Param("key") String key);

    @Select("SELECT meta_key AS k, meta_value AS v FROM app_meta")
    List<Map<String, Object>> all();

    @org.apache.ibatis.annotations.Insert("""
            INSERT INTO app_meta (meta_key, meta_value, updated_at) VALUES (#{key}, #{value}, CURRENT_TIMESTAMP)
            """)
    int insert(@Param("key") String key, @Param("value") String value);

    @org.apache.ibatis.annotations.Update("""
            UPDATE app_meta SET meta_value = #{value}, updated_at = CURRENT_TIMESTAMP WHERE meta_key = #{key}
            """)
    int update(@Param("key") String key, @Param("value") String value);
}
