* Fixed the CI/CD pipeline so deployments to production run again after the recent native-build speedup change (GitHub Actions was skipping the deploy step entirely). No user-visible change.
* Removed a leftover redundant native build check that could intermittently fail the pipeline for unrelated reasons. No user-visible change.
