package dev.tsk.server.watcher.core.data.aws

import aws.smithy.kotlin.runtime.ServiceException
import aws.sdk.kotlin.services.cognitoidentity.model.NotAuthorizedException as IdentityNotAuthorizedException
import aws.sdk.kotlin.services.cognitoidentityprovider.model.NotAuthorizedException as IdpNotAuthorizedException

/**
 * Service error codes meaning "these credentials / tokens are no longer valid".
 *
 * Detection is based on the error code the service returned, not on message text: AWS SDK messages
 * are free-form ("Refresh Token has expired") and do not contain the exception class name.
 * Access-denied codes such as UnauthorizedOperation are deliberately absent — refreshing a token
 * will not fix a missing IAM permission, and it must not sign the user out.
 */
private val AUTH_ERROR_CODES = setOf(
    "NotAuthorizedException",
    "ExpiredToken",
    "ExpiredTokenException",
    "InvalidToken",
    "InvalidClientTokenId",
    "AuthFailure",
)

private const val NOT_AUTHORIZED = "NotAuthorizedException"

private fun Throwable.causeChain(): Sequence<Throwable> =
    generateSequence(this) { current -> current.cause?.takeIf { it !== current } }.take(8)

private fun Throwable.serviceErrorCodes(): Sequence<String?> =
    causeChain().mapNotNull { (it as? ServiceException)?.sdkErrorMetadata?.errorCode }

// Cognito has typed exceptions, so match those directly too. EC2 and Route 53 only expose the
// error code (set by the SDK's response deserializer), hence both checks.
private fun Throwable.isTypedNotAuthorized(): Boolean =
    causeChain().any { it is IdpNotAuthorizedException || it is IdentityNotAuthorizedException }

/** True if this failure (or any wrapped cause) is an expired/invalid-token style service error. */
internal fun Throwable.isAuthError(): Boolean =
    isTypedNotAuthorized() || serviceErrorCodes().any { it in AUTH_ERROR_CODES }

/** True if Cognito rejected the refresh token itself — the user has to sign in again. */
internal fun Throwable.isNotAuthorized(): Boolean =
    isTypedNotAuthorized() || serviceErrorCodes().any { it == NOT_AUTHORIZED }
