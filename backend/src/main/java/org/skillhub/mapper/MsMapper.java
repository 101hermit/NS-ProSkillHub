package org.skillhub.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.skillhub.model.MsEntities;

/**
 * Skill-MS（库语义）的数据访问：逻辑库 / 索引 / 约束 / 事件 / 事务台账。
 *
 * <p>SQL 只用可移植子集（见 docs/02 §1），H2 与 MySQL 8 共用。
 */
@Mapper
public interface MsMapper {

    // ------------------------------------------------------------ skillset
    @Select("SELECT * FROM skillset ORDER BY code ASC")
    List<MsEntities.Skillset> listSkillsets();

    @Select("SELECT * FROM skillset WHERE code = #{code}")
    MsEntities.Skillset findSkillset(@Param("code") String code);

    @Insert("""
            INSERT INTO skillset (code, name, description, owner, skill_count, status, created_at, updated_at)
            VALUES (#{code}, #{name}, #{description}, #{owner}, 0, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """)
    int insertSkillset(MsEntities.Skillset set);

    @Update("UPDATE skillset SET skill_count = #{count}, updated_at = CURRENT_TIMESTAMP WHERE code = #{code}")
    int updateSkillsetCount(@Param("code") String code, @Param("count") int count);

    @Delete("DELETE FROM skillset WHERE code = #{code}")
    int deleteSkillset(@Param("code") String code);

    @Select("SELECT COUNT(*) FROM skill WHERE skillset_code = #{code}")
    long countSkillsInSet(@Param("code") String code);

    @Update("UPDATE skill SET skillset_code = #{to} WHERE skillset_code = #{from}")
    int moveSkills(@Param("from") String from, @Param("to") String to);

    // ------------------------------------------------------------ index
    @Select("SELECT * FROM ms_index_def ORDER BY code ASC")
    List<MsEntities.IndexDef> listIndexes();

    @Select("SELECT * FROM ms_index_def WHERE code = #{code}")
    MsEntities.IndexDef findIndex(@Param("code") String code);

    @Insert("""
            INSERT INTO ms_index_def (code, name, skillset_code, columns_col, index_type, unique_flag,
                status, entry_count, hit_count, last_built_at, created_at)
            VALUES (#{code}, #{name}, #{skillsetCode}, #{columnsCol}, #{indexType}, #{uniqueFlag},
                'BUILDING', 0, 0, NULL, CURRENT_TIMESTAMP)
            """)
    int insertIndex(MsEntities.IndexDef def);

    @Update("""
            UPDATE ms_index_def SET status = #{status}, entry_count = #{entryCount},
              last_built_at = CURRENT_TIMESTAMP WHERE code = #{code}
            """)
    int markIndexBuilt(@Param("code") String code, @Param("status") String status,
                       @Param("entryCount") int entryCount);

    @Update("UPDATE ms_index_def SET status = #{status} WHERE code = #{code}")
    int updateIndexStatus(@Param("code") String code, @Param("status") String status);

    @Update("UPDATE ms_index_def SET hit_count = hit_count + 1 WHERE code = #{code}")
    int touchIndexHit(@Param("code") String code);

    @Delete("DELETE FROM ms_index_def WHERE code = #{code}")
    int deleteIndex(@Param("code") String code);

    @Delete("DELETE FROM ms_index_entry WHERE index_code = #{code}")
    int clearIndexEntries(@Param("code") String code);

    @Insert("""
            <script>
            INSERT INTO ms_index_entry (index_code, skill_id, key_value) VALUES
            <foreach item="e" collection="entries" separator=",">
              (#{e.indexCode}, #{e.skillId}, #{e.keyValue})
            </foreach>
            </script>
            """)
    int insertIndexEntries(@Param("entries") List<MsEntities.IndexEntry> entries);

    @Select("""
            SELECT id, index_code, skill_id, key_value FROM ms_index_entry
            WHERE index_code = #{code} ORDER BY key_value ASC, skill_id ASC LIMIT #{limit}
            """)
    List<MsEntities.IndexEntry> listIndexEntries(@Param("code") String code, @Param("limit") int limit);

    /** 索引里到底有多少条目录项（对象树要显示这个数，用 COUNT 比拉全表再 size 更合适）。 */
    @Select("SELECT COUNT(*) FROM ms_index_entry WHERE index_code = #{code}")
    long countIndexEntries(@Param("code") String code);

    // ------------------------------------------------------------ constraint
    @Select("SELECT * FROM ms_constraint_def ORDER BY code ASC")
    List<MsEntities.ConstraintDef> listConstraints();

    @Select("SELECT * FROM ms_constraint_def WHERE code = #{code}")
    MsEntities.ConstraintDef findConstraint(@Param("code") String code);

    @Select("""
            SELECT * FROM ms_constraint_def
            WHERE status = 'ACTIVE' AND (skillset_code = #{skillsetCode} OR skillset_code = 'default')
            """)
    List<MsEntities.ConstraintDef> activeConstraints(@Param("skillsetCode") String skillsetCode);

    @Insert("""
            INSERT INTO ms_constraint_def (code, name, skillset_code, constraint_type, target_column,
                expression, severity, status, violation_count, created_at)
            VALUES (#{code}, #{name}, #{skillsetCode}, #{constraintType}, #{targetColumn},
                #{expression}, #{severity}, 'ACTIVE', 0, CURRENT_TIMESTAMP)
            """)
    int insertConstraint(MsEntities.ConstraintDef def);

    @Update("""
            UPDATE ms_constraint_def SET name = #{name}, target_column = #{targetColumn},
              expression = #{expression}, severity = #{severity}, status = #{status}
            WHERE code = #{code}
            """)
    int updateConstraint(MsEntities.ConstraintDef def);

    @Update("""
            UPDATE ms_constraint_def SET violation_count = violation_count + 1,
              last_violated_at = CURRENT_TIMESTAMP WHERE code = #{code}
            """)
    int touchConstraintViolation(@Param("code") String code);

    @Delete("DELETE FROM ms_constraint_def WHERE code = #{code}")
    int deleteConstraint(@Param("code") String code);

    // ------------------------------------------------------------ event
    @Select("SELECT * FROM ms_event_def ORDER BY code ASC")
    List<MsEntities.EventDef> listEvents();

    @Select("SELECT * FROM ms_event_def WHERE code = #{code}")
    MsEntities.EventDef findEvent(@Param("code") String code);

    @Select("""
            SELECT * FROM ms_event_def
            WHERE enabled = TRUE AND (skillset_code = #{skillsetCode} OR skillset_code = 'default')
            """)
    List<MsEntities.EventDef> activeEvents(@Param("skillsetCode") String skillsetCode);

    @Insert("""
            INSERT INTO ms_event_def (code, name, skillset_code, trigger_event, condition_expr, action_type,
                action_payload, enabled, fired_count, created_at)
            VALUES (#{code}, #{name}, #{skillsetCode}, #{triggerEvent}, #{conditionExpr}, #{actionType},
                #{actionPayload}, TRUE, 0, CURRENT_TIMESTAMP)
            """)
    int insertEvent(MsEntities.EventDef def);

    @Update("""
            UPDATE ms_event_def SET name = #{name}, trigger_event = #{triggerEvent},
              condition_expr = #{conditionExpr}, action_type = #{actionType},
              action_payload = #{actionPayload}, enabled = #{enabled}
            WHERE code = #{code}
            """)
    int updateEvent(MsEntities.EventDef def);

    @Update("UPDATE ms_event_def SET fired_count = fired_count + 1 WHERE code = #{code}")
    int touchEventFired(@Param("code") String code);

    @Delete("DELETE FROM ms_event_def WHERE code = #{code}")
    int deleteEvent(@Param("code") String code);

    // ------------------------------------------------------------ event log
    @Insert("""
            INSERT INTO event_log (event_code, trigger_on, skill_id, action_type, payload, status,
                attempts, last_error, created_at, updated_at)
            VALUES (#{eventCode}, #{triggerOn}, #{skillId}, #{actionType}, #{payload}, #{status},
                #{attempts}, #{lastError}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertEventLog(MsEntities.EventLog log);

    @Select("""
            <script>
            SELECT * FROM event_log
            <where>
              <if test="status != null and status != ''">AND status = #{status}</if>
            </where>
            ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<MsEntities.EventLog> listEventLogs(@Param("status") String status,
                                            @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM event_log
            <where>
              <if test="status != null and status != ''">AND status = #{status}</if>
            </where>
            </script>
            """)
    long countEventLogs(@Param("status") String status);

    @Select("SELECT * FROM event_log WHERE id = #{id}")
    MsEntities.EventLog findEventLog(@Param("id") long id);

    @Update("""
            UPDATE event_log SET status = #{status}, attempts = #{attempts}, last_error = #{lastError},
              updated_at = CURRENT_TIMESTAMP WHERE id = #{id}
            """)
    int updateEventLog(MsEntities.EventLog log);

    @Select("SELECT status AS k, COUNT(*) AS c FROM event_log GROUP BY status")
    List<KeyCount> groupEventLogByStatus();

    // ------------------------------------------------------------ tx log
    @Insert("""
            INSERT INTO ms_tx_log (tx_id, op_type, skill_id, actor, verdict, before_json, after_json,
                constraint_hits, index_touched, events_fired, created_at)
            VALUES (#{txId}, #{opType}, #{skillId}, #{actor}, #{verdict}, #{beforeJson}, #{afterJson},
                #{constraintHits}, #{indexTouched}, #{eventsFired}, CURRENT_TIMESTAMP)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertTxLog(MsEntities.TxLog log);

    @Select("""
            <script>
            SELECT * FROM ms_tx_log
            <where>
              <if test="skillId != null and skillId != ''">AND skill_id = #{skillId}</if>
            </where>
            ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<MsEntities.TxLog> listTxLogs(@Param("skillId") String skillId,
                                      @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM ms_tx_log
            <where>
              <if test="skillId != null and skillId != ''">AND skill_id = #{skillId}</if>
            </where>
            </script>
            """)
    long countTxLogs(@Param("skillId") String skillId);
}
