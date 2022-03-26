plugins {
    kotlin("multiplatform") version "1.6.10"
    id("org.jetbrains.kotlinx.benchmark") version "0.4.2"
    id("org.jetbrains.dokka") version "1.6.10"
    `maven-publish`
    signing
}

group = "io.github.quillraven.fleks"
version = "1.0-KMP-RC1"
java.sourceCompatibility = JavaVersion.VERSION_1_8

repositories {
    mavenCentral()
}

kotlin {
    targets {
        jvm {
            compilations {
                all {
                    kotlinOptions {
                        jvmTarget = "1.8"
                    }
                }
                val main by getting { }
                // custom benchmark compilation
                val benchmarks by compilations.creating {
                    defaultSourceSet {
                        dependencies {
                            // Compile against the main compilation's compile classpath and outputs:
                            implementation(main.compileDependencyFiles + main.output.classesDirs)
                        }
                    }
                }
            }
            withJava()
            testRuns["test"].executionTask.configure {
                useJUnitPlatform()
            }
        }
    }
    /*js(BOTH) {
        browser {
            commonWebpackConfig {
                cssSupport.enabled = true
            }
        }
    }
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }*/

    sourceSets {
        val commonMain by getting { }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting
        val jvmTest by getting
        val jvmBenchmarks by getting {
            dependsOn(commonMain)
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.2")
                implementation("com.badlogicgames.ashley:ashley:1.7.4")
                implementation("net.onedaybeard.artemis:artemis-odb:2.3.0")
            }
        }
        /*val jsMain by getting
        val jsTest by getting
        val nativeMain by getting
        val nativeTest by getting*/
    }
}

benchmark {
    targets {
        register("jvmBenchmarks")
    }
}

val dokkaJavadocJar = tasks.create<Jar>("jarDokkaJavadoc") {
    group = "build"

    dependsOn(tasks.dokkaJavadoc)
    from(tasks.dokkaJavadoc.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}

val sourcesJar = tasks.create<Jar>("jarSources") {
    group = "build"

    //from(sourceSets.main.get().allSource)
    archiveClassifier.set("sources")
}

artifacts {
    archives(dokkaJavadocJar)
    archives(sourcesJar)
}

val publicationName = "mavenFleks"
publishing {
    repositories {
        maven {
            url = if (project.version.toString().endsWith("SNAPSHOT")) {
                uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            } else {
                uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            }

            credentials {
                username = System.getenv("OSSRH_USERNAME")
                password = System.getenv("OSSRH_TOKEN")
            }
        }
    }

    publications {
        register<MavenPublication>(publicationName) {
            version = project.version.toString()
            groupId = project.group.toString()
            artifactId = "Fleks"

            from(components["kotlin"])
            artifact(dokkaJavadocJar)
            artifact(sourcesJar)

            pom {
                name.set("Fleks")
                packaging = "jar"
                description.set("A lightweight entity component system written in Kotlin.")
                url.set("https://github.com/Quillraven/Fleks")

                scm {
                    connection.set("scm:git:git@github.com:quillraven/fleks.git")
                    developerConnection.set("scm:git:git@github.com:quillraven/fleks.git")
                    url.set("https://github.com/quillraven/fleks/")
                }


                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("Quillraven")
                        name.set("Simon Klausner")
                        email.set("quillraven@gmail.com")
                    }
                }
            }
        }
    }
}

signing {
    useInMemoryPgpKeys(System.getenv("SIGNING_KEY"), System.getenv("SIGNING_PASSWORD"))
    sign(publishing.publications[publicationName])
}
