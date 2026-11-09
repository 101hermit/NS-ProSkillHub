package org.skillhub.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.skillhub.model.SkillFile;

/** Skill 附件清单（表 {@code skill_file}）。 */
@Mapper
public interface SkillFileMapper {

    @Insert("""
            <script>
            INSERT INTO skill_file (skill_id, path, bytes, sha256, is_primary, created_at) VALUES
            <foreach item="f" collection="files" separator=",">
              (#{f.skillId}, #{f.path}, #{f.bytes}, #{f.sha256}, #{f.isPrimary}, CURRENT_TIMESTAMP)
            </foreach>
            </script>
            """)
    int insertBatch(@Param("files") List<SkillFile> files);

    @Select("""
            SELECT id, skill_id, path, bytes, sha256, is_primary, created_at
            FROM skill_file WHERE skill_id = #{skillId}
            ORDER BY is_primary DESC, path ASC
            """)
    List<SkillFile> findBySkillId(@Param("skillId") String skillId);

    @Delete("DELETE FROM skill_file WHERE skill_id = #{skillId}")
    int deleteBySkillId(@Param("skillId") String skillId);
}
