package com.kvl.cyclotrack

class FeatureFlags {
    companion object {
        const val devBuild: Boolean =
            BuildConfig.BUILD_TYPE == "dev" || BuildConfig.BUILD_TYPE == "debug"
        const val productionBuild: Boolean = BuildConfig.BUILD_TYPE == "prod"
        const val betaBuild: Boolean = !productionBuild
    }
}
