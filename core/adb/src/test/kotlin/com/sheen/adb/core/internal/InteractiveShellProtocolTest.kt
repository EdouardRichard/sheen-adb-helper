package com.sheen.adb.core.internal

import java.io.File
import java.util.ArrayDeque
import kotlinx.coroutines.runBlocking
import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class InteractiveShellProtocolTest {
    private val adapter =
        File("src/main/kotlin/com/sheen/adb/core/internal/AdbProtocolAdapter.kt")
    private val kadbFactory =
        File("src/main/kotlin/com/sheen/adb/core/internal/KadbProtocolClientFactory.kt")

    @Test
    fun `protocol exposes one owned bidirectional child stream`() {
        val source = adapter.readText()
        listOf(
            "ProtocolInteractiveShellStream",
            "AutoCloseable",
            "fun openInteractiveShell",
            "fun read",
            "fun write",
        ).forEach { token -> assertTrue(source.contains(token), "missing duplex protocol token $token") }

        val declaration = source.substringAfter("interface ProtocolInteractiveShellStream")
            .substringBefore("internal interface AdbProtocolClient")
        assertFalse(declaration.contains("fun execute("), "interactive child stream must not be a one-shot command")
    }

    @Test
    fun `kadb adapter reads and writes through the same single opened stream`() {
        val block = interactiveFactoryBlock()
        assertTrue(block.isNotBlank(), "Kadb interactive-shell adapter is missing")
        assertEquals(
            Regex("kadb\\.open(?:Shell)?\\(").findAll(block).count(),
            1,
            "one interactive handle must own exactly one Kadb child stream",
        )
        listOf(".source", ".sink", "read(", "write(", "flush(").forEach { token ->
            assertTrue(block.contains(token), "interactive stream is not bidirectional: $token")
        }
        assertFalse(block.contains("openShellCommand("), "submitted input must stay on the owned child stream")
    }

    @Test
    fun `complete command submission is one serialized write rather than per key execution`() {
        val source = adapter.readText() + interactiveFactoryBlock()
        listOf(
            "sendSubmittedCommand",
            "completeCommand",
        ).forEach { token -> assertTrue(source.contains(token), "missing submitted-command token $token") }
        assertTrue(
            source.contains("completeCommand.encodeToByteArray") ||
                source.contains("submittedCommand.encodeToByteArray"),
            "the confirmed complete command must be encoded as one payload",
        )
        assertFalse(source.contains("execute(completeCommand)"), "submission must not open a one-shot shell")
        assertFalse(
            Regex("completeCommand\\s*\\.\\s*forEach").containsMatchIn(source),
            "ordinary command characters must not be written key by key",
        )
    }

    @Test
    fun `live terminal input uses semantic encoder and never writes key labels`() {
        val source = adapter.readText() + interactiveFactoryBlock()
        listOf(
            "sendTerminalInput",
            "TerminalInput",
            "TerminalInputEncoder.encode",
        ).forEach { token -> assertTrue(source.contains(token), "missing semantic-input token $token") }
        assertFalse(source.contains("terminalInput.toString().encodeToByteArray"))
        assertFalse(source.contains("semanticInput.toString().encodeToByteArray"))
    }

    @Test
    fun `submitted command emits a hidden completion boundary across split output packets`() = runBlocking {
        val stream = RecordingInteractiveStream()
        val shell = ProtocolInteractiveShell(stream)

        shell.sendSubmittedCommand("echo ready")

        val payload = stream.writes.single().toString(Charsets.UTF_8)
        val marker = requireNotNull(Regex("__SHEEN_READY_[0-9a-f]+__").find(payload)).value
        val split = marker.length / 2
        stream.reads.addLast(
            ProtocolShellPacket.StandardOutput(("result\n" + marker.take(split)).encodeToByteArray()),
        )
        stream.reads.addLast(
            ProtocolShellPacket.StandardOutput((marker.drop(split) + "prompt").encodeToByteArray()),
        )

        val output = shell.read() as ProtocolShellPacket.StandardOutput
        val completion = shell.read() as ProtocolShellPacket.StandardOutput
        val followingOutput = shell.read() as ProtocolShellPacket.StandardOutput

        assertEquals(output.bytes.toString(Charsets.UTF_8), "result\n")
        assertTrue(completion.bytes.isEmpty(), "empty packet is the internal remote-ready boundary")
        assertEquals(followingOutput.bytes.toString(Charsets.UTF_8), "prompt")
        assertFalse(
            listOf(output, followingOutput).any { it.bytes.toString(Charsets.UTF_8).contains(marker) },
            "the private completion marker must never reach terminal presentation",
        )
    }

    @Test
    fun `closing child output closes only that stream and preserves parent protocol connection`() {
        val block = interactiveFactoryBlock()
        assertTrue(block.contains("ProtocolShellPacket.Exit"), "child output must expose its terminal boundary")
        assertTrue(
            block.contains("closeOutput") || block.contains("outputClosed") || block.contains("ended"),
            "child output closure must be represented explicitly",
        )
        assertTrue(block.contains("stream.close()"), "closing the handle must close its owned child stream")
        assertFalse(block.contains("kadb.close()"), "child close must not disconnect the main ADB Session")

        val factory = kadbFactory.readText()
        assertTrue(
            factory.substringAfter(block).contains("override fun openSync()") ||
                factory.contains("override fun openSync()"),
            "parent client must remain available after a child Shell stream closes",
        )
    }

    private fun interactiveFactoryBlock(): String {
        val source = kadbFactory.readText()
        return source.substringAfter(
            "override fun openInteractiveShell",
            missingDelimiterValue = "",
        ).substringBefore("override fun openSync")
    }

    private class RecordingInteractiveStream : ProtocolInteractiveShellStream {
        val reads = ArrayDeque<ProtocolShellPacket>()
        val writes = mutableListOf<ByteArray>()

        override fun read(): ProtocolShellPacket = reads.removeFirst()

        override fun write(bytes: ByteArray) {
            writes += bytes
        }

        override fun closeInput() = Unit

        override fun closeOutput() = Unit

        override fun close() = Unit
    }
}
