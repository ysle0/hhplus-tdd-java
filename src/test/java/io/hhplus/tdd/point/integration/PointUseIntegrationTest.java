package io.hhplus.tdd.point.integration;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.TransactionType;
import io.hhplus.tdd.point.exception.NegativePointException;
import io.hhplus.tdd.point.exception.NotEnoughPointException;
import io.hhplus.tdd.point.exception.UserNotFoundException;
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

@DisplayName("Point Use Integration Tests")
@SpringBootTest
@AutoConfigureMockMvc
public class PointUseIntegrationTest {

    private static final long TEST_USER_ID = 1L;
    private static final long TEST_USER_ID_INVALID = 999L;
    private static final long INIT_BALANCE = 10_000L;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserPointTable userPointTable;
    @Autowired
    private PointHistoryTable pointHistoryTable;

    @BeforeEach
    void beforeEach() {
        userPointTable.insertOrUpdate(TEST_USER_ID, INIT_BALANCE);
        pointHistoryTable.insert(TEST_USER_ID, INIT_BALANCE, TransactionType.CHARGE, System.currentTimeMillis());
    }

    @AfterEach
    void afterEach() {
        userPointTable = new UserPointTable();
        pointHistoryTable = new PointHistoryTable();
    }

    @DisplayName("PATCH /point/{id}/use returns 200 with reduced point balance")
    @ParameterizedTest(name = "#{index} API returns 200 for use amount {0}")
    @ValueSource(longs = {1, 10, 100, 1_000, 5_000, 9_999})
    void patch_use_returns_200_with_reduced_balance(long usedPoint) throws Exception {
        final String body = """
                {
                     "amount": %d
                }
                """.formatted(usedPoint);
        final long expectPoint = INIT_BALANCE - usedPoint;

        ResultActions performer = mvc.perform(
                patch("http://localhost:8080/point/{id}/use", TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        );

        performer.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_USER_ID))
                .andExpect(jsonPath("$.point").value(expectPoint));
    }

    @Test
    @DisplayName("PATCH /point/{id}/use returns 200 with unchanged balance for zero amount")
    void patch_use_returns_200_for_zero_amount() throws Exception {
        final String body = """
                {
                    "amount": 0
                }
                """;

        ResultActions performer = mvc.perform(
                patch("http://localhost:8080/point/{id}/use", TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        );

        performer.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_USER_ID))
                .andExpect(jsonPath("$.point").value(INIT_BALANCE));
    }


    @Test
    @DisplayName("PATCH /point/{id}/use returns 400 with error message for invalid user ID")
    void patch_use_returns_400_for_invalid_user_id() throws Exception {
        final String body = """
                {
                    "amount": 1000
                }
                """;
        ResultActions performer = mvc.perform(
                patch("http://localhost:8080/point/{id}/use", TEST_USER_ID_INVALID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        );

        performer.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(UserNotFoundException.MakeExMsg(TEST_USER_ID_INVALID)));
    }

    @DisplayName("PATCH /point/{id}/use returns 400 with error message for insufficient balance")
    @ParameterizedTest(name = "#{index} API returns 400 for insufficient balance with amount {0}")
    @ValueSource(longs = {10_001, 50_000, 100_000, 1_000_000, 99_999_999_999L})
    void patch_use_returns_400_for_insufficient_balance(long usedPoint) throws Exception {
        final String body = """
                {
                    "amount": %d
                }
                """.formatted(usedPoint);

        ResultActions performer = mvc.perform(
                patch("http://localhost:8080/point/{id}/use", TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        );

        performer.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(NotEnoughPointException.MakeExMsg(TEST_USER_ID, usedPoint, INIT_BALANCE)));
    }

    @DisplayName("PATCH /point/{id}/use returns 400 with error message for negative amounts")
    @ParameterizedTest(name = "#{index} API returns 400 for negative amount {0}")
    @ValueSource(longs = {-1, -10, -100, -1_000, -10_000, -1_234_567})
    void patch_use_returns_400_for_negative_amounts(long usedPoint) throws Exception {
        final String body = """
                {
                    "amount": %d
                }
                """.formatted(usedPoint);

        ResultActions performer = mvc.perform(
                patch("http://localhost:8080/point/{id}/use", TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        );

        performer.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(NegativePointException.MakeExMsg(usedPoint)));
    }

    @Test
    @DisplayName("PATCH /point/{id}/charge and /use return 200 with correct balance after multiple API calls")
    void patch_charge_and_use_return_200_with_correct_balance_after_multiple_calls() throws Exception {
        final long chargeAmount1 = 5_000L;
        final long chargeAmount2 = 3_000L;
        final long useAmount1 = 2_500L;
        final long useAmount2 = 1_500L;
        final long expectedFinalBalance = INIT_BALANCE + chargeAmount1 + chargeAmount2 - useAmount1 - useAmount2;

        // First charge operation
        String chargeBody1 = """
                {
                    "amount": %d
                }
                """.formatted(chargeAmount1);

        ResultActions chargePerformer1 = mvc.perform(
                patch("http://localhost:8080/point/{id}/charge", TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chargeBody1)
        );

        chargePerformer1.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_USER_ID))
                .andExpect(jsonPath("$.point").value(INIT_BALANCE + chargeAmount1));

        // First use operation
        String useBody1 = """
                {
                    "amount": %d
                }
                """.formatted(useAmount1);

        ResultActions usePerformer1 = mvc.perform(
                patch("http://localhost:8080/point/{id}/use", TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(useBody1)
        );

        usePerformer1.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_USER_ID))
                .andExpect(jsonPath("$.point").value(INIT_BALANCE + chargeAmount1 - useAmount1));

        // Second charge operation
        String chargeBody2 = """
                {
                    "amount": %d
                }
                """.formatted(chargeAmount2);

        ResultActions chargePerformer2 = mvc.perform(
                patch("http://localhost:8080/point/{id}/charge", TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chargeBody2)
        );

        chargePerformer2.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_USER_ID))
                .andExpect(jsonPath("$.point").value(INIT_BALANCE + chargeAmount1 - useAmount1 + chargeAmount2));

        // Second use operation
        String useBody2 = """
                {
                    "amount": %d
                }
                """.formatted(useAmount2);

        ResultActions usePerformer2 = mvc.perform(
                patch("http://localhost:8080/point/{id}/use", TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(useBody2)
        );

        usePerformer2.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_USER_ID))
                .andExpect(jsonPath("$.point").value(expectedFinalBalance));
    }
}
