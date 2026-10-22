package dev.tsk.server.watcher.feature.auth

import aws.sdk.kotlin.services.cognitoidentityprovider.model.CodeMismatchException
import aws.sdk.kotlin.services.cognitoidentityprovider.model.EnableSoftwareTokenMfaException
import aws.sdk.kotlin.services.cognitoidentityprovider.model.ExpiredCodeException
import aws.sdk.kotlin.services.cognitoidentityprovider.model.InvalidPasswordException
import aws.sdk.kotlin.services.cognitoidentityprovider.model.LimitExceededException
import aws.sdk.kotlin.services.cognitoidentityprovider.model.NotAuthorizedException
import aws.sdk.kotlin.services.cognitoidentityprovider.model.PasswordResetRequiredException
import aws.sdk.kotlin.services.cognitoidentityprovider.model.TooManyRequestsException
import aws.sdk.kotlin.services.cognitoidentityprovider.model.UserNotConfirmedException
import aws.sdk.kotlin.services.cognitoidentityprovider.model.UserNotFoundException
import java.io.IOException

internal const val SESSION_TIMED_OUT = "Your sign-in timed out. Please sign in again."

/** True when the error means the in-progress challenge session is no longer usable. */
internal fun Throwable.isChallengeSessionLost(): Boolean = this is NotAuthorizedException

/**
 * Turns an SDK exception into something a person can act on. [duringChallenge] distinguishes a
 * wrong password at the first step from an expired session at a later one — Cognito uses
 * NotAuthorizedException for both.
 */
internal fun Throwable.toAuthMessage(duringChallenge: Boolean): String = when {
    this is NotAuthorizedException ->
        if (duringChallenge) SESSION_TIMED_OUT else "Incorrect email or password."
    // Same wording as a wrong password so the app does not reveal which emails have accounts.
    this is UserNotFoundException -> "Incorrect email or password."
    this is CodeMismatchException || this is EnableSoftwareTokenMfaException ->
        "That code is incorrect. Check your authenticator app and try again."
    this is ExpiredCodeException -> "That code has expired. Enter the latest code."
    this is InvalidPasswordException -> message ?: "That password does not meet the requirements."
    this is UserNotConfirmedException -> "This account has not been confirmed yet."
    this is PasswordResetRequiredException -> "A password reset is required for this account."
    this is TooManyRequestsException || this is LimitExceededException ->
        "Too many attempts. Wait a moment and try again."
    generateSequence(this) { it.cause }.take(6).any { it is IOException } ->
        "Can't reach AWS. Check your connection and try again."
    else -> message ?: "Something went wrong. Please try again."
}
