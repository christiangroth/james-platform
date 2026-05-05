* Deleting a data record now checks for references from other records via reference properties.
* Deletion is rejected if the record is referenced by a required (non-nullable) reference property in another record.
* If all referencing properties are nullable, those references are automatically cleared before deleting the record.
* The delete confirmation message reports how many references were cleared.
