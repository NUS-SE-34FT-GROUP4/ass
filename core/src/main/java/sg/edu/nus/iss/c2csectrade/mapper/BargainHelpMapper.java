package sg.edu.nus.iss.c2csectrade.mapper;

import sg.edu.nus.iss.c2csectrade.entity.BargainHelp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BargainHelpMapper {
    void insert(BargainHelp bargainHelp);
    List<BargainHelp> selectByBargainId(@Param("bargainId") Long bargainId);

    // Check whether a user has already helped a bargain
    BargainHelp selectByBargainIdAndHelperId(@Param("bargainId") Long bargainId, @Param("helperId") Long helperId);

    // Count the helpers of a bargain
    Integer countByBargainId(@Param("bargainId") Long bargainId);
}

