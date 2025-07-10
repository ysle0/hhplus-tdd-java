package io.hhplus.tdd.point.integration;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.TransactionType;
import io.hhplus.tdd.point.exception.NegativePointException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Point Charge Integration Tests")
@SpringBootTest
@AutoConfigureMockMvc
public class PointChargeIntegrationTest {

    private static final long TEST_USER_ID = 12324L;
    private static final long TEST_USER_ID_INVALID = 999L;
    private static final long INIT_POINT = 10_000L;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserPointTable userPointTable;
    @Autowired
    private PointHistoryTable pointHistoryTable;

    @BeforeEach
    void beforeEach() {
        userPointTable.insertOrUpdate(TEST_USER_ID, INIT_POINT);
        pointHistoryTable.insert(TEST_USER_ID, INIT_POINT, TransactionType.CHARGE, System.currentTimeMillis());
    }

    @AfterEach
    void afterEach() {
        userPointTable = new UserPointTable();
        pointHistoryTable = new PointHistoryTable();
    }

    @DisplayName("PATCH /point/{id}/charge returns 200 with updated point balance")
    @ParameterizedTest(name = "#{index} API returns 200 for charge amount {0}")
    @ValueSource(longs = {1, 10, 100, 1_000, 1_000_000})
    void patch_charge_returns_200_with_updated_balance(long amount) throws Exception {
        final String body = """
                {
                    "amount": %d
                }
                """.formatted(amount);

        ResultActions performer = mvc.perform(
                patch("http://localhost:8080/point/{id}/charge", TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        );

        performer.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_USER_ID))
                .andExpect(jsonPath("$.point").value(INIT_POINT + amount))
                .andExpect(jsonPath("$.updateMillis").exists());
    }

    @DisplayName("PATCH /point/{id}/charge returns 400 with error message for negative amounts")
    @ParameterizedTest(name = "#{index} API returns 400 for negative amount {0}")
    @ValueSource(longs = {-1, -10, -100, -1_000, -10_000, -100_000})
    void patch_charge_returns_400_for_negative_amounts(long amount) throws Exception {
        final String body = """
                {
                    "amount": %d
                }
                """.formatted(amount);

        ResultActions performer = mvc.perform(
                patch("http://localhost:8080/point/{id}/charge", TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        );

        performer.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(NegativePointException.makeExMsg(amount)));
    }

    @DisplayName("PATCH /point/{id}/charge returns 200 with unchanged balance for zero amount")
    @Test
    void patch_charge_returns_200_for_zero_amount() throws Exception {
        final String body = """
                {
                    "amount": 0
                }
                """;

        ResultActions performer = mvc.perform(
                patch("http://localhost:8080/point/{id}/charge", TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        );

        performer.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_USER_ID))
                .andExpect(jsonPath("$.point").value(INIT_POINT))
                .andExpect(jsonPath("$.updateMillis").exists());
    }

    @DisplayName("PATCH /point/{id}/charge returns 200 with updated balance for maximum amount")
    @Test
    void patch_charge_returns_200_for_maximum_amount() throws Exception {
        long maxAmount = Long.MAX_VALUE - INIT_POINT;
        final String body = """
                {
                    "amount": %d
                }
                """.formatted(maxAmount);

        ResultActions performer = mvc.perform(
                patch("http://localhost:8080/point/{id}/charge", TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        );

        performer.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_USER_ID))
                .andExpect(jsonPath("$.point").value(INIT_POINT + maxAmount))
                .andExpect(jsonPath("$.updateMillis").exists());
    }

    @DisplayName("PATCH /point/{id}/charge returns 200 with cumulative balance after multiple requests")
    @Test
    void patch_charge_returns_200_with_cumulative_balance_after_multiple_requests() throws Exception {
        final long firstChargeAmount = 5_000L;
        final long secondChargeAmount = 3_000L;
        final long expectedFinalAmount = INIT_POINT + firstChargeAmount + secondChargeAmount;

        String firstBody = """
                {
                    "amount": %d
                }
                """.formatted(firstChargeAmount);

        mvc.perform(
                patch("http://localhost:8080/point/{id}/charge", TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstBody)
        ).andExpect(status().isOk());

        String secondBody = """
                {
                    "amount": %d
                }
                """.formatted(secondChargeAmount);

        ResultActions performer = mvc.perform(
                patch("http://localhost:8080/point/{id}/charge", TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondBody)
        );

        performer.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_USER_ID))
                .andExpect(jsonPath("$.point").value(expectedFinalAmount))
                .andExpect(jsonPath("$.updateMillis").exists());
    }

    @DisplayName("PATCH /point/{id}/charge returns 400 for invalid request and preserves balance")
    @Test
    void patch_charge_returns_400_and_preserves_balance_after_invalid_request() throws Exception {
        final long chargeAmount = 5_000L;
        final long negativeAmount = -1_000L;
        final long expectedFinalAmount = INIT_POINT + chargeAmount;

        String chargeBody = """
                {
                    "amount": %d
                }
                """.formatted(chargeAmount);

        mvc.perform(
                patch("http://localhost:8080/point/{id}/charge", TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chargeBody)
        ).andExpect(status().isOk());

        String negativeBody = """
                {
                    "amount": %d
                }
                """.formatted(negativeAmount);

        mvc.perform(
                patch("http://localhost:8080/point/{id}/charge", TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(negativeBody)
        ).andExpect(status().isBadRequest());

        final String zeroBody = """
                {
                    "amount": 0
                }
                """;

        ResultActions performer = mvc.perform(
                patch("http://localhost:8080/point/{id}/charge", TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(zeroBody)
        );

        performer.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_USER_ID))
                .andExpect(jsonPath("$.point").value(expectedFinalAmount))
                .andExpect(jsonPath("$.updateMillis").exists());
    }
}
