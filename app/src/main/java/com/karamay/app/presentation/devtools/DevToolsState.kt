package com.karamay.app.presentation.devtools

/**
 * Fix #31: canAskAgain renamed to canRequestAgain for semantic clarity.
 *
 * The previous name "canAskAgain" was a loose echo of the old (incorrect) implementation
 * that used a Build.VERSION.SDK_INT check. Now that both monitor screens derive this flag
 * from ActivityCompat.shouldShowRequestPermissionRationale(), the name "canRequestAgain"
 * more precisely describes what the flag means:
 *   - true  → the system will still show the permission dialog if we launch the request
 *   - false → the user tapped "Don't ask again"; we must send them to Settings instead
 */
sealed interface PermissionState {
    data object Idle      : PermissionState
    data object Requested : PermissionState
    data object Granted   : PermissionState
    data class  Denied(val canRequestAgain: Boolean) : PermissionState
}
