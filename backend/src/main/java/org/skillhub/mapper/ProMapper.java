package org.skillhub.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.skillhub.model.ProEntities;

/** 专业版数据访问：实验矩阵 / 知识图谱 / 分析报告。 */
@Mapper
public interface ProMapper {

    // ------------------------------------------------------------ 矩阵
    @Insert("""
            INSERT INTO mx_matrix (code, name, matrix_type, skill_ids, agent_codes, mcp_codes, param_grid,
                scene_code, case_json, status, planned_cells, done_cells, created_at)
            VALUES (#{code}, #{name}, #{matrixType}, #{skillIds}, #{agentCodes}, #{mcpCodes}, #{paramGrid},
                #{sceneCode}, #{caseJson}, #{status}, #{plannedCells}, #{doneCells}, CURRENT_TIMESTAMP)
            """)
    int insertMatrix(ProEntities.Matrix m);

    @Select("SELECT * FROM mx_matrix ORDER BY id DESC")
    List<ProEntities.Matrix> listMatrices();

    @Select("SELECT * FROM mx_matrix WHERE code = #{code}")
    ProEntities.Matrix findMatrix(@Param("code") String code);

    @Update("""
            UPDATE mx_matrix SET status = #{status}, planned_cells = #{plannedCells},
              done_cells = #{doneCells}, summary_json = #{summaryJson}, finished_at = CURRENT_TIMESTAMP
            WHERE code = #{code}
            """)
    int updateMatrix(ProEntities.Matrix m);

    @Delete("DELETE FROM mx_matrix WHERE code = #{code}")
    int deleteMatrix(@Param("code") String code);

    @Insert("""
            INSERT INTO mx_cell (matrix_code, skill_id, agent_code, mcp_code, params_json, scene_code,
                case_code, input_text, expected_text, status, created_at)
            VALUES (#{matrixCode}, #{skillId}, #{agentCode}, #{mcpCode}, #{paramsJson}, #{sceneCode},
                #{caseCode}, #{inputText}, #{expectedText}, 'PENDING', CURRENT_TIMESTAMP)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertCell(ProEntities.Cell c);

    @Update("""
            UPDATE mx_cell SET output_text = #{outputText}, reasoning_text = #{reasoningText},
              status = #{status}, latency_ms = #{latencyMs}, ttft_ms = #{ttftMs},
              prompt_tokens = #{promptTokens}, completion_tokens = #{completionTokens},
              score_total = #{scoreTotal}, score_json = #{scoreJson}, error = #{error}
            WHERE id = #{id}
            """)
    int finishCell(ProEntities.Cell c);

    @Select("SELECT * FROM mx_cell WHERE matrix_code = #{code} ORDER BY id ASC")
    List<ProEntities.Cell> cellsOf(@Param("code") String code);

    @Select("SELECT COUNT(*) FROM mx_cell WHERE matrix_code = #{code} AND status = 'DONE'")
    int countDoneCells(@Param("code") String code);

    @Select("SELECT COUNT(*) FROM mx_cell WHERE matrix_code = #{code}")
    int countCells(@Param("code") String code);

    @Delete("DELETE FROM mx_cell WHERE matrix_code = #{code}")
    int deleteCells(@Param("code") String code);

    // ------------------------------------------------------------ 知识实体
    @Delete("DELETE FROM kg_entity")
    int clearEntities();

    @Delete("DELETE FROM kg_relation")
    int clearRelations();

    @Insert("""
            <script>
            INSERT INTO kg_entity (code, entity_type, name, label, weight, props_json, created_at) VALUES
            <foreach item="e" collection="entities" separator=",">
              (#{e.code}, #{e.entityType}, #{e.name}, #{e.label}, #{e.weight}, #{e.propsJson}, CURRENT_TIMESTAMP)
            </foreach>
            </script>
            """)
    int insertEntities(@Param("entities") List<ProEntities.KgEntity> entities);

    @Insert("""
            <script>
            INSERT INTO kg_relation (src_code, dst_code, relation_type, weight, evidence, created_at) VALUES
            <foreach item="r" collection="relations" separator=",">
              (#{r.srcCode}, #{r.dstCode}, #{r.relationType}, #{r.weight}, #{r.evidence}, CURRENT_TIMESTAMP)
            </foreach>
            </script>
            """)
    int insertRelations(@Param("relations") List<ProEntities.KgRelation> relations);

    @Select("SELECT COUNT(*) FROM kg_entity")
    long countEntities();

    @Select("SELECT COUNT(*) FROM kg_relation")
    long countRelations();

    @Select("SELECT * FROM kg_entity ORDER BY entity_type ASC, code ASC")
    List<ProEntities.KgEntity> listEntities();

    @Select("""
            <script>
            SELECT * FROM kg_entity
            <where>
              <if test="type != null and type != ''">AND entity_type = #{type}</if>
              <if test="kw != null and kw != ''">AND (code LIKE #{kw} OR name LIKE #{kw})</if>
            </where>
            ORDER BY weight DESC, code ASC LIMIT #{limit}
            </script>
            """)
    List<ProEntities.KgEntity> searchEntities(@Param("type") String type, @Param("kw") String kw,
                                              @Param("limit") int limit);

    @Select("SELECT * FROM kg_relation ORDER BY weight DESC")
    List<ProEntities.KgRelation> listRelations();

    @Select("SELECT * FROM kg_relation WHERE src_code = #{code} OR dst_code = #{code}")
    List<ProEntities.KgRelation> relationsOf(@Param("code") String code);

    @Select("""
            SELECT * FROM kg_relation
            WHERE relation_type = #{type} AND (src_code = #{code} OR dst_code = #{code})
            """)
    List<ProEntities.KgRelation> relationsOfType(@Param("code") String code, @Param("type") String type);

    // ------------------------------------------------------------ 快照
    @Insert("""
            INSERT INTO kg_snapshot (code, name, scope, scene_code, entity_count, relation_count,
                payload_json, created_at)
            VALUES (#{code}, #{name}, #{scope}, #{sceneCode}, #{entityCount}, #{relationCount},
                #{payloadJson}, CURRENT_TIMESTAMP)
            """)
    int insertSnapshot(ProEntities.KgSnapshot s);

    @Select("SELECT * FROM kg_snapshot ORDER BY id DESC")
    List<ProEntities.KgSnapshot> listSnapshots();

    // ------------------------------------------------------------ 报告
    @Insert("""
            INSERT INTO ix_report (code, title, method, scope, params_json, result_json, conclusion, created_at)
            VALUES (#{code}, #{title}, #{method}, #{scope}, #{paramsJson}, #{resultJson}, #{conclusion},
                CURRENT_TIMESTAMP)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertReport(ProEntities.IxReport r);

    @Select("SELECT * FROM ix_report ORDER BY id DESC LIMIT #{limit}")
    List<ProEntities.IxReport> listReports(@Param("limit") int limit);

    @Select("SELECT * FROM ix_report WHERE code = #{code}")
    ProEntities.IxReport findReport(@Param("code") String code);
}
