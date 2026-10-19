package sg.edu.nus.iss.c2csectrade.controller;

import sg.edu.nus.iss.c2csectrade.dto.ProductDTO;
import sg.edu.nus.iss.c2csectrade.service.FavoriteService;
import sg.edu.nus.iss.c2csectrade.service.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Favourite endpoints act on the signed-in user; a favourite whose
 * product no longer exists is left out of the list.
 */
@ExtendWith(MockitoExtension.class)
class FavoriteControllerTest {

    @Mock
    private FavoriteService favoriteService;
    @Mock
    private ProductService productService;

    @InjectMocks
    private FavoriteController favoriteController;

    private final Authentication buyer7 = new UsernamePasswordAuthenticationToken("buyer7", null);

    @Test
    void addingAndRemovingReportTheOutcome() {
        when(favoriteService.addFavorite("buyer7", 42L)).thenReturn(true);
        when(favoriteService.addFavorite("buyer7", 43L)).thenReturn(false);
        when(favoriteService.removeFavorite("buyer7", 42L)).thenReturn(true);
        when(favoriteService.removeFavorite("buyer7", 43L)).thenReturn(false);

        assertThat((Map<Object, Object>) favoriteController.addFavorite(42L, buyer7).getBody()).containsEntry("message", "Added to favorites");
        assertThat((Map<Object, Object>) favoriteController.addFavorite(43L, buyer7).getBody()).containsEntry("success", false);
        assertThat((Map<Object, Object>) favoriteController.removeFavorite(42L, buyer7).getBody()).containsEntry("message", "Removed from favorites");
        assertThat((Map<Object, Object>) favoriteController.removeFavorite(43L, buyer7).getBody()).containsEntry("success", false);
    }

    @Test
    void theCheckAndCountAreReturned() {
        when(favoriteService.isFavorite("buyer7", 42L)).thenReturn(true);
        when(favoriteService.getFavoriteCount("buyer7")).thenReturn(2);

        assertThat((Map<Object, Object>) favoriteController.checkFavorite(42L, buyer7).getBody()).containsEntry("isFavorite", true);
        assertThat((Map<Object, Object>) favoriteController.getFavoriteCount(buyer7).getBody()).containsEntry("count", 2);
    }

    @Test
    void theListSkipsProductsThatNoLongerExist() {
        ProductDTO lamp = new ProductDTO();
        when(favoriteService.getFavoriteProductIds("buyer7")).thenReturn(List.of(42L, 43L));
        when(productService.getProductDTOById(42L)).thenReturn(lamp);
        when(productService.getProductDTOById(43L)).thenReturn(null);

        assertThat((List<Object>) favoriteController.getFavorites(buyer7).getBody()).containsExactly(lamp);
    }
}
