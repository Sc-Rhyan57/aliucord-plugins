version = "1.0.0"
description = "Spoof Discord version to the latest available from APKMirror"

aliucord {
    changelog.set(
        """
        # 1.0.0
        * Fetches latest Discord Android version from APKMirror on startup.
        * Patches PackageManager.getPackageInfo to return the spoofed version.
        * Patches PackageInfo fields directly so all internal Discord calls are fooled.
        * Falls back to a hardcoded recent version if the network fetch fails.
        """.trimIndent(),
    )
    deploy.set(true)
}
