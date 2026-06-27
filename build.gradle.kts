plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.0" apply false
}

allprojects {
    group = rootProject.property("mod_group_id") as String
    version = rootProject.findProperty("version") ?: rootProject.property("mod_version")
}
