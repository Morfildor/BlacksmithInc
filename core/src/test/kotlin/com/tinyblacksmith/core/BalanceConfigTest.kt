package com.tinyblacksmith.core

import com.tinyblacksmith.core.config.BalanceConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class BalanceConfigTest {
    /**
     * [BalanceConfig] sits at the JVM limit of 255 argument slots per method (a Double takes two). One flat field too
     * many still compiles and then fails when the constructor or `copy` is first linked, so both are called here:
     * the failure shows up in this test instead of at launch. New numbers go into the nested groups.
     */
    @Test
    fun constructs() {
        val config = BalanceConfig()
        assertEquals(BalanceConfig.DEFAULT, config)
        assertEquals(config.version + 1, config.copy(version = config.version + 1).version)
    }
}
