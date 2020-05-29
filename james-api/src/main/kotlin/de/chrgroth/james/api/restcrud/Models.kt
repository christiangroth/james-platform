package de.chrgroth.james.api.restcrud

import org.litote.kmongo.Id

data class App(
    val _id: Id<App>?,
    val id: Long,
    val name: String,
    val description: String? 
)

data class AppVersion(
    val _id: Id<AppVersion>?,
    val appId: Long,
    val version: String,
    val models: List<AppVersionModel>?,
    val views: List<AppVersionView>? 
)

data class AppVersionModel(
    val _id: Id<AppVersionModel>?,
    val name: String,
    val jsonSchema: String 
)

data class AppVersionView(
    val _id: Id<AppVersionView>?,
    val name: String,
    val html: String 
)
