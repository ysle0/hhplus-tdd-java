package io.hhplus.tdd.point.integration;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.TransactionType;
import io.hhplus.tdd.point.exception.UserNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Point Show Integration Tests")
@SpringBootTest
@AutoConfigureMockMvc
public class PointShowIntegrationTest {

    private static final long TEST_USER_ID = 12324L;
    private static final long TEST_USER_ID_INVALID = 999L;
    private static final long INIT_POINT = 50_000L;
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

    @DisplayName("GET /point/{id} returns 200 with user point balance in JSON response")
    @ParameterizedTest(name = "#{index} API returns 200 with balance {0}")
    @ValueSource(longs = {0, 1, 100, 1_000, 10_000, 50_000, 100_000, 1_000_000})
    void get_point_returns_200_with_user_balance(long pointAmount) throws Exception {
        userPointTable.insertOrUpdate(TEST_USER_ID, pointAmount);

        ResultActions performer = mvc.perform(
                get("http://localhost:8080/point/{id}", TEST_USER_ID)
        );

        performer.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_USER_ID))
                .andExpect(jsonPath("$.point").value(pointAmount))
                .andExpect(jsonPath("$.updateMillis").exists());
    }

    @DisplayName("GET /point/{id} returns 400 with error message for invalid user IDs")
    @ParameterizedTest(name = "#{index} API returns 400 for invalid user ID {0}")
    @ValueSource(longs = {-1, 0, 999, 12345, 99999, Long.MAX_VALUE})
    void get_point_returns_400_for_invalid_user_ids(long invalidUserId) throws Exception {
        ResultActions performer = mvc.perform(
                get("http://localhost:8080/point/{id}", invalidUserId)
        );

        performer.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(UserNotFoundException.MakeExMsg(invalidUserId)));
    }

    @DisplayName("GET /point/{id} returns 200 with updated balance after state changes")
    @ParameterizedTest(name = "#{index} API returns 200 with final balance {0}")
    @ValueSource(longs = {0, 1_000, 5_000, 10_000, 25_000, 45_000, 52_000})
    void get_point_returns_200_with_updated_balance_after_state_changes(long finalAmount) throws Exception {
        // Simulate operations that result in the final amount
        userPointTable.insertOrUpdate(TEST_USER_ID, finalAmount);
        pointHistoryTable.insert(TEST_USER_ID, finalAmount - INIT_POINT, TransactionType.CHARGE, System.currentTimeMillis());

        ResultActions performer = mvc.perform(
                get("http://localhost:8080/point/{id}", TEST_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
        );

        performer.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_USER_ID))
                .andExpect(jsonPath("$.point").value(finalAmount))
                .andExpect(jsonPath("$.updateMillis").exists());
    }
}
