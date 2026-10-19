package sg.edu.nus.iss.c2csectrade.service;

import sg.edu.nus.iss.c2csectrade.entity.BargainActivity;
import sg.edu.nus.iss.c2csectrade.entity.BargainHelp;
import sg.edu.nus.iss.c2csectrade.entity.Order;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.mapper.BargainActivityMapper;
import sg.edu.nus.iss.c2csectrade.mapper.BargainHelpMapper;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A bargain runs for 24 hours towards a random target of 60-80% of the
 * price. Each helper cuts 5-20% of what is left, at least one cent, and
 * never past the target. User 7 bargains for product 42 (price 100).
 */
@ExtendWith(MockitoExtension.class)
class BargainServiceTest {

    private static final long DAY = 24 * 60 * 60 * 1000L;

    @Mock
    private BargainActivityMapper bargainActivityMapper;
    @Mock
    private BargainHelpMapper bargainHelpMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private OrderService orderService;

    @InjectMocks
    private BargainService bargainService;

    @Test
    void aBargainStartsAtThePriceWithATargetOf60To80Percent() {
        when(productMapper.selectById(42L)).thenReturn(product(1));
        doAnswer(call -> {
            call.<BargainActivity>getArgument(0).setId(5L);
            return null;
        }).when(bargainActivityMapper).insert(any());
        ArgumentCaptor<BargainActivity> started = ArgumentCaptor.forClass(BargainActivity.class);

        bargainService.startBargain(7L, 42L);

        verify(bargainActivityMapper).insert(started.capture());
        BargainActivity bargain = started.getValue();
        assertThat(bargain.getStatus()).isEqualTo("ACTIVE");
        assertThat(bargain.getCurrentPrice()).isEqualByComparingTo("100");
        assertThat(bargain.getTargetPrice()).isBetween(new BigDecimal("60"), new BigDecimal("80"));
        assertThat(bargain.getExpireTime()).isAfter(new Date(System.currentTimeMillis() + DAY - 60_000));
        verify(bargainActivityMapper).selectById(5L);
    }

    @Test
    void aBargainNeedsAProductAndOnlyOneRunsAtATime() {
        when(productMapper.selectById(42L)).thenReturn(null).thenReturn(product(1));
        when(bargainActivityMapper.selectActiveByUserAndProduct(7L, 42L)).thenReturn(new BargainActivity());

        assertThatThrownBy(() -> bargainService.startBargain(7L, 42L)).hasMessageContaining("Product not found");
        assertThatThrownBy(() -> bargainService.startBargain(7L, 42L)).hasMessageContaining("already started");
        verify(bargainActivityMapper, never()).insert(any());
    }

    @Test
    void aHelperCutsPartOfWhatIsLeft() {
        BargainActivity bargain = bargain("ACTIVE", "100", "60", DAY);
        when(bargainActivityMapper.selectById(5L)).thenReturn(bargain);
        when(productMapper.selectById(42L)).thenReturn(product(1));

        BargainHelp help = bargainService.helpBargain(5L, 9L, "seller9");

        // 5-20% of the 40 still to go
        assertThat(help.getCutAmount()).isBetween(new BigDecimal("2.00"), new BigDecimal("8.00"));
        assertThat(bargain.getCurrentPrice()).isEqualByComparingTo(new BigDecimal("100").subtract(help.getCutAmount()));
        assertThat(bargain.getStatus()).isEqualTo("ACTIVE");
        verify(bargainHelpMapper).insert(help);
        verify(bargainActivityMapper).update(bargain);
    }

    @Test
    void theLastCentReachesTheTargetAndSucceeds() {
        BargainActivity bargain = bargain("ACTIVE", "60.01", "60", DAY);
        when(bargainActivityMapper.selectById(5L)).thenReturn(bargain);
        when(productMapper.selectById(42L)).thenReturn(product(1));

        BargainHelp help = bargainService.helpBargain(5L, null, "guest");

        assertThat(help.getCutAmount()).isEqualByComparingTo("0.01");
        assertThat(bargain.getCurrentPrice()).isEqualByComparingTo("60");
        assertThat(bargain.getStatus()).isEqualTo("SUCCESS");
        verify(bargainHelpMapper, never()).selectByBargainIdAndHelperId(any(), any());
    }

    @Test
    void aHelperCannotHelpTwiceOrPastTheTarget() {
        when(bargainActivityMapper.selectById(5L)).thenReturn(bargain("ACTIVE", "100", "60", DAY));
        when(bargainActivityMapper.selectById(6L)).thenReturn(bargain("ACTIVE", "60", "60", DAY));
        when(productMapper.selectById(42L)).thenReturn(product(1));
        when(bargainHelpMapper.selectByBargainIdAndHelperId(5L, 9L)).thenReturn(new BargainHelp());

        assertThatThrownBy(() -> bargainService.helpBargain(5L, 9L, "seller9")).hasMessageContaining("already helped");
        assertThatThrownBy(() -> bargainService.helpBargain(6L, null, "guest")).hasMessageContaining("already complete");
        verify(bargainHelpMapper, never()).insert(any());
    }

    @Test
    void helpingAMissingOrExpiredBargainFails() {
        when(bargainActivityMapper.selectById(4L)).thenReturn(null);
        when(bargainActivityMapper.selectById(5L)).thenReturn(bargain("ACTIVE", "100", "60", -1000));

        assertThatThrownBy(() -> bargainService.helpBargain(4L, 9L, "x")).hasMessageContaining("not found");
        assertThatThrownBy(() -> bargainService.helpBargain(5L, 9L, "x")).hasMessageContaining("expired");
        verify(bargainActivityMapper).updateStatus(5L, "EXPIRED");
    }

    @ParameterizedTest
    @CsvSource({"EXPIRED, has expired", "SUCCESS, has ended"})
    void onlyAnActiveBargainCanBeHelped(String status, String message) {
        when(bargainActivityMapper.selectById(5L)).thenReturn(bargain(status, "100", "60", DAY));

        assertThatThrownBy(() -> bargainService.helpBargain(5L, 9L, "x")).hasMessageContaining(message);
    }

    @Test
    void aBargainFailsWhenTheProductIsGoneOrSoldOut() {
        when(bargainActivityMapper.selectById(5L)).thenReturn(bargain("ACTIVE", "100", "60", DAY));
        when(productMapper.selectById(42L)).thenReturn(null).thenReturn(product(0));

        assertThatThrownBy(() -> bargainService.helpBargain(5L, 9L, "x")).hasMessageContaining("delisted");
        assertThatThrownBy(() -> bargainService.helpBargain(5L, 9L, "x")).hasMessageContaining("sold out");
        verify(bargainActivityMapper, org.mockito.Mockito.times(2)).updateStatus(5L, "FAILED");
    }

    @Test
    void bargainsAndHelpersAreListedAndExpiredOnesClosed() {
        BargainActivity bargain = bargain("ACTIVE", "100", "60", DAY);
        when(bargainActivityMapper.selectById(5L)).thenReturn(bargain);
        when(bargainActivityMapper.selectByUserId(7L)).thenReturn(List.of(bargain));
        when(bargainHelpMapper.selectByBargainId(5L)).thenReturn(List.of(new BargainHelp()));
        BargainActivity stale = bargain("ACTIVE", "100", "60", -1000);
        stale.setId(8L);
        when(bargainActivityMapper.selectExpiredActivities()).thenReturn(List.of(stale));

        assertThat(bargainService.getBargainActivity(5L)).isSameAs(bargain);
        assertThat(bargainService.getUserBargainActivities(7L)).containsExactly(bargain);
        assertThat(bargainService.getBargainHelpList(5L)).hasSize(1);
        bargainService.closeExpiredBargains();
        verify(bargainActivityMapper).updateStatus(8L, "EXPIRED");
    }

    @Test
    void givingUpBuysAtTheCurrentPrice() {
        BargainActivity bargain = bargain("ACTIVE", "90", "60", DAY);
        when(bargainActivityMapper.selectById(5L)).thenReturn(bargain);
        when(productMapper.selectByIdForUpdate(42L)).thenReturn(product(1));
        when(orderService.createBargainOrder(7, 42L, 1, new BigDecimal("90"))).thenReturn(order("90"));

        Map<String, Object> result = bargainService.abandonAndBuy(5L, 7L);

        assertThat(bargain.getStatus()).isEqualTo("ABANDONED");
        assertThat(result).containsEntry("id", 30).containsEntry("status", "PENDING");
    }

    @Test
    void aSuccessfulBargainIsBoughtAtTheTarget() {
        BargainActivity bargain = bargain("SUCCESS", "60", "60", DAY);
        when(bargainActivityMapper.selectById(5L)).thenReturn(bargain);
        when(productMapper.selectByIdForUpdate(42L)).thenReturn(product(1));
        when(orderService.createBargainOrder(7, 42L, 1, new BigDecimal("60"))).thenReturn(order("60"));

        Map<String, Object> result = bargainService.buyAtBargainPrice(5L, 7L);

        assertThat(bargain.getStatus()).isEqualTo("COMPLETED");
        assertThat(result).containsEntry("totalAmount", new BigDecimal("60"));
    }

    @Test
    void onlyTheStarterCanGiveUpOrBuy() {
        when(bargainActivityMapper.selectById(5L)).thenReturn(bargain("SUCCESS", "60", "60", DAY));
        when(bargainActivityMapper.selectById(4L)).thenReturn(null);

        assertThatThrownBy(() -> bargainService.abandonAndBuy(5L, 8L)).hasMessageContaining("Only the person");
        assertThatThrownBy(() -> bargainService.buyAtBargainPrice(5L, 8L)).hasMessageContaining("Only the person");
        assertThatThrownBy(() -> bargainService.abandonAndBuy(4L, 7L)).hasMessageContaining("not found");
        assertThatThrownBy(() -> bargainService.buyAtBargainPrice(4L, 7L)).hasMessageContaining("not found");
        verifyNoInteractions(orderService);
    }

    @Test
    void anExpiredBargainCannotBeBought() {
        when(bargainActivityMapper.selectById(5L)).thenReturn(bargain("ACTIVE", "90", "60", -1000));

        assertThatThrownBy(() -> bargainService.abandonAndBuy(5L, 7L)).hasMessageContaining("over 24 hours");
        assertThatThrownBy(() -> bargainService.buyAtBargainPrice(5L, 7L)).hasMessageContaining("over 24 hours");
    }

    @ParameterizedTest
    @CsvSource({"EXPIRED, has expired", "COMPLETED, has ended"})
    void onlyAnActiveBargainCanBeGivenUp(String status, String message) {
        when(bargainActivityMapper.selectById(5L)).thenReturn(bargain(status, "90", "60", DAY));

        assertThatThrownBy(() -> bargainService.abandonAndBuy(5L, 7L)).hasMessageContaining(message);
    }

    @ParameterizedTest
    @CsvSource({"EXPIRED, has expired", "COMPLETED, already been bought", "FAILED, has failed", "ACTIVE, has not succeeded"})
    void onlyASuccessfulBargainCanBeBought(String status, String message) {
        when(bargainActivityMapper.selectById(5L)).thenReturn(bargain(status, "90", "60", DAY));

        assertThatThrownBy(() -> bargainService.buyAtBargainPrice(5L, 7L)).hasMessageContaining(message);
    }

    @Test
    void buyingFailsWhenTheProductIsGoneOrSoldOut() {
        when(bargainActivityMapper.selectById(5L)).thenReturn(bargain("ACTIVE", "90", "60", DAY));
        when(bargainActivityMapper.selectById(6L)).thenReturn(bargain("SUCCESS", "60", "60", DAY));
        when(productMapper.selectByIdForUpdate(42L))
                .thenReturn(null).thenReturn(product(0)).thenReturn(null).thenReturn(product(0));

        assertThatThrownBy(() -> bargainService.abandonAndBuy(5L, 7L)).hasMessageContaining("delisted");
        assertThatThrownBy(() -> bargainService.abandonAndBuy(5L, 7L)).hasMessageContaining("sold out");
        assertThatThrownBy(() -> bargainService.buyAtBargainPrice(6L, 7L)).hasMessageContaining("delisted");
        assertThatThrownBy(() -> bargainService.buyAtBargainPrice(6L, 7L)).hasMessageContaining("sold out");
        verifyNoInteractions(orderService);
    }

    private static Product product(int stock) {
        Product product = new Product();
        product.setId(42L);
        product.setPrice(new BigDecimal("100"));
        product.setStock(stock);
        return product;
    }

    private static BargainActivity bargain(String status, String current, String target, long expiresInMillis) {
        BargainActivity bargain = new BargainActivity();
        bargain.setId(5L);
        bargain.setUserId(7L);
        bargain.setProductId(42L);
        bargain.setStatus(status);
        bargain.setCurrentPrice(new BigDecimal(current));
        bargain.setTargetPrice(new BigDecimal(target));
        bargain.setExpireTime(new Date(System.currentTimeMillis() + expiresInMillis));
        return bargain;
    }

    private static Order order(String amount) {
        Order order = new Order();
        order.setId(30);
        order.setUserId(7);
        order.setTotalAmount(new BigDecimal(amount));
        order.setStatus("PENDING");
        return order;
    }
}
