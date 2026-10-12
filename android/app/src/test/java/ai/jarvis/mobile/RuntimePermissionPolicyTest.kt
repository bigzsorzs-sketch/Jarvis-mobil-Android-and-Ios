package ai.jarvis.mobile

import android.Manifest
import org.junit.Assert.*
import org.junit.Test

class RuntimePermissionPolicyTest {
    @Test fun notificationOnlyResultDoesNotBlockAnAlreadyGrantedMicrophone() {
        assertTrue(RuntimePermissionPolicy.mayStartVoice(true,
            mapOf(Manifest.permission.POST_NOTIFICATIONS to true)))
        assertTrue(RuntimePermissionPolicy.mayStartVoice(true,
            mapOf(Manifest.permission.POST_NOTIFICATIONS to false)))
        assertFalse(RuntimePermissionPolicy.mayStartVoice(false,
            mapOf(Manifest.permission.POST_NOTIFICATIONS to true)))
    }

    @Test fun explicitMicrophoneResultControlsVoiceStart() {
        assertTrue(RuntimePermissionPolicy.mayStartVoice(false,
            mapOf(Manifest.permission.RECORD_AUDIO to true)))
        assertFalse(RuntimePermissionPolicy.mayStartVoice(true,
            mapOf(Manifest.permission.RECORD_AUDIO to false)))
    }

    @Test fun approximateToPreciseUpgradeKeepsBothLocationPermissions() {
        val requested = listOf(Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION)
        val result = RuntimePermissionPolicy.missing(requested,
            setOf(Manifest.permission.ACCESS_COARSE_LOCATION))
        assertEquals(requested.toSet(), result.toSet())
    }

    @Test fun grantedMicrophoneAndContactsAreNotRequestedAgain() {
        val requested = listOf(Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)
        assertEquals(listOf(Manifest.permission.CALL_PHONE),
            RuntimePermissionPolicy.missing(requested,
                setOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS)))
        assertTrue(RuntimePermissionPolicy.missing(requested, requested.toSet()).isEmpty())
    }
}

