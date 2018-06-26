package com.cashalgo.fix

import org.redisson.Redisson
import org.redisson.api.RedissonClient

val redis = init()
class IdGen {
    private val id = redis.getAtomicLong("id")
    fun nextId(): String = id.andIncrement.toString()
}

fun init(): RedissonClient {
    val redisConfig = org.redisson.config.Config()
    redisConfig.useSingleServer().setAddress("redis://localhost:6379")
    return Redisson.create(redisConfig)!!
}