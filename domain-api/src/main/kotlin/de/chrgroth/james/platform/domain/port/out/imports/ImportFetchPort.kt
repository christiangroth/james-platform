package de.chrgroth.james.platform.domain.port.out.imports

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError

/**
 * Fetches a JSON document from a user-supplied URL using a Bearer token. Implementations are responsible for
 * SSRF protections (host validation, scheme allow-list, timeouts, response size limits) since the URL and its
 * resolved target are not otherwise trusted.
 */
interface ImportFetchPort {
  fun fetch(url: String, bearerToken: String): Either<DomainError, String>
}
