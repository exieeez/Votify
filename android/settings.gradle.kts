pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // NewPipeExtractor (YouTube/SoundCloud extraction without a server) lives on JitPack.
        maven(url = "https://jitpack.io")
    }
}
rootProject.name = "Votify"
include(":app")
