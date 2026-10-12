package ai.jarvis.mobile

import android.Manifest

internal object RuntimePermissionPolicy {
    /** Optional notification results do not revoke an already granted microphone. */
    fun mayStartVoice(microphoneGranted: Boolean, result: Map<String, Boolean>): Boolean =
        result[Manifest.permission.RECORD_AUDIO] ?: microphoneGranted

    /** Android requires both location permissions when upgrading approximate to precise access. */
    fun missing(requested: List<String>, granted: Set<String>): List<String> {
        val pending = requested.distinct().filterNot { it in granted }.toMutableList()
        if (Manifest.permission.ACCESS_FINE_LOCATION in pending &&
            Manifest.permission.ACCESS_COARSE_LOCATION in requested &&
            Manifest.permission.ACCESS_COARSE_LOCATION !in pending) {
            pending.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        return pending
    }
}

