package sg.edu.nus.iss.c2csectrade.service;

import sg.edu.nus.iss.c2csectrade.entity.CartItem;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.CartItemMapper;
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
 * The cart holds one line per product; it is what a cart order is built
 * from. Buyer 7 is signed in as "buyer7".
 */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartItemMapper cartItemMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private HybridRecommendationService hybridRecommendationService;

    @InjectMocks
    private CartService cartService;

    @Test
    void addingANewProductCreatesACartLine() {
        signedIn();
        when(cartItemMapper.selectByUserIdAndProductId(7L, 42L)).thenReturn(null);

        assertThat(cartService.addToCart("buyer7", 42L, 2)).isTrue();

        ArgumentCaptor<CartItem> added = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemMapper).insert(added.capture());
        assertThat(added.getValue().getUserId()).isEqualTo(7L);
        assertThat(added.getValue().getQuantity()).isEqualTo(2);
        verify(hybridRecommendationService).updatePopularity(42L, "cart");
    }

    @Test
    void addingAProductAlreadyInTheCartRaisesItsQuantity() {
        signedIn();
        CartItem line = line(1);
        when(cartItemMapper.selectByUserIdAndProductId(7L, 42L)).thenReturn(line);

        cartService.addToCart("buyer7", 42L, 2);

        assertThat(line.getQuantity()).isEqualTo(3);
        verify(cartItemMapper).update(line);
        verify(cartItemMapper, never()).insert(any());
    }

    @Test
    void theQuantityOfACartLineCanBeChanged() {
        signedIn();
        CartItem line = line(1);
        when(cartItemMapper.selectByUserIdAndProductId(7L, 42L)).thenReturn(line);

        assertThat(cartService.updateCartItemQuantity("buyer7", 42L, 4)).isTrue();

        assertThat(line.getQuantity()).isEqualTo(4);
        verify(cartItemMapper).update(line);
    }

    @Test
    void aProductNotInTheCartCannotHaveItsQuantityChanged() {
        signedIn();
        when(cartItemMapper.selectByUserIdAndProductId(7L, 42L)).thenReturn(null);

        assertThat(cartService.updateCartItemQuantity("buyer7", 42L, 4)).isFalse();
        verify(cartItemMapper, never()).update(any());
    }

    @Test
    void removingAProductDeletesItsLine() {
        signedIn();

        assertThat(cartService.removeFromCart("buyer7", 42L)).isTrue();
        verify(cartItemMapper).deleteByUserIdAndProductId(7L, 42L);
    }

    @Test
    void theCartIsListedAndCounted() {
        signedIn();
        List<CartItem> lines = List.of(line(1));
        when(cartItemMapper.selectByUserId(7L)).thenReturn(lines);
        when(cartItemMapper.countByUserId(7L)).thenReturn(1);

        assertThat(cartService.getCartItems("buyer7")).isSameAs(lines);
        assertThat(cartService.getCartCount("buyer7")).isEqualTo(1);
    }

    @Test
    void anUnknownUserHasAnEmptyCartAndCannotChangeIt() {
        when(userMapper.selectByUsername("ghost")).thenReturn(null);

        assertThat(cartService.addToCart("ghost", 42L, 1)).isFalse();
        assertThat(cartService.updateCartItemQuantity("ghost", 42L, 1)).isFalse();
        assertThat(cartService.removeFromCart("ghost", 42L)).isFalse();
        assertThat(cartService.getCartItems("ghost")).isEmpty();
        assertThat(cartService.getCartCount("ghost")).isZero();
        verifyNoInteractions(cartItemMapper);
    }

    private void signedIn() {
        User user = new User();
        user.setId(7L);
        user.setUsername("buyer7");
        when(userMapper.selectByUsername("buyer7")).thenReturn(user);
    }

    private static CartItem line(int quantity) {
        CartItem line = new CartItem();
        line.setUserId(7L);
        line.setProductId(42L);
        line.setQuantity(quantity);
        return line;
    }
}
