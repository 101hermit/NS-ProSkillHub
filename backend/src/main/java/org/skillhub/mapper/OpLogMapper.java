package org.skillhub.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.skillhub.model.OpLog;

/** 操作审计流水（表 {@code op_log}）。 */
@Mapper
public interface OpLogMapper {

    @Insert("""
            INSERT INTO op_log (actor, role, action, target_type, target_id, detail, trace_id, created_at)
            VALUES (#{actor}, #{role}, #{action}, #{targetType}, #{targetId}, #{detail}, #{traceId},
                CURRENT_TIMESTAMP)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OpLog log);

    @Select("""
            <script>
            SELECT * FROM op_log
            <where>
              <if test="action != null and action != ''">AND action = #{action}</if>
            </where>
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<OpLog> findPage(@Param("action") String action, @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM op_log
            <where>
              <if test="action != null and action != ''">AND action = #{action}</if>
            </where>
            </script>
            """)
    long count(@Param("action") String action);
}
