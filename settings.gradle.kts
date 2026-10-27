pluginManagement { repositories { google(); gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "AgroCampo"
include(":shared", ":androidApp")
