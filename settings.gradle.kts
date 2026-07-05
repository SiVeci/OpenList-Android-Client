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
    }
}

rootProject.name = "OpenList-Android-Client"
include(":app")
include(":core:common")
include(":core:model")
include(":core:designsystem")
include(":core:network")
include(":core:database")
include(":core:auth")
include(":core:domain")
include(":data:repository")
include(":feature:instance")
include(":feature:auth")
include(":feature:files")
include(":feature:settings")
include(":feature:upload")
include(":feature:share")
include(":feature:task")
include(":feature:search")
include(":feature:preview")
include(":feature:admin")
