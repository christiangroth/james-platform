* Added `>`, `>=`, `<=`, `<` filter operators for number, date and date/time fields in the import filter step.
* Added `matches pattern` / `does not match pattern` filter operators for text fields.
* Filter operators are now only offered for the field types they make sense for.
* `EQUALS`/`NOT_EQUALS` filters on text fields now offer the values already seen in the data as picks, to avoid typos.
