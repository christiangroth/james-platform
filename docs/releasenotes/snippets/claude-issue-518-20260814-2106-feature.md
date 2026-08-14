* Import dry-run now distinguishes records that are skipped by design (fan-in mappings: no value, or a value already used by an earlier record for a unique property) from genuinely invalid records.
* The dry-run report shows a separate "Skipped" count and section for these expected fan-in skips, so real validation problems are no longer buried in noise.
