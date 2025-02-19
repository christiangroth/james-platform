package de.chrgroth.james.platform.adapter.`in`.http

import de.chrgroth.james.platform.domain.user.USER_ROLE_ADMIN
import de.chrgroth.james.platform.domain.user.USER_ROLE_DEVELOPER
import de.chrgroth.james.platform.domain.user.USER_ROLE_USER
import io.quarkus.security.Authenticated
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed

@RolesAllowed(value = [USER_ROLE_ADMIN])
annotation class AdminAccess

@RolesAllowed(value = [USER_ROLE_DEVELOPER])
annotation class DeveloperAccess

@RolesAllowed(value = [USER_ROLE_USER])
annotation class UserAccess

@Authenticated
annotation class PrivateAccess

@PermitAll
annotation class PublicAccess
