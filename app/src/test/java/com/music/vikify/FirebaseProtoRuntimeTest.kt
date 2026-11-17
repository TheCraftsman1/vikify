package com.music.vikify

import com.google.firestore.v1.Value
import com.google.rpc.Status
import com.google.type.LatLng
import org.junit.Assert.assertEquals
import org.junit.Test

class FirebaseProtoRuntimeTest {
    @Test fun `Firestore document values retain their required Google API message types`() {
        val value = Value.newBuilder().setGeoPointValue(
            LatLng.newBuilder().setLatitude(17.4).setLongitude(78.5),
        ).build()
        val restored = Value.parseFrom(value.toByteArray())
        assertEquals(17.4, restored.geoPointValue.latitude, 0.0)
        assertEquals(78.5, restored.geoPointValue.longitude, 0.0)
        val status = Status.newBuilder().setCode(7).setMessage("permission denied").build()
        assertEquals(7, Status.parseFrom(status.toByteArray()).code)
    }
}
