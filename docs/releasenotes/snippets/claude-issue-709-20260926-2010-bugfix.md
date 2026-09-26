* Fixed the breaking-change check assuming every installation is already on the latest published version; installations that
  fell behind auto-upgrade now have their own pending migrations applied first before the check runs.
