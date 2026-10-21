// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration.model

/** Why a capability can or cannot be used right now. The UI renders exactly these states. */
enum class AvailabilityState {
    AVAILABLE,
    UNSUPPORTED_DEVICE,
    PERMISSION_REQUIRED,

    /** Root integration not enabled, refused, or no su binary. */
    ROOT_REQUIRED,

    /** The Magisk/KernelSU module that provides it is not installed (or its daemon is down). */
    COMPONENT_NOT_INSTALLED,

    /** The native component was not packaged for this ABI, or cannot run. */
    NATIVE_COMPONENT_UNAVAILABLE,
    NETWORK_UNAVAILABLE,
    LICENSE_REQUIRED,
    INITIALIZATION_FAILED,

    /** Implemented and callable, but never validated on hardware. */
    NOT_VERIFIED,

    /** The owning repository contains no implementation to integrate. */
    NO_UPSTREAM_IMPLEMENTATION,
}

data class Availability(val state: AvailabilityState, val reason: String) {
    val usable: Boolean get() = state == AvailabilityState.AVAILABLE || state == AvailabilityState.NOT_VERIFIED

    companion object {
        fun available(reason: String = "") = Availability(AvailabilityState.AVAILABLE, reason)
    }
}

/** How a capability reaches its implementation (prompt modes A–D). */
enum class Privilege {
    /** Public Android APIs or code running as the app's own uid. */
    STANDARD,

    /** Root through su, to an installed Zairenkai module. */
    ROOT,

    /** Authenticated remote service. None exists in the organization yet. */
    REMOTE,

    NONE,
}

/** Repository and commit a capability's implementation comes from. */
data class Provider(val repository: String, val revision: String?, val component: String)
