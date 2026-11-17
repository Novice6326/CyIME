# CI build note

The ARM64-only workflow build uses `ndk.abiFilters` to restrict native libraries to `arm64-v8a` and disables APK ABI splits for that variant. Multi-ABI builds use APK splits and leave `ndk.abiFilters` unset. Android Gradle Plugin rejects a variant when both mechanisms specify ABI filters.

This note documents the build-configuration invariant fixed in the development branch.
