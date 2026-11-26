package com.venas.foldspectrum.device

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CoverDisplayTest {
    @Test fun cancellationAfterCommandStartsStillRestoresTheDisplay() = runBlocking {
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val commands = CopyOnWriteArrayList<List<String>>()
        val display = CoverDisplay(manufacturer = "OPPO", execute = { args ->
            commands.add(args.toList())
            if (args.last() != "reset") {
                started.countDown()
                check(finish.await(3, TimeUnit.SECONDS))
            }
            DisplayResult(true, "ok")
        })
        try {
            val entry = launch { display.enter() }
            withContext(Dispatchers.IO) { assertTrue(started.await(3, TimeUnit.SECONDS)) }
            entry.cancel()
            val reset = display.reset()
            finish.countDown()
            entry.join()
            reset.join()
            assertEquals(listOf(listOf("state", "1"), listOf("state", "reset")), commands)
        } finally {
            finish.countDown()
            display.close()
        }
    }

    @Test fun failedRepeatedEntryDoesNotForgetAnExistingOverride() = runBlocking {
        val commands = mutableListOf<List<String>>()
        val display = CoverDisplay(manufacturer = "OPPO", execute = { args ->
            commands.add(args.toList())
            DisplayResult(commands.size != 2, "")
        })
        try {
            assertTrue(display.enter().success)
            assertFalse(display.enter().success)
            display.reset().join()
            assertEquals(listOf("state", "reset"), commands.last())
            assertEquals(3, commands.size)
        } finally { display.close() }
    }

    @Test fun successfulHonorSecondResetClearsOwnership() = runBlocking {
        var resets = 0
        val display = CoverDisplay(manufacturer = "HONOR", execute = { args ->
            DisplayResult(if (args.last() == "reset") ++resets > 1 else true, "")
        })
        try {
            display.enter()
            display.reset().join()
            display.reset().join()
            assertEquals(2, resets)
        } finally { display.close() }
    }

    @Test fun unsuccessfulResetRemainsRetryable() = runBlocking {
        var resets = 0
        val display = CoverDisplay(manufacturer = "OPPO", execute = { args ->
            DisplayResult(if (args.last() == "reset") ++resets > 1 else true, "")
        })
        try {
            display.enter()
            display.reset().join()
            display.reset().join()
            display.reset().join()
            assertEquals(2, resets)
        } finally { display.close() }
    }
}
