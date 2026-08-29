package com.raven.application

import com.raven.application.bluetooth.BluetoothMessage
import com.raven.application.data.MeshProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RavenCoreTest {
    @Test
    fun meshProtocolKeepsWireTypesCentralized() {
        assertEquals("CHAT", MeshProtocol.CHAT)
        assertEquals("SOS", MeshProtocol.SOS)
        assertEquals("LOCATION", MeshProtocol.LOCATION)
        assertEquals("CAMP_UPSERT", MeshProtocol.CAMP_UPSERT)
    }

    @Test
    fun relayDecrementsTtlWithoutMutatingOriginal() {
        val message = BluetoothMessage(senderName = "Node", message = "hello", ttl = 2)
        val relayed = message.relay()

        assertEquals(2, message.ttl)
        assertEquals(1, relayed?.ttl)
    }

    @Test
    fun expiredOrExhaustedMessagesAreDropped() {
        val expired = BluetoothMessage(senderName = "Node", message = "old", ttl = 2, timestamp = 0L)
        val exhausted = BluetoothMessage(senderName = "Node", message = "done", ttl = 0)

        assertNull(expired.relay())
        assertNull(exhausted.relay())
    }
}
