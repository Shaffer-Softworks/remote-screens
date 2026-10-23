package tech.shaffersoftworks.remotescreens.server.plugins

import io.ktor.utils.io.ClosedByteChannelException
import io.ktor.utils.io.ClosedWriteChannelException
import kotlinx.coroutines.CancellationException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ErrorHandlingTest {
    @Test
    fun `a cancelled request is abandoned`() {
        assertTrue(isAbandoned(CancellationException("LazyStandaloneCoroutine was cancelled")))
    }

    @Test
    fun `a client hanging up mid-response is abandoned`() {
        val reset = IOException("Connection reset by peer")
        assertTrue(isAbandoned(ClosedByteChannelException(ClosedWriteChannelException(reset))))
    }

    @Test
    fun `a closed channel buried in the cause chain is abandoned`() {
        assertTrue(isAbandoned(RuntimeException("wrapped", ClosedByteChannelException(null))))
    }

    @Test
    fun `other failures are real errors`() {
        assertFalse(isAbandoned(IllegalStateException("boom")))
        assertFalse(isAbandoned(IOException("Connection reset by peer")))
    }
}
