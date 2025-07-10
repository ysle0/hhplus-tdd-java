package io.hhplus.tdd.point.integration;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.TransactionType;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Point History Integration Tests")
@SpringBootTest
@AutoConfigureMockMvc
public class PointHistoryIntegrationTest {

    private static final long TEST_USER_ID = 12324L;
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

    @DisplayName("GET /point/{id}/histories returns 200 with charge transaction in response array")
    @ParameterizedTest(name = "#{index} API returns charge transaction for amount {0}")
    @ValueSource(longs = {1, 10, 100, 1_000, 1_000_000, 100_000_000})
    void get_histories_returns_200_with_charge_transaction(long amount) throws Exception {
        // First perform a charge operation
        final String chargeBody = """
                {
                    "amount": %d
                }
                """.formatted(amount);

        mvc.perform(
                patch("http://localhost:8080/point/{id}/charge", TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chargeBody)
        ).andExpect(status().isOk());

        // Then verify the history contains the charge operation
        ResultActions performer = mvc.perform(
                get("http://localhost:8080/point/{id}/histories", TEST_USER_ID)
        );

        performer.andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2)) // Initial + new charge
                .andExpect(jsonPath("$[0].userId").value(TEST_USER_ID));
    }

    @DisplayName("GET /point/{id}/histories returns 200 with use transaction in response array")
    @ParameterizedTest(name = "#{index} API returns use transaction for amount {0}")
    @ValueSource(longs = {1, 10, 100, 1_000, 5_000})
    void get_histories_returns_200_with_use_transaction(long amount) throws Exception {
        // First perform a use operation
        final String useBody = """
                {
                    "amount": %d
                }
                """.formatted(amount);

        mvc.perform(
                patch("http://localhost:8080/point/{id}/use", TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(useBody)
        ).andExpect(status().isOk());

        // Then verify the history contains the use operation
        ResultActions performer = mvc.perform(
                get("http://localhost:8080/point/{id}/histories", TEST_USER_ID)
        );

        performer.andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2)) // Initial + new use
                .andExpect(jsonPath("$[0].userId").value(TEST_USER_ID));
    }

    @DisplayName("GET /point/{id}/histories returns 400 with error message for invalid user IDs")
    @ParameterizedTest(name = "#{index} API returns 400 for invalid user ID {0}")
    @ValueSource(longs = {-1, 0, 999, 12345, 99999, 23410941902L})
    void get_histories_returns_400_for_invalid_user_ids(long invalidUserId) throws Exception {
        ResultActions performer = mvc.perform(
                get("http://localhost:8080/point/{id}/histories", invalidUserId)
        );

        performer.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(UserNotFoundException.makeExMsg(invalidUserId)));
    }

    @Test
    @DisplayName("GET /point/{id}/histories returns 200 with complete transaction array after multiple operations")
    void get_histories_returns_200_with_complete_transaction_array() throws Exception {
        final long chargeAmount1 = 5_000L;
        final long chargeAmount2 = 3_000L;
        final long useAmount1 = 2_500L;
        final long useAmount2 = 1_500L;

        // Perform multiple operations
        String chargeBody1 = """
                {
                    "amount": %d
                }
                """.formatted(chargeAmount1);

        mvc.perform(
                patch("http://localhost:8080/point/{id}/charge", TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chargeBody1)
        ).andExpect(status().isOk());

        String useBody1 = """
                {
                    "amount": %d
                }
                """.formatted(useAmount1);

        mvc.perform(
                patch("http://localhost:8080/point/{id}/use", TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(useBody1)
        ).andExpect(status().isOk());

        String chargeBody2 = """
                {
                    "amount": %d
                }
                """.formatted(chargeAmount2);

        mvc.perform(
                patch("http://localhost:8080/point/{id}/charge", TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chargeBody2)
        ).andExpect(status().isOk());

        String useBody2 = """
                {
                    "amount": %d
                }
                """.formatted(useAmount2);

        mvc.perform(
                patch("http://localhost:8080/point/{id}/use", TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(useBody2)
        ).andExpect(status().isOk());

        // Verify complete history
        ResultActions performer = mvc.perform(
                get("http://localhost:8080/point/{id}/histories", TEST_USER_ID)
        );

        performer.andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(5)) // Initial charge + 4 operations
                .andExpect(jsonPath("$[0].userId").value(TEST_USER_ID))
                .andExpect(jsonPath("$[0].updateMillis").exists());
    }
}
