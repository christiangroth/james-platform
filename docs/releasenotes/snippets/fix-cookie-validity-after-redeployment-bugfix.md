* Session cookies now survive redeployments when `APP_TOKEN_ENCRYPTION_KEY` is kept stable across deployments.
* Deployment will now fail immediately if `APP_TOKEN_ENCRYPTION_KEY` or `HTTP_AUTH_ENCRYPTION_KEY` are not set, preventing silent misconfigurations.
