* Removed the mapping name field from the import job mapping form; it was no longer needed.
* Fan-in import mappings (many source records into few target objects identified by a unique field) can now be saved without a static fallback value.
* Source records without a value for such a field, or whose value was already used by an earlier record, are skipped instead of being reported as an error.
