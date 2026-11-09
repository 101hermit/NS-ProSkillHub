package org.skillhub.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.skillhub.model.DocPage;

/** 在线文档（表 {@code doc_page}）。 */
@Mapper
public interface DocMapper {

    @Select("""
            SELECT id, doc_key, title, section, summary, sort_no, updated_at
            FROM doc_page ORDER BY sort_no ASC, doc_key ASC
            """)
    List<DocPage> findCatalog();

    @Select("SELECT * FROM doc_page WHERE doc_key = #{docKey}")
    DocPage findByKey(@Param("docKey") String docKey);

    @Insert("""
            INSERT INTO doc_page (doc_key, title, section, summary, body, sort_no, updated_at)
            VALUES (#{docKey}, #{title}, #{section}, #{summary}, #{body}, #{sortNo}, CURRENT_TIMESTAMP)
            """)
    int insert(DocPage page);

    @Update("""
            UPDATE doc_page SET title = #{title}, section = #{section}, summary = #{summary},
              body = #{body}, sort_no = #{sortNo}, updated_at = CURRENT_TIMESTAMP WHERE doc_key = #{docKey}
            """)
    int update(DocPage page);

    @Select("SELECT COUNT(*) FROM doc_page")
    long countAll();
}
