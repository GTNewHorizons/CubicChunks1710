import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
plugins {
    id("com.gtnewhorizons.gtnhconvention")
    id("me.champeau.jmh") version "0.7.3"
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

sourceSets {
    named("jmh") {
        compileClasspath += sourceSets["patchedMc"].output
        runtimeClasspath += sourceSets["patchedMc"].output
    }
}

dependencies {
    add("jmhRuntimeOnly", files(sourceSets["patchedMc"].output))
    add("jmhAnnotationProcessor", "net.bytebuddy:byte-buddy:1.15.11")
}

tasks.withType<ShadowJar>().configureEach {
    relocate("net.jpountz", "com.cardinalstar.cubicchunks.shadow.net.jpountz")
}
