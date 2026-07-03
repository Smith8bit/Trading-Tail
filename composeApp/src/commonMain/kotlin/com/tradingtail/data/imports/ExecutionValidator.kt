package com.tradingtail.data.imports
// ponytail: dir/package is `imports` not `import` — `import` is a Kotlin hard keyword and won't
// compile as a package segment. Same role CLAUDE.md's `data/import/` describes.

import com.tradingtail.common.BigDecimal
import com.tradingtail.common.ZERO
import com.tradingtail.data.local.entity.ExecutionEntity
import com.tradingtail.data.local.entity.ExecutionSource
import com.tradingtail.data.local.entity.InstrumentType
import com.tradingtail.data.local.entity.Side

/**
 * The single validation + normalization gate. Both manual entry paths and (later) CSV import
 * converge here before an Execution ever hits the database: one validator, one shape.
 */
object ExecutionValidator {
    fun validate(
        symbol: String,
        side: Side,
        price: BigDecimal,
        quantity: BigDecimal,
        timestamp: Long,
        source: ExecutionSource,
        fees: BigDecimal = ZERO,
        instrumentType: InstrumentType = InstrumentType.STOCK,
    ): ExecutionEntity {
        val normalizedSymbol = symbol.trim().uppercase()
        require(normalizedSymbol.isNotEmpty()) { "symbol must not be blank" }
        require(price > ZERO) { "price must be > 0, was $price" }
        require(quantity > ZERO) { "quantity must be > 0, was $quantity" }
        require(fees >= ZERO) { "fees must be >= 0, was $fees" }
        return ExecutionEntity(
            symbol = normalizedSymbol,
            side = side,
            price = price,
            quantity = quantity,
            timestamp = timestamp,
            fees = fees,
            instrumentType = instrumentType,
            source = source,
        )
    }
}
