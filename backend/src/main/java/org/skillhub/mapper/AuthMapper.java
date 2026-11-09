package org.skillhub.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.skillhub.model.AuthEntities;

/** 账号与令牌（表 {@code user_account} / {@code auth_token}）。 */
@Mapper
public interface AuthMapper {

    @Select("SELECT * FROM user_account WHERE username = #{username}")
    AuthEntities.UserAccount findByUsername(@Param("username") String username);

    @Select("SELECT COUNT(*) FROM user_account")
    long countAccounts();

    @Insert("""
            INSERT INTO user_account (username, display_name, password_hash, password_salt, role, status,
                created_at)
            VALUES (#{username}, #{displayName}, #{passwordHash}, #{passwordSalt}, #{role}, 'active',
                CURRENT_TIMESTAMP)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertAccount(AuthEntities.UserAccount account);

    @Update("UPDATE user_account SET last_login_at = CURRENT_TIMESTAMP WHERE username = #{username}")
    int touchLogin(@Param("username") String username);

    @Update("UPDATE user_account SET password_hash = #{hash}, password_salt = #{salt} WHERE username = #{username}")
    int updatePassword(@Param("username") String username, @Param("hash") String hash,
                       @Param("salt") String salt);

    @Insert("""
            INSERT INTO auth_token (token, username, role, expires_at, created_at)
            VALUES (#{token}, #{username}, #{role}, #{expiresAt}, CURRENT_TIMESTAMP)
            """)
    int insertToken(AuthEntities.AuthToken row);

    @Select("SELECT * FROM auth_token WHERE token = #{token}")
    AuthEntities.AuthToken findToken(@Param("token") String token);

    @Delete("DELETE FROM auth_token WHERE token = #{token}")
    int deleteToken(@Param("token") String token);

    @Delete("DELETE FROM auth_token WHERE username = #{username}")
    int deleteTokensOf(@Param("username") String username);

    /** 清理过期令牌（每次登录顺手做一次，避免表无限增长）。 */
    @Delete("DELETE FROM auth_token WHERE expires_at < CURRENT_TIMESTAMP")
    int purgeExpired();
}
