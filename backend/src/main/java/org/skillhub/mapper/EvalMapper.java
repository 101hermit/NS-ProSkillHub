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

/** 接入注册表（Agent / MCP）、评估指标字典、单点实测与分数。 */
@Mapper
public interface EvalMapper {

    // ------------------------------------------------------------ agent
    @Select("SELECT * FROM agent_registry ORDER BY code ASC")
    List<MsEntities.AgentRecord> listAgents();

    @Select("SELECT * FROM agent_registry WHERE code = #{code}")
    MsEntities.AgentRecord findAgent(@Param("code") String code);

    @Insert("""
            INSERT INTO agent_registry (code, name, provider, base_url, model, api_key, temperature, top_p,
                max_tokens, enabled, status, last_error, created_at)
            VALUES (#{code}, #{name}, #{provider}, #{baseUrl}, #{model}, #{apiKey}, #{temperature}, #{topP},
                #{maxTokens}, #{enabled}, 'unknown', '', CURRENT_TIMESTAMP)
            """)
    int insertAgent(MsEntities.AgentRecord rec);

    @Update("""
            UPDATE agent_registry SET name = #{name}, provider = #{provider}, base_url = #{baseUrl},
              model = #{model}, api_key = #{apiKey}, temperature = #{temperature}, top_p = #{topP},
              max_tokens = #{maxTokens}, enabled = #{enabled} WHERE code = #{code}
            """)
    int updateAgent(MsEntities.AgentRecord rec);

    @Update("""
            UPDATE agent_registry SET status = #{status}, last_probe_at = CURRENT_TIMESTAMP,
              last_probe_ms = #{ms}, last_error = #{error} WHERE code = #{code}
            """)
    int markAgentProbe(@Param("code") String code, @Param("status") String status,
                       @Param("ms") int ms, @Param("error") String error);

    @Delete("DELETE FROM agent_registry WHERE code = #{code}")
    int deleteAgent(@Param("code") String code);

    @Select("SELECT COUNT(*) FROM agent_registry")
    long countAgents();

    // ------------------------------------------------------------ mcp
    @Select("SELECT * FROM mcp_registry ORDER BY code ASC")
    List<MsEntities.McpRecord> listMcps();

    @Select("SELECT * FROM mcp_registry WHERE code = #{code}")
    MsEntities.McpRecord findMcp(@Param("code") String code);

    @Insert("""
            INSERT INTO mcp_registry (code, name, transport, endpoint, tool_name, description, headers_json,
                enabled, status, last_error, created_at)
            VALUES (#{code}, #{name}, #{transport}, #{endpoint}, #{toolName}, #{description}, #{headersJson},
                #{enabled}, 'unknown', '', CURRENT_TIMESTAMP)
            """)
    int insertMcp(MsEntities.McpRecord rec);

    @Update("""
            UPDATE mcp_registry SET name = #{name}, transport = #{transport}, endpoint = #{endpoint},
              tool_name = #{toolName}, description = #{description}, headers_json = #{headersJson},
              enabled = #{enabled} WHERE code = #{code}
            """)
    int updateMcp(MsEntities.McpRecord rec);

    @Update("""
            UPDATE mcp_registry SET status = #{status}, last_probe_at = CURRENT_TIMESTAMP,
              last_probe_ms = #{ms}, last_error = #{error} WHERE code = #{code}
            """)
    int markMcpProbe(@Param("code") String code, @Param("status") String status,
                     @Param("ms") int ms, @Param("error") String error);

    @Delete("DELETE FROM mcp_registry WHERE code = #{code}")
    int deleteMcp(@Param("code") String code);

    @Select("SELECT COUNT(*) FROM mcp_registry")
    long countMcps();

    // ------------------------------------------------------------ metric
    @Select("SELECT * FROM eval_metric_def ORDER BY sort_no ASC, code ASC")
    List<MsEntities.MetricDef> listMetrics();

    @Insert("""
            INSERT INTO eval_metric_def (code, label, dimension, unit, higher_better, weight, formula,
                description, sort_no)
            VALUES (#{code}, #{label}, #{dimension}, #{unit}, #{higherBetter}, #{weight}, #{formula},
                #{description}, #{sortNo})
            """)
    int insertMetric(MsEntities.MetricDef def);

    @Select("SELECT COUNT(*) FROM eval_metric_def")
    long countMetrics();

    // ------------------------------------------------------------ run
    @Insert("""
            INSERT INTO eval_run (code, skill_id, agent_code, mcp_code, params_json, scene_code,
                input_text, expected_text, output_text, reasoning_text, status, latency_ms, ttft_ms,
                prompt_tokens, completion_tokens, score_total, score_json, error, created_at, finished_at)
            VALUES (#{code}, #{skillId}, #{agentCode}, #{mcpCode}, #{paramsJson}, #{sceneCode},
                #{inputText}, #{expectedText}, #{outputText}, #{reasoningText}, #{status}, #{latencyMs},
                #{ttftMs}, #{promptTokens}, #{completionTokens}, #{scoreTotal}, #{scoreJson}, #{error},
                CURRENT_TIMESTAMP, NULL)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRun(MsEntities.EvalRun run);

    @Update("""
            UPDATE eval_run SET output_text = #{outputText}, reasoning_text = #{reasoningText},
              status = #{status}, latency_ms = #{latencyMs}, ttft_ms = #{ttftMs},
              prompt_tokens = #{promptTokens}, completion_tokens = #{completionTokens},
              score_total = #{scoreTotal}, score_json = #{scoreJson}, error = #{error},
              finished_at = CURRENT_TIMESTAMP
            WHERE code = #{code}
            """)
    int finishRun(MsEntities.EvalRun run);

    @Select("""
            <script>
            SELECT * FROM eval_run
            <where>
              <if test="skillId != null and skillId != ''">AND skill_id = #{skillId}</if>
              <if test="status != null and status != ''">AND status = #{status}</if>
            </where>
            ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<MsEntities.EvalRun> listRuns(@Param("skillId") String skillId, @Param("status") String status,
                                      @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM eval_run
            <where>
              <if test="skillId != null and skillId != ''">AND skill_id = #{skillId}</if>
              <if test="status != null and status != ''">AND status = #{status}</if>
            </where>
            </script>
            """)
    long countRuns(@Param("skillId") String skillId, @Param("status") String status);

    @Select("SELECT * FROM eval_run WHERE code = #{code}")
    MsEntities.EvalRun findRun(@Param("code") String code);

    @Select("""
            SELECT * FROM eval_run WHERE skill_id = #{skillId} AND status = 'DONE'
            ORDER BY score_total DESC LIMIT 200
            """)
    List<MsEntities.EvalRun> runsOfSkill(@Param("skillId") String skillId);

    // ------------------------------------------------------------ score
    @Insert("""
            <script>
            INSERT INTO eval_score (run_id, metric_code, metric_value, weight, detail) VALUES
            <foreach item="s" collection="scores" separator=",">
              (#{s.runId}, #{s.metricCode}, #{s.metricValue}, #{s.weight}, #{s.detail})
            </foreach>
            </script>
            """)
    int insertScores(@Param("scores") List<MsEntities.EvalScore> scores);

    @Select("SELECT * FROM eval_score WHERE run_id = #{runId} ORDER BY metric_code ASC")
    List<MsEntities.EvalScore> findScores(@Param("runId") long runId);

    @Select("SELECT metric_code AS k, AVG(metric_value) AS c FROM eval_score GROUP BY metric_code")
    List<AvgRow> avgScoreByMetric();

    /** 指标均值行（{@code c} 为 double，因此单独定义而不是复用 {@link KeyCount}）。 */
    class AvgRow {
        public String k;
        public Double c;
    }
}
