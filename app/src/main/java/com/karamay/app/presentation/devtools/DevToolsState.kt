package com.karamay.app.presentation.devtools

/**
 * Models the runtime permission lifecycle shared by all dev-tools screens that
 * require the ACTIVITY_RECOGNITION permission (activity monitor, sleep monitor).
 *
 * Kept in the parent [devtools] package so both sub-packages can import it
 * without a circular dependency.
 */
sealed interface PermissionState {
    /** Initial state — no request has been made yet this session. */
    data object Idle : PermissionState

    /** A system dialog has been launched and we are awaiting the result. */
    data object Requested : PermissionState

    /** The user granted the permission (or it was already granted). */
    data object Granted : PermissionState

    /**
     * The user denied the permission.
     *
     * @param canAskAgain true when the system will still show a dialog on the
     *                    next request (i.e. "Don't ask again" was NOT checked).
     */
    data class Denied(val canAskAgain: Boolean) : PermissionState
}