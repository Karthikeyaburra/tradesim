package com.tradesim.tradesim.repository;

import com.tradesim.tradesim.domain.Position;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface HoldingRepository {

    record RawHolding(Long userId, String symbol, int quantity, int reservedQuantity, BigDecimal avgBuyPrice) {
        public int availableQuantity() {
            return quantity - reservedQuantity;
        }
    }

    Optional<RawHolding> findRaw(Long userId, String symbol);

    List<Position> findPositionsByUserId(Long userId);

    List<RawHolding> findAllHoldersOfSymbol(String symbol);

    /** Atomically adds bought shares and updates weighted-average buy price. */
    void addShares(Long userId, String symbol, int addedQty, BigDecimal executionPrice);

    /** Atomically deducts unreserved shares ONLY IF (quantity - reserved_quantity) >= soldQty. */
    boolean removeUnreservedShares(Long userId, String symbol, int soldQty);

    /** Atomically locks shares for a Limit SELL ONLY IF (quantity - reserved_quantity) >= qty. */
    boolean reserveShares(Long userId, String symbol, int qty);

    /** Atomically unlocks shares when a Limit SELL is cancelled. */
    void releaseReservedShares(Long userId, String symbol, int qty);

    /** Atomically consumes previously reserved shares when a Limit SELL fills. */
    void consumeReservedShares(Long userId, String symbol, int qty);

    void deleteByUserId(Long userId);

    void deleteBySymbol(String symbol);
}

