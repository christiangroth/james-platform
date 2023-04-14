plugins {
    id("kotlin-project")
    id("net.researchgate.release") version "3.0.2"

    id("se.patrikerdes.use-latest-versions") version "0.2.18"
    id("com.github.ben-manes.versions") version "0.46.0"
}

// TODO #34 automate
// call task koverMergedReport for merged report manually
koverMerged {
    enable()
}
