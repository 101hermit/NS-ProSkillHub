package org.skillhub.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.skillhub.model.Contributor;

/** 贡献者档案（表 {@code contributor}）。 */
@Mapper
public interface ContributorMapper {

    @Insert("""
            INSERT INTO contributor (source, author, name, kind, description, git_profile, created_at)
            VALUES (#{source}, #{author}, #{name}, #{kind}, #{description}, #{gitProfile}, CURRENT_TIMESTAMP)
            """)
    int insert(Contributor contributor);

    @Update("""
            UPDATE contributor SET name = #{name}, kind = #{kind}, description = #{description},
              git_profile = #{gitProfile} WHERE source = #{source} AND author = #{author}
            """)
    int update(Contributor contributor);

    @Select("SELECT * FROM contributor WHERE source = #{source} AND author = #{author}")
    Contributor findOne(@Param("source") String source, @Param("author") String author);

    @Select("""
            <script>
            SELECT * FROM contributor
            <where>
              <if test="kind != null and kind != '' and kind != 'all'">AND kind = #{kind}</if>
            </where>
            ORDER BY name ASC, author ASC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Contributor> findPage(@Param("kind") String kind, @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM contributor
            <where>
              <if test="kind != null and kind != '' and kind != 'all'">AND kind = #{kind}</if>
            </where>
            </script>
            """)
    long count(@Param("kind") String kind);

    @Select("SELECT kind AS k, COUNT(*) AS c FROM contributor GROUP BY kind")
    List<KeyCount> groupByKind();

    @Select("SELECT COUNT(*) FROM contributor")
    long countAll();
}
