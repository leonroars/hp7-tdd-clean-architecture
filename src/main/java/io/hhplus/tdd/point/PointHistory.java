package io.hhplus.tdd.point;

import java.util.Random;

public record PointHistory(
        long id,
        long userId,
        long amount,
        TransactionType type,
        long updateMillis
) {
}
