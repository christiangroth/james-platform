* Unrelated user deletions, app uninstalls/deletions, App-Version auto-upgrades and import accepts now run concurrently in the background instead of queueing behind each other; operations on the same entity still run in order.
* Scheduled imports now start right at their configured time instead of on the next poll cycle (previously up to 15 minutes late).
