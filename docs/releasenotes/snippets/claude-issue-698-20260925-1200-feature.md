* The import history now shows how many data objects each run added, replaced and discarded.
* Import schedules are now chosen from simple options (no schedule, daily at a fixed time, or a fixed interval) instead of typing a cron expression, and the import overview describes them in plain words.
* Schedule times and all displayed timestamps now use one shared time zone (Europe/Berlin by default), so "daily at 06:30" runs at 06:30 local time; existing schedules are now interpreted in this zone instead of UTC.
* Fixed unreadable text in dark mode in the data path overview and the mapping preview.
* Renamed the "Quelldaten anzeigen" button to "Quelldaten Schema".
