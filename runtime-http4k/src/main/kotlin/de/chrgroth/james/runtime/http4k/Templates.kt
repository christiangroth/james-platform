package de.chrgroth.james.runtime.http4k

import org.http4k.template.ViewModel
import java.util.UUID

sealed interface NamedViewModel : ViewModel {
    /**
     * This is the path of the template file - which matches the fully qualified classname. The templating suffix
     * is added by the template implementation (eg. java.lang.String -> java/lang/String.hbs)
     */
    override fun template(): String = "templates/${javaClass.simpleName.substringBeforeLast("ViewModel")}"
}

// TODO put to services / into other Kotlin files?
data class AppViewModel(val dummy: String = UUID.randomUUID().toString()) : NamedViewModel
data class AppsViewModel(val dummy: String = UUID.randomUUID().toString()) : NamedViewModel
data class DevelopmentViewModel(val dummy: String = UUID.randomUUID().toString()) : NamedViewModel
data class LoginViewModel(val dummy: String = UUID.randomUUID().toString()) : NamedViewModel
data class ReleasenotesViewModel(val releasenotes: List<ReleasenotesEntry>) : NamedViewModel
data class WorkspaceViewModel(val dummy: String = UUID.randomUUID().toString()) : NamedViewModel
