* Imported data objects now internally track which import connection they came from, as groundwork for future traceability features.
* Accepting an import now fails with a clear error if the underlying import connection was deleted in the meantime, instead of importing the data without any origin information.
