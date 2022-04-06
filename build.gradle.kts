plugins {
    checkstyle
    jacoco
    signing
    groovy
    `java-library`
    `java-library-distribution`
    `maven-publish`
    id("com.github.spotbugs") version "5.0.6"
    id("com.diffplug.spotless") version "6.4.2"
    id("com.github.kt3k.coveralls") version "2.12.0"
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
    id("com.palantir.git-version") version "0.12.3"
    id("kr.motd.sphinx") version "2.10.1"
}

group = "io.github.eb4j"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains:annotations:23.0.0")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("commons-io:commons-io:2.11.0")
    implementation("com.ibm.icu:icu4j-charset:70.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
    testImplementation("org.codehaus.groovy:groovy-all:3.0.10")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}
spotbugs {
    excludeFilter.set(project.file("config/spotbugs/exclude.xml"))
    tasks.spotbugsMain {
        reports.create("html") {
            required.set(true)
        }
    }
    tasks.spotbugsTest {
        reports.create("html") {
            required.set(true)
        }
    }
}

jacoco {
    toolVersion="0.8.6"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test) // tests are required to run before generating the report
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.coveralls {
    dependsOn(tasks.jacocoTestReport)
}

coveralls {
    jacocoReportPath = "build/reports/jacoco/test/jacocoTestReport.xml"
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:deprecation")
    options.compilerArgs.add("-Xlint:unchecked")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
    withJavadocJar()
}

val versionDetails: groovy.lang.Closure<com.palantir.gradle.gitversion.VersionDetails> by extra
val details = versionDetails()
val baseVersion = details.lastTag.substring(1)
if (details.isCleanTag) {  // release version
    version = baseVersion
} else {  // snapshot version
    version = baseVersion + "-" + details.commitDistance + "-" + details.gitHash + "-SNAPSHOT"
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("PDIC4j")
                description.set("PDIC access library for java")
                url.set("https://github.com/eb4j/pdic4j")
                licenses {
                    license {
                        name.set("The GNU General Public License, Version 3")
                        url.set("https://www.gnu.org/licenses/licenses/gpl-3.html")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("miurahr")
                        name.set("Hiroshi Miura")
                        email.set("miurahr@linux.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/eb4j/pdic4j.git")
                    developerConnection.set("scm:git:git://github.com/eb4j/pdic4j.git")
                    url.set("https://github.com/eb4j/pdic4j")
                }
            }
        }
    }
}

signing {
    if (project.hasProperty("signingKey")) {
        val signingKey: String? by project
        val signingPassword: String? by project
        useInMemoryPgpKeys(signingKey, signingPassword)
    } else {
        useGpgCmd()
    }
    sign(publishing.publications["mavenJava"])
}

tasks.withType<Sign> {
    val hasKey = project.hasProperty("signingKey") || project.hasProperty("signing.gnupg.keyName")
    onlyIf { hasKey && details.isCleanTag }
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            username.set(System.getenv("SONATYPE_USER"))
            password.set(System.getenv("SONATYPE_PASS"))
        }
    }
}

tasks.sphinx {
    sourceDirectory {"docs"}
}