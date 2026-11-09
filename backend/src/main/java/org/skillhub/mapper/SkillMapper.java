package org.skillhub.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.skillhub.dto.SkillQuery;
import org.skillhub.model.Skill;

/**
 * Skill 主表访问（表 {@code skill}）。
 *
 * <p>命名约定：
 * <ul>
 *   <li>{@code findCard*} / {@code list*} —— <b>不带</b> {@code content}（列表页用，避免拉 40 KB 大字段）</li>
 *   <li>{@code findDetailBySkillId} —— <b>带</b> {@code content}（详情页用）</li>
 * </ul>
 */
@Mapper
public interface SkillMapper {

    /** 卡片列（不含 content）。 */
    String CARD_COLS = "skill_id, slug, name, title, description, category_code, scene_code, tags, version, "
            + "source, source_url, source_repo, license, author, author_kind, risk_score, risk_level, status, "
            + "content_bytes, file_count, package_bytes, content_sha256, download_count, install_count, "
            + "star_count, eval_count, avg_score, skillset_code, created_at, updated_at";

    // ------------------------------------------------------------------ 写

    @Insert("""
            INSERT INTO skill (skill_id, slug, name, title, description, category_code, scene_code, tags,
                version, source, source_url, source_repo, license, author, author_kind, risk_score, risk_level,
                status, content, content_bytes, file_count, package_bytes, content_sha256, skillset_code,
                created_at, updated_at)
            VALUES (#{skillId}, #{slug}, #{name}, #{title}, #{description}, #{categoryCode}, #{sceneCode},
                #{tags}, #{version}, #{source}, #{sourceUrl}, #{sourceRepo}, #{license}, #{author},
                #{authorKind}, #{riskScore}, #{riskLevel}, #{status}, #{content}, #{contentBytes},
                #{fileCount}, #{packageBytes}, #{contentSha256}, #{skillsetCode},
                #{createdAt}, #{updatedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Skill skill);

    /** 编辑：只更新非 null 字段（null 表示"不改"）。 */
    @Update("""
            <script>
            UPDATE skill
            <set>
              <if test="s.name != null">name = #{s.name},</if>
              <if test="s.title != null">title = #{s.title},</if>
              <if test="s.description != null">description = #{s.description},</if>
              <if test="s.categoryCode != null">category_code = #{s.categoryCode},</if>
              <if test="s.sceneCode != null">scene_code = #{s.sceneCode},</if>
              <if test="s.tags != null">tags = #{s.tags},</if>
              <if test="s.version != null">version = #{s.version},</if>
              <if test="s.source != null">source = #{s.source},</if>
              <if test="s.sourceUrl != null">source_url = #{s.sourceUrl},</if>
              <if test="s.sourceRepo != null">source_repo = #{s.sourceRepo},</if>
              <if test="s.license != null">license = #{s.license},</if>
              <if test="s.author != null">author = #{s.author},</if>
              <if test="s.authorKind != null">author_kind = #{s.authorKind},</if>
              <if test="s.riskScore != null">risk_score = #{s.riskScore},</if>
              <if test="s.riskLevel != null">risk_level = #{s.riskLevel},</if>
              <if test="s.status != null">status = #{s.status},</if>
              <if test="s.content != null">content = #{s.content},</if>
              <if test="s.contentBytes != null">content_bytes = #{s.contentBytes},</if>
              <if test="s.contentSha256 != null">content_sha256 = #{s.contentSha256},</if>
              <if test="s.skillsetCode != null">skillset_code = #{s.skillsetCode},</if>
              updated_at = CURRENT_TIMESTAMP
            </set>
            WHERE skill_id = #{s.skillId}
            </script>
            """)
    int updateSelective(@Param("s") Skill skill);

    @Update("UPDATE skill SET status = #{status}, updated_at = CURRENT_TIMESTAMP WHERE skill_id = #{skillId}")
    int updateStatus(@Param("skillId") String skillId, @Param("status") String status);

    @Update("UPDATE skill SET download_count = download_count + 1 WHERE skill_id = #{skillId}")
    int incrementDownload(@Param("skillId") String skillId);

    @Update("UPDATE skill SET install_count = install_count + 1 WHERE skill_id = #{skillId}")
    int incrementInstall(@Param("skillId") String skillId);

    @Update("UPDATE skill SET eval_count = eval_count + 1, avg_score = #{avg} WHERE skill_id = #{skillId}")
    int updateEvalAggregate(@Param("skillId") String skillId, @Param("avg") BigDecimal avg);

    @Delete("DELETE FROM skill WHERE skill_id = #{skillId}")
    int deleteHard(@Param("skillId") String skillId);

    // ------------------------------------------------------------------ 读（卡片）

    @Select("SELECT " + CARD_COLS + " FROM skill WHERE skill_id = #{skillId}")
    Skill findCardBySkillId(@Param("skillId") String skillId);

    @Select("SELECT * FROM skill WHERE skill_id = #{skillId}")
    Skill findDetailBySkillId(@Param("skillId") String skillId);

    @Select("SELECT " + CARD_COLS + " FROM skill WHERE slug = #{slug}")
    List<Skill> findCardBySlug(@Param("slug") String slug);

    @Select("""
            <script>
            SELECT
            """ + " " + CARD_COLS + " " + """
            FROM skill
            <where>
              <if test="q.statusFilter != null">AND status = #{q.statusFilter}</if>
              <if test="q.category != null and q.category != ''">AND category_code = #{q.category}</if>
              <if test="q.scene != null and q.scene != ''">AND scene_code = #{q.scene}</if>
              <if test="q.riskLevel != null and q.riskLevel != ''">AND risk_level = #{q.riskLevel}</if>
              <if test="q.authorKind != null and q.authorKind != ''">AND author_kind = #{q.authorKind}</if>
              <if test="q.source != null and q.source != ''">AND source = #{q.source}</if>
              <if test="q.likeKw != null">AND (name LIKE #{q.likeKw} OR title LIKE #{q.likeKw}
                    OR description LIKE #{q.likeKw} OR tags LIKE #{q.likeKw})</if>
            </where>
            ORDER BY ${q.orderBy}
            LIMIT #{q.limit} OFFSET #{q.offset}
            </script>
            """)
    List<Skill> listCards(@Param("q") SkillQuery q);

    @Select("""
            <script>
            SELECT COUNT(*) FROM skill
            <where>
              <if test="q.statusFilter != null">AND status = #{q.statusFilter}</if>
              <if test="q.category != null and q.category != ''">AND category_code = #{q.category}</if>
              <if test="q.scene != null and q.scene != ''">AND scene_code = #{q.scene}</if>
              <if test="q.riskLevel != null and q.riskLevel != ''">AND risk_level = #{q.riskLevel}</if>
              <if test="q.authorKind != null and q.authorKind != ''">AND author_kind = #{q.authorKind}</if>
              <if test="q.source != null and q.source != ''">AND source = #{q.source}</if>
              <if test="q.likeKw != null">AND (name LIKE #{q.likeKw} OR title LIKE #{q.likeKw}
                    OR description LIKE #{q.likeKw} OR tags LIKE #{q.likeKw})</if>
            </where>
            </script>
            """)
    long countCards(@Param("q") SkillQuery q);

    /** 检索候选集（正文也参与匹配），由业务层做加权打分与排序。 */
    @Select("""
            SELECT
            """ + " " + CARD_COLS + " " + """
            FROM skill
            WHERE status = 'published'
              AND (name LIKE #{kw} OR title LIKE #{kw} OR description LIKE #{kw}
                   OR tags LIKE #{kw} OR content LIKE #{kw})
            ORDER BY download_count DESC
            LIMIT #{limit}
            """)
    List<Skill> searchCandidates(@Param("kw") String likeKw, @Param("limit") int limit);

    /** 一批 Skill（用于贡献者详情、相关推荐）。 */
    @Select("""
            <script>
            SELECT
            """ + " " + CARD_COLS + " " + """
            FROM skill WHERE skill_id IN
            <foreach item="id" collection="ids" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY updated_at DESC
            </script>
            """)
    List<Skill> findCardsByIds(@Param("ids") List<String> ids);

    /** 同分类的其它 Skill（详情页"相关技能"）。 */
    @Select("""
            SELECT
            """ + " " + CARD_COLS + " " + """
            FROM skill
            WHERE status = 'published' AND category_code = #{category} AND skill_id <> #{excludeId}
            ORDER BY download_count DESC, updated_at DESC
            LIMIT #{limit}
            """)
    List<Skill> findRelated(@Param("category") String category,
                            @Param("excludeId") String excludeId,
                            @Param("limit") int limit);

    // ------------------------------------------------------------------ 聚合

    @Select("SELECT COUNT(*) FROM skill")
    long countAll();

    @Select("SELECT COUNT(*) FROM skill WHERE status = #{status}")
    long countByStatus(@Param("status") String status);

    @Select("SELECT COALESCE(SUM(download_count), 0) FROM skill")
    long sumDownloads();

    @Select("SELECT COUNT(*) FROM skill WHERE risk_level = #{level}")
    long countByRiskLevel(@Param("level") String level);

    @Select("SELECT MAX(updated_at) FROM skill")
    String maxUpdatedAt();

    @Select("SELECT risk_level AS k, COUNT(*) AS c FROM skill GROUP BY risk_level")
    List<KeyCount> groupByRiskLevel();

    @Select("SELECT category_code AS k, COUNT(*) AS c FROM skill GROUP BY category_code")
    List<KeyCount> groupByCategory();

    @Select("SELECT scene_code AS k, COUNT(*) AS c FROM skill GROUP BY scene_code")
    List<KeyCount> groupByScene();

    @Select("SELECT source AS k, COUNT(*) AS c FROM skill GROUP BY source")
    List<KeyCount> groupBySource();

    @Select("SELECT author_kind AS k, COUNT(*) AS c FROM skill GROUP BY author_kind")
    List<KeyCount> groupByAuthorKind();

    /** 按作者聚合 Skill 数（贡献者列表用；在 Java 侧与 {@code contributor} 表做关联）。 */
    @Select("SELECT author AS k, COUNT(*) AS c FROM skill WHERE author <> '' GROUP BY author")
    List<KeyCount> groupCountByAuthor();

    @Select("SELECT COUNT(*) FROM skill WHERE category_code = #{code}")
    long countByCategoryCode(@Param("code") String code);

    @Select("SELECT author AS k, COALESCE(SUM(download_count), 0) AS c FROM skill "
            + "WHERE author <> '' GROUP BY author")
    List<KeyCount> groupDownloadsByAuthor();

    @Select("""
            SELECT
            """ + " " + CARD_COLS + " " + """
            FROM skill WHERE author = #{author} ORDER BY updated_at DESC LIMIT #{limit}
            """)
    List<Skill> findCardsByAuthor(@Param("author") String author, @Param("limit") int limit);
    /** 排除 {@code exclude} 后，某列等于某值的行数（约束 UNIQUE 用；列名由业务层白名单保证）。 */
    @Select("""
            <script>
            SELECT COUNT(*) FROM skill WHERE ${column} = #{value}
            <if test="exclude != null and exclude != ''">AND skill_id &lt;&gt; #{exclude}</if>
            </script>
            """)
    long countByColumn(@Param("column") String column, @Param("value") String value,
                       @Param("exclude") String exclude);

    @Select("SELECT COUNT(*) FROM skillset WHERE ${column} = #{value}")
    long countSkillsetByColumn(@Param("column") String column, @Param("value") String value);

    @Select("SELECT COUNT(*) FROM skill WHERE skillset_code = #{code}")
    long countBySkillset(@Param("code") String code);

    @Update("UPDATE skill SET skillset_code = #{code}, updated_at = CURRENT_TIMESTAMP WHERE skill_id = #{skillId}")
    int updateSkillset(@Param("skillId") String skillId, @Param("code") String code);

    /** 跳过正文的全量行（索引重建用）。 */
    @Select("SELECT " + CARD_COLS + " FROM skill ORDER BY skill_id ASC")
    List<Skill> listAllCards();
}