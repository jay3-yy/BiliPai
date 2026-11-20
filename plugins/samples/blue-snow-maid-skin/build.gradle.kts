plugins {
    base
}

tasks.register<Zip>("packageBpSkin") {
    group = "distribution"
    description = "Package the Blue Snow Maid UI skin as a .bpskin file."
    archiveBaseName.set("blue-snow-maid")
    archiveExtension.set("bpskin")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))

    from(layout.projectDirectory.file("skin-manifest.json"))
    from(layout.projectDirectory.dir("assets")) {
        into("assets")
    }
    // Keep the published package at its existing URL and let local edits take precedence.
    from(zipTree(layout.projectDirectory.file("blue-snow-maid.bpskin").asFile)) {
        include("assets/**")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}
