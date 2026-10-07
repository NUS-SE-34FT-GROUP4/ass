package lut.cn.c2cplatform.mapper;

import lut.cn.c2cplatform.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface UserMapper {
    User selectById(@Param("id") Long id);
    User selectByUsername(@Param("username") String username);
    // Load a user with roles
    User selectByUsernameWithRoles(@Param("username") String username);
    // Load all users (with roles)
    List<User> selectAllWithRoles();
    List<User> selectAll();
    int insert(User user);
    int update(User user);
    int deleteById(@Param("id") Long id);
}
