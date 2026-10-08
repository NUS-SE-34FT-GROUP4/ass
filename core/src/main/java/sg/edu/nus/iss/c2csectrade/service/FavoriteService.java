package sg.edu.nus.iss.c2csectrade.service;

import sg.edu.nus.iss.c2csectrade.entity.Favorite;
import sg.edu.nus.iss.c2csectrade.mapper.FavoriteMapper;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class FavoriteService {

    @Autowired
    private FavoriteMapper favoriteMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired(required = false)
    private HybridRecommendationService hybridRecommendationService;

    public boolean addFavorite(String username, Long productId) {
        var user = userMapper.selectByUsername(username);
        if (user == null) {
            return false;
        }

        // Check whether it is already a favorite
        Favorite existing = favoriteMapper.selectByUserIdAndProductId(user.getId(), productId);
        if (existing != null) {
            return true; // Already a favorite
        }

        Favorite favorite = Favorite.builder()
                .userId(user.getId())
                .productId(productId)
                .createdAt(Instant.now())
                .build();

        favoriteMapper.insert(favorite);

        // Update product popularity
        if (hybridRecommendationService != null) {
            hybridRecommendationService.updatePopularity(productId, "favorite");
        }

        return true;
    }

    public boolean removeFavorite(String username, Long productId) {
        var user = userMapper.selectByUsername(username);
        if (user == null) {
            return false;
        }

        favoriteMapper.deleteByUserIdAndProductId(user.getId(), productId);
        return true;
    }

    public boolean isFavorite(String username, Long productId) {
        var user = userMapper.selectByUsername(username);
        if (user == null) {
            return false;
        }

        return favoriteMapper.selectByUserIdAndProductId(user.getId(), productId) != null;
    }

    public List<Long> getFavoriteProductIds(String username) {
        var user = userMapper.selectByUsername(username);
        if (user == null) {
            return List.of();
        }

        return favoriteMapper.selectProductIdsByUserId(user.getId());
    }

    public int getFavoriteCount(String username) {
        var user = userMapper.selectByUsername(username);
        if (user == null) {
            return 0;
        }

        return favoriteMapper.countByUserId(user.getId());
    }
}

