* Display text placeholders now use property names instead of internal IDs, making templates more readable for developers.
* Already used property tokens are hidden from the placeholder badge list in the display text editor.
* Unknown property references in display texts are rendered as `<?>` at runtime.
* Publishing a version is now blocked when any entity's display text references unknown or nullable properties.
