package sg.edu.nus.iss.c2csectrade.service;

import sg.edu.nus.iss.c2csectrade.entity.Favorite;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.FavoriteMapper;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A favourite is one row per user and product; adding it twice is a no-op.
 * Buyer 7 is signed in as "buyer7".
 */
@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    private FavoriteMapper favoriteMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private HybridRecommendationService hybridRecommendationService;

    @InjectMocks
    private FavoriteService favoriteService;

    @Test
    void aNewFavouriteIsSavedAndCountsTowardsPopularity() {
        signedIn();

        assertThat(favoriteService.addFavorite("buyer7", 42L)).isTrue();

        ArgumentCaptor<Favorite> saved = ArgumentCaptor.forClass(Favorite.class);
        verify(favoriteMapper).insert(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(7L);
        assertThat(saved.getValue().getProductId()).isEqualTo(42L);
        verify(hybridRecommendationService).updatePopularity(42L, "favorite");
    }

    @Test
    void anExistingFavouriteIsNotSavedAgain() {
        signedIn();
        when(favoriteMapper.selectByUserIdAndProductId(7L, 42L)).thenReturn(new Favorite());

        assertThat(favoriteService.addFavorite("buyer7", 42L)).isTrue();
        verify(favoriteMapper, never()).insert(any());
    }

    @Test
    void aFavouriteIsRemovedChecksAndCounted() {
        signedIn();
        when(favoriteMapper.selectByUserIdAndProductId(7L, 42L)).thenReturn(new Favorite());
        when(favoriteMapper.selectByUserIdAndProductId(7L, 43L)).thenReturn(null);
        when(favoriteMapper.selectProductIdsByUserId(7L)).thenReturn(List.of(42L));
        when(favoriteMapper.countByUserId(7L)).thenReturn(1);

        assertThat(favoriteService.removeFavorite("buyer7", 42L)).isTrue();
        verify(favoriteMapper).deleteByUserIdAndProductId(7L, 42L);
        assertThat(favoriteService.isFavorite("buyer7", 42L)).isTrue();
        assertThat(favoriteService.isFavorite("buyer7", 43L)).isFalse();
        assertThat(favoriteService.getFavoriteProductIds("buyer7")).containsExactly(42L);
        assertThat(favoriteService.getFavoriteCount("buyer7")).isEqualTo(1);
    }

    @Test
    void anUnknownUserHasNoFavourites() {
        when(userMapper.selectByUsername("ghost")).thenReturn(null);

        assertThat(favoriteService.addFavorite("ghost", 42L)).isFalse();
        assertThat(favoriteService.removeFavorite("ghost", 42L)).isFalse();
        assertThat(favoriteService.isFavorite("ghost", 42L)).isFalse();
        assertThat(favoriteService.getFavoriteProductIds("ghost")).isEmpty();
        assertThat(favoriteService.getFavoriteCount("ghost")).isZero();
        verifyNoInteractions(favoriteMapper);
    }

    private void signedIn() {
        User user = new User();
        user.setId(7L);
        when(userMapper.selectByUsername("buyer7")).thenReturn(user);
    }
}
