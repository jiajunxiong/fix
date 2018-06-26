package com.cashalgo.fix

data class Position(
    val id: String,
    var pq: PQ,
    private var amount: Double
) {
    constructor(id: String): this(id, pq0, 0.0)

    @Suppress("unused")
    constructor(): this("")

    fun add(quantity: Double, amount: Double) {
        val nextQuantity = this.pq.quantity+quantity
        val nextAmount = this.amount+amount
        val nextPrice = nextAmount/nextQuantity
        this.pq = PQ(nextPrice, nextQuantity)
        this.amount = nextAmount
    }
}