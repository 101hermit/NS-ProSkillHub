package org.skillhub.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.skillhub.model.Contribution;

/** 在线贡献记录（表 {@code contribution}）。 */
@Mapper
public interface ContributionMapper {

    @Insert("""
            INSERT INTO contribution (kind, contributor_name, name, title, version, description, source_type,
                source_url, storage_dir, status, cla_signed, cla_signature, cla_signed_at, skill_id,
                risk_score, risk_level, notes, created_at, updated_at)
            VALUES (#{kind}, #{contributorName}, #{name}, #{title}, #{version}, #{description}, #{sourceType},
                #{sourceUrl}, #{storageDir}, #{status}, #{claSigned}, #{claSignature}, #{claSignedAt}, #{skillId},
                #{riskScore}, #{riskLevel}, #{notes}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Contribution contribution);

    @Update("""
            <script>
            UPDATE contribution
            <set>
              <if test="c.name != null">name = #{c.name},</if>
              <if test="c.title != null">title = #{c.title},</if>
              <if test="c.version != null">version = #{c.version},</if>
              <if test="c.description != null">description = #{c.description},</if>
              <if test="c.sourceUrl != null">source_url = #{c.sourceUrl},</if>
              <if test="c.storageDir != null">storage_dir = #{c.storageDir},</if>
              <if test="c.status != null">status = #{c.status},</if>
              <if test="c.claSigned != null">cla_signed = #{c.claSigned},</if>
              <if test="c.claSignature != null">cla_signature = #{c.claSignature},</if>
              <if test="c.claSignedAt != null">cla_signed_at = #{c.claSignedAt},</if>
              <if test="c.skillId != null">skill_id = #{c.skillId},</if>
              <if test="c.riskScore != null">risk_score = #{c.riskScore},</if>
              <if test="c.riskLevel != null">risk_level = #{c.riskLevel},</if>
              <if test="c.notes != null">notes = #{c.notes},</if>
              updated_at = CURRENT_TIMESTAMP
            </set>
            WHERE id = #{c.id}
            </script>
            """)
    int updateSelective(@Param("c") Contribution contribution);

    @Select("SELECT * FROM contribution WHERE id = #{id}")
    Contribution findById(@Param("id") long id);

    @Select("""
            <script>
            SELECT * FROM contribution
            <where>
              <if test="status != null and status != '' and status != 'all'">AND status = #{status}</if>
              <if test="kind != null and kind != '' and kind != 'all'">AND kind = #{kind}</if>
            </where>
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    java.util.List<Contribution> findPage(@Param("status") String status, @Param("kind") String kind,
                                          @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM contribution
            <where>
              <if test="status != null and status != '' and status != 'all'">AND status = #{status}</if>
              <if test="kind != null and kind != '' and kind != 'all'">AND kind = #{kind}</if>
            </where>
            </script>
            """)
    long count(@Param("status") String status, @Param("kind") String kind);

    @Select("SELECT status AS k, COUNT(*) AS c FROM contribution GROUP BY status")
    java.util.List<KeyCount> groupByStatus();
}
