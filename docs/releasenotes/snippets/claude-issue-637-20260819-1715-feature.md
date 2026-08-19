* Generating a large batch of test data for an Entity now runs in the background instead of blocking the request.
* The entity editor shows a "generating..." status while a large test data run is in progress, and refreshes automatically once it finishes.
* Small, bounded test data runs still complete immediately as before.
