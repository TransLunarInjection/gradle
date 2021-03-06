plugins {
   `java-library`
}

repositories {
    mavenCentral()
}

// tag::dependencies[]
dependencies {
    implementation("org.apache.commons:commons-lang3:3.0")
    // the following dependency brings lang3 3.8.1 transitively
    implementation("com.opencsv:opencsv:4.6")
}
// end::dependencies[]

// tag::fail-on-version-conflict[]
configurations.all {
    resolutionStrategy {
        failOnVersionConflict()
    }
}
// end::fail-on-version-conflict[]

// tag::fail-on-dynamic[]
configurations.all {
    resolutionStrategy {
        failOnDynamicVersions()
    }
}
// end::fail-on-dynamic[]

// tag::fail-on-changing[]
configurations.all {
    resolutionStrategy {
        failOnChangingVersions()
    }
}
// end::fail-on-changing[]

// tag::fail-on-unstable[]
configurations.all {
    resolutionStrategy {
        failOnNonReproducibleResolution()
    }
}
// end::fail-on-unstable[]
