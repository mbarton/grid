package com.gu.mediaservice.lib.auth

import com.gu.mediaservice.lib.auth.Authentication.{AuthenticatedService, PandaUser, Principal}
import com.gu.mediaservice.lib.config.CommonConfig
import com.gu.permissions.{PermissionDefinition, PermissionsConfig, PermissionsProvider}


object PermissionDeniedError extends Throwable("Permission denied")

trait PermissionsHandler {
  def config: CommonConfig

  private val permissionsStage = if(config.stage == "PROD") { "PROD" } else { "CODE" }
  private val permissions = PermissionsProvider(PermissionsConfig(permissionsStage, config.awsRegion, config.awsCredentials))

  def storeIsEmpty: Boolean = {
    permissions.storeIsEmpty
  }

  def hasPermission(user: Principal, permission: PermissionDefinition): Boolean = {
    user match {
      case PandaUser(u) => permissions.hasPermission(permission, u.email)
      // think about only allowing certain services i.e. on `service.name`?
      case service: AuthenticatedService if service.apiKey.tier == Internal => true
      case _ => false
    }
  }
}
