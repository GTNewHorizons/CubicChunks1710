import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

tasks.withType<ShadowJar>().configureEach {
    relocate("net.jpountz", "com.cardinalstar.cubicchunks.shadow.net.jpountz")
}
