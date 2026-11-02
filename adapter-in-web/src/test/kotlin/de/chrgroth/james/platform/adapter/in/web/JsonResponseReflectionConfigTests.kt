package de.chrgroth.james.platform.adapter.`in`.web

import io.quarkus.runtime.annotations.RegisterForReflection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

class JsonResponseReflectionConfigTests {

  /**
   * A response DTO nested inside a registered one (e.g. a chart axis inside a report panel) is serialized by Jackson as well, so it
   * needs the same native-mode registration - otherwise it silently serializes as `{}` in production (native) while every JVM test
   * passes. Walks every registered class' field types (incl. generic arguments) and requires each web-package class found to be
   * registered too.
   */
  @Test
  fun `every response DTO reachable from a registered one is registered for reflection`() {
    val registered = JsonResponseReflectionConfig::class.java.getAnnotation(RegisterForReflection::class.java).targets.map { it.java }.toSet()
    val missing = mutableSetOf<String>()
    val seen = mutableSetOf<Class<*>>()

    fun visit(type: Type) {
      when (type) {
        is Class<*> -> {
          if (type.isEnum || type.isPrimitive || type.`package`?.name != WEB_PACKAGE || !seen.add(type)) return
          if (type !in registered) missing += type.simpleName
          type.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.forEach { visit(it.genericType) }
        }
        is ParameterizedType -> type.actualTypeArguments.forEach { visit(it) }
        is WildcardType -> type.upperBounds.forEach { visit(it) }
      }
    }
    registered.forEach { visit(it) }

    assertThat(missing).describedAs("nested response DTOs missing in JsonResponseReflectionConfig").isEmpty()
  }

  private companion object {
    const val WEB_PACKAGE = "de.chrgroth.james.platform.adapter.in.web"
  }
}
