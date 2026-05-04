* Added app data list view to installed app detail page, showing stored objects with ID, entity type, and last modified date.
* Added an Add button to create new data entries for an installed app's entities.
* If an app has only one entity, the Add button opens the form directly; otherwise a dropdown lets you choose the entity.
* A generic form generates inputs for all entity properties and validates constraints before saving.
* App data is persisted in MongoDB in the `app_data` collection.
