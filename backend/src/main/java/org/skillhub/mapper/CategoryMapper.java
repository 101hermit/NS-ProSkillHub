package org.skillhub.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.skillhub.model.Category;

/** 分类字典（表 {@code category}）。 */
@Mapper
public interface CategoryMapper {

    @Select("""
            SELECT code, label, kind, scene_code, sort_no, skill_count
            FROM category ORDER BY sort_no ASC, code ASC
            """)
    List<Category> findAll();

    @Select("SELECT code, label, kind, scene_code, sort_no, skill_count FROM category WHERE code = #{code}")
    Category findByCode(@Param("code") String code);

    @Insert("""
            INSERT INTO category (code, label, kind, scene_code, sort_no, skill_count)
            VALUES (#{code}, #{label}, #{kind}, #{sceneCode}, #{sortNo}, #{skillCount})
            """)
    int insert(Category category);

    @Update("UPDATE category SET label = #{label}, kind = #{kind}, scene_code = #{sceneCode}, "
            + "sort_no = #{sortNo}, skill_count = #{skillCount} WHERE code = #{code}")
    int update(Category category);

    @Update("UPDATE category SET skill_count = #{count} WHERE code = #{code}")
    int updateCount(@Param("code") String code, @Param("count") int count);

    @Select("SELECT COUNT(*) FROM category")
    int countAll();
}
