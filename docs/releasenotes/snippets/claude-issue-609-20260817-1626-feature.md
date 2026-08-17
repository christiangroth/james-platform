* Publishing a Version with an otherwise-breaking change no longer forces a Major version bump if the Developer also supplies a migration script that successfully transforms all existing data — the change can now be published as a Feature or Bugfix instead.
* If the migration doesn't bring all existing data into a valid state, publishing still requires a Major bump, exactly as before.
