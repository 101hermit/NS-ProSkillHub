package org.skillhub.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.skillhub.model.RiskAudit;
import org.skillhub.model.RiskSignal;

/** 风险评估结论与信号明细（表 {@code risk_audit} / {@code risk_signal}）。 */
@Mapper
public interface RiskMapper {

    @Insert("""
            INSERT INTO risk_audit (skill_id, risk_score, risk_level, engine, dimension_json, signal_total,
                summary, conclusion, created_at)
            VALUES (#{skillId}, #{riskScore}, #{riskLevel}, #{engine}, #{dimensionJson}, #{signalTotal},
                #{summary}, #{conclusion}, CURRENT_TIMESTAMP)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertAudit(RiskAudit audit);

    @Insert("""
            <script>
            INSERT INTO risk_signal (audit_id, signal_code, indicator, dimension, severity, weight,
                file_path, line_no, remediation) VALUES
            <foreach item="s" collection="signals" separator=",">
              (#{s.auditId}, #{s.signalCode}, #{s.indicator}, #{s.dimension}, #{s.severity}, #{s.weight},
               #{s.filePath}, #{s.lineNo}, #{s.remediation})
            </foreach>
            </script>
            """)
    int insertSignals(@Param("signals") List<RiskSignal> signals);

    /** 最近一次评估（不含结论正文，列表/详情摘要用）。 */
    @Select("""
            SELECT id, skill_id, risk_score, risk_level, engine, dimension_json, signal_total, summary, created_at
            FROM risk_audit WHERE skill_id = #{skillId} ORDER BY id DESC LIMIT 1
            """)
    RiskAudit findLatestMeta(@Param("skillId") String skillId);

    @Select("SELECT * FROM risk_audit WHERE id = #{id}")
    RiskAudit findById(@Param("id") long id);

    @Select("""
            SELECT id, audit_id, signal_code, indicator, dimension, severity, weight, file_path, line_no, remediation
            FROM risk_signal WHERE audit_id = #{auditId}
            ORDER BY weight DESC, id ASC
            """)
    List<RiskSignal> findSignals(@Param("auditId") long auditId);

    @Select("SELECT COUNT(*) FROM risk_audit")
    long countAll();

    @Select("SELECT COUNT(*) FROM risk_signal")
    long countSignals();

    @Select("SELECT severity AS k, COUNT(*) AS c FROM risk_signal GROUP BY severity")
    List<KeyCount> groupSignalsBySeverity();

    @Delete("DELETE FROM risk_signal WHERE audit_id = #{auditId}")
    int deleteSignals(@Param("auditId") long auditId);
}
