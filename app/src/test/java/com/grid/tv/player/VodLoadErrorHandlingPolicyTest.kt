package com.grid.tv.player

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class VodLoadErrorHandlingPolicyTest {

    private val policy = VodLoadErrorHandlingPolicy()

    @Test
    fun minimumRetries_zeroForAllDataTypes() {
        assertEquals(0, policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MANIFEST))
        assertEquals(0, policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA))
    }
}
