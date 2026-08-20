* Data imports can now be scheduled to run automatically on a recurring cron schedule, reusing the previously configured connection, filter and mapping without any manual steps.
* A scheduled import run is automatically skipped (instead of importing possibly wrong data) if the source data's structure has changed since the last run.
* Import schedules can be set at most every 15 minutes.
