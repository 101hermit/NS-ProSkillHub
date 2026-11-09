package org.skillhub.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.skillhub.model.SkillVersion;

/** 版本快照（表 {@code skill_version}）。 */
@Mapper
public interface SkillVersionMapper {

    String META_COLS = "id, skill_id, version, notes, size_bytes, origin, created_at";

    @Insert("""
            INSERT INTO skill_version (skill_id, version, content, notes, size_bytes, origin, created_at)
            VALUES (#{skillId}, #{version}, #{content}, #{notes}, #{sizeBytes}, #{origin}, CURRENT_TIMESTAMP)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SkillVersion version);

    /** 列表用：不带 content。 */
    @Select("SELECT " + META_COLS + " FROM skill_version WHERE skill_id = #{skillId} "
            + "ORDER BY created_at DESC, id DESC")
    List<SkillVersion> findBySkillId(@Param("skillId") String skillId);

    @Select("SELECT * FROM skill_version WHERE skill_id = #{skillId} AND version = #{version}")
    SkillVersion findBySkillIdAndVersion(@Param("skillId") String skillId, @Param("version") String version);

    @Select("SELECT COUNT(*) FROM skill_version WHERE skill_id = #{skillId}")
    int countBySkillId(@Param("skillId") String skillId);

    @Select("SELECT COUNT(*) FROM skill_version")
    long countAll();

    @Select("SELECT COUNT(DISTINCT skill_id) FROM skill_version")
    long countDistinctSkills();
}
