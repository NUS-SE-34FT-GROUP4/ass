package sg.edu.nus.iss.c2csectrade.controller;

import sg.edu.nus.iss.c2csectrade.dto.ProductDTO;
import sg.edu.nus.iss.c2csectrade.entity.CartItem;
import sg.edu.nus.iss.c2csectrade.service.CartService;
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
 * Cart endpoints act on the signed-in user's cart; a product that no
 * longer exists is left out of the listing.
 */
@ExtendWith(MockitoExtension.class)
class CartControllerTest {

    @Mock
    private CartService cartService;
    @Mock
    private ProductService productService;

    @InjectMocks
    private CartController cartController;

    private final Authentication buyer7 = new UsernamePasswordAuthenticationToken("buyer7", null);

    @Test
    void addingWithoutAQuantityAddsOne() {
        when(cartService.addToCart("buyer7", 42L, 1)).thenReturn(true);

        assertThat((Map<Object, Object>) cartController.addToCart(Map.of("productId", 42), buyer7).getBody())
                .containsEntry("success", true).containsEntry("message", "Added to cart");
    }

    @Test
    void aFailedAddSaysSo() {
        when(cartService.addToCart("buyer7", 42L, 3)).thenReturn(false);

        assertThat((Map<Object, Object>) cartController.addToCart(Map.of("productId", "42", "quantity", "3"), buyer7).getBody())
                .containsEntry("success", false).containsEntry("message", "Failed to add to cart");
    }

    @Test
    void theQuantityIsUpdated() {
        when(cartService.updateCartItemQuantity("buyer7", 42L, 4)).thenReturn(true);
        when(cartService.updateCartItemQuantity("buyer7", 43L, 4)).thenReturn(false);

        assertThat((Map<Object, Object>) cartController.updateCartItem(Map.of("productId", 42, "quantity", 4), buyer7).getBody())
                .containsEntry("message", "Quantity updated");
        assertThat((Map<Object, Object>) cartController.updateCartItem(Map.of("productId", 43, "quantity", 4), buyer7).getBody())
                .containsEntry("message", "Update failed");
    }

    @Test
    void aProductIsRemoved() {
        when(cartService.removeFromCart("buyer7", 42L)).thenReturn(true);
        when(cartService.removeFromCart("buyer7", 43L)).thenReturn(false);

        assertThat((Map<Object, Object>) cartController.removeFromCart(42L, buyer7).getBody()).containsEntry("message", "Removed from cart");
        assertThat((Map<Object, Object>) cartController.removeFromCart(43L, buyer7).getBody()).containsEntry("message", "Remove failed");
    }

    @Test
    void theListingSkipsProductsThatNoLongerExist() {
        CartItem lamp = line(1L, 42L, 2);
        CartItem gone = line(2L, 43L, 1);
        when(cartService.getCartItems("buyer7")).thenReturn(List.of(lamp, gone));
        ProductDTO product = new ProductDTO();
        when(productService.getProductDTOById(42L)).thenReturn(product);
        when(productService.getProductDTOById(43L)).thenReturn(null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) cartController.getCartItems(buyer7).getBody();

        assertThat(items).hasSize(1);
        assertThat(items.get(0)).containsEntry("cartItemId", 1L).containsEntry("quantity", 2).containsEntry("product", product);
    }

    @Test
    void theCountIsReturned() {
        when(cartService.getCartCount("buyer7")).thenReturn(3);

        assertThat((Map<Object, Object>) cartController.getCartCount(buyer7).getBody()).containsEntry("count", 3);
    }

    private static CartItem line(Long id, Long productId, int quantity) {
        CartItem item = new CartItem();
        item.setId(id);
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }
}
