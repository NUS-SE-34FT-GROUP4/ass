package sg.edu.nus.iss.c2csectrade.mapper;

import sg.edu.nus.iss.c2csectrade.entity.User;
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
    int updateEnabled(@Param("id") Long id, @Param("enabled") boolean enabled);
    int deleteById(@Param("id") Long id);
}
