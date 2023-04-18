package th.ac.kmitl.se;

import org.graphwalker.core.machine.ExecutionContext;
import org.graphwalker.java.annotation.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.*;
import static org.mockito.Mockito.*;

// Update the filename of the saved file of your model here.
@Model(file  = "model.json")
public class OrderAdapter extends ExecutionContext {
    // The following method add some delay between each step
    // so that we can see the progress in GraphWalker player.
    public static int delay = 200;
    @AfterElement
    public void afterEachStep() {
        try
        {
            Thread.sleep(delay);
        }
        catch(InterruptedException ex)
        {
            Thread.currentThread().interrupt();
        }
    }

    OrderDB orderDB;
    ProductDB productDB;
    PaymentService paymentService;
    ShippingService shippingService;
    Order order;
    @Mock
    Card card;
    @Mock
    Address address;

    static int orderId = 1;
    static int paymentServiceRefundCount = 1;
    static int paymentServiceCountPay = 1;

    ArgumentCaptor<PaymentCallback> callbackCaptor;
    ArgumentCaptor<PaymentCallback> callbackCaptorRefund;

    @BeforeExecution
    public void setUp() {
        // Initialize the mocks
        order = new Order(orderDB,productDB,paymentService,shippingService);
        orderDB = mock(OrderDB.class);
        productDB = mock(ProductDB.class);
        paymentService = mock(PaymentService.class);
        shippingService = mock(ShippingService.class);

        // Create test data
        Card makeUpCard = new Card("001", "John", 2022, 2025);
        Address personMakeUpAddress = new Address("Building", "001", "Street 1", "Town 1", "City 1", "001");

        // Initialize the callback captors
        callbackCaptor = ArgumentCaptor.forClass(PaymentCallback.class);
        callbackCaptorRefund = ArgumentCaptor.forClass(PaymentCallback.class);
    }

    @Edge()
    public void reset() {
        System.out.println("Edge reset");
        // Reset the order instance
        order = new Order(orderDB, productDB, paymentService, shippingService);

        Assertions.assertEquals(Order.Status.CREATED, order.getStatus());

    }

    @Edge()
    public void place() {
        System.out.println("Edge place");
        // Get a new order ID and place an order
        when(orderDB.getOrderID()).thenReturn(orderId++);
        order.place("John", "Apple Watch", 2, address);

        Assertions.assertEquals(Order.Status.PLACED, order.getStatus());
    }

    @Edge()
    public void cancel() {
        System.out.println("Edge cancel");
        // Cancel order
        order.cancel();
    }

    @Edge()
    public void pay() {
        System.out.println("Edge pay");
        // Get product and shipping information
        when(productDB.getPrice("Apple Watch")).thenReturn(1500f);
        when(productDB.getWeight("Apple Watch")).thenReturn(350f);
        when(shippingService.getPrice(address, 700f)).thenReturn(50f);

        assertEquals(order.getTotalCost(), 3050F);

        // Pay for the order
        order.pay(card);

        Assertions.assertEquals(Order.Status.PAYMENT_CHECK, order.getStatus());
    }

    @Edge()
    public void retryPay() {
        System.out.println("Edge retryPay");
        // Retry payment for the order
        order.pay(card);

        assertEquals(Order.Status.PAYMENT_CHECK, order.getStatus());
    }

    @Edge()
    public void paySuccess() {

        System.out.println("Edge paySuccess");
        // Check for parameters in payment service
        verify(paymentService, times(paymentServiceCountPay++)).pay(any(Card.class), anyFloat(), callbackCaptor.capture());
        // Payment success callback
        callbackCaptor.getValue().onSuccess("1");

        assertEquals(Order.Status.PAID, order.getStatus());
        assertEquals(order.paymentConfirmCode, "1");
    }

    @Edge()
    public void payError() {
        System.out.println("Edge payError");
        // Check for parameters in payment service
        verify(paymentService, times(paymentServiceCountPay++)).pay(any(Card.class), anyFloat(), callbackCaptor.capture());
        // Payment error callback
        callbackCaptor.getValue().onError("0");
        assertEquals(Order.Status.PAYMENT_ERROR, order.getStatus());
    }

    @Edge()
    public void ship() {
        System.out.println("Edge ship");
        // Get a shipping price and a tracking code
        when(shippingService.ship(address, 700F)).thenReturn("11001");
        // Shipping process for the order
        order.ship();
        assertEquals(order.trackingCode, "11001");
        assertEquals(Order.Status.SHIPPED, order.getStatus());
    }

    @Edge()
    public void refundSuccess() {
        System.out.println("Edge refundSuccess");
        // Verify that the order status is "AWAIT_REFUND"
        assertEquals(Order.Status.AWAIT_REFUND, order.getStatus());

        verify(paymentService, times(paymentServiceRefundCount++)).refund(any(), callbackCaptorRefund.capture());
        // Successful refund callback
        callbackCaptorRefund.getValue().onSuccess("1");
        assertEquals(Order.Status.REFUNDED, order.getStatus());
    }

    @Edge()
    public void refundError() {
        System.out.println("Edge refundError");
        // Verify that the order status is "AWAIT_REFUND"
        assertEquals(Order.Status.AWAIT_REFUND, order.getStatus());
        verify(paymentService, times(paymentServiceRefundCount++)).refund(any(), callbackCaptorRefund.capture());
        // Refund error callback
        callbackCaptorRefund.getValue().onError("0");

        assertEquals(Order.Status.REFUND_ERROR, order.getStatus());
    }
}
