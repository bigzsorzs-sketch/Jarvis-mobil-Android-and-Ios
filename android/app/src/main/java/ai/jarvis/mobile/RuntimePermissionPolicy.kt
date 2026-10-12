package ai.jarvis.mobile

import android.Manifest

internal object RuntimePermissionPolicy {
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
