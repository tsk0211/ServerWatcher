@file:OptIn(InternalApi::class)

package dev.tsk.server.watcher.core.data.aws

import aws.sdk.kotlin.services.cognitoidentityprovider.model.CodeMismatchException
import aws.sdk.kotlin.services.cognitoidentityprovider.model.NotAuthorizedException
import aws.sdk.kotlin.runtime.AwsServiceException
import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.ServiceErrorMetadata
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import aws.sdk.kotlin.services.cognitoidentity.model.NotAuthorizedException as IdentityNotAuthorizedException

class AuthErrorsTest {

    @Test
    fun `refresh-token rejection is detected from the error code, not the message`() {
        // Real Cognito wording: it never contains the exception class name.
        val e = NotAuthorizedException { message = "Refresh Token has expired" }

        assertTrue(e.isAuthError())
        assertTrue(e.isNotAuthorized())
    }

    @Test
    fun `identity pool token rejection counts as an auth error`() {
        val e = IdentityNotAuthorizedException { message = "Invalid login token. Token expired: 1700000000" }

        assertTrue(e.isAuthError())
    }

    @Test
    fun `auth errors are found through wrapping exceptions`() {
        val wrapped = RuntimeException("credentials failed", IllegalStateException("resolve", NotAuthorizedException {}))

        assertTrue(wrapped.isAuthError())
        assertTrue(wrapped.isNotAuthorized())
    }

    @Test
    fun `message text alone no longer triggers a sign-out`() {
        val e = RuntimeException("NotAuthorizedException mentioned in a log line")

        assertFalse(e.isAuthError())
        assertFalse(e.isNotAuthorized())
    }

    @Test
    fun `unrelated service errors and network failures are not auth errors`() {
        assertFalse(CodeMismatchException { message = "wrong code" }.isAuthError())
        assertFalse(java.io.IOException("timeout").isAuthError())
    }

    // EC2 / Route 53 have no typed exceptions: the SDK deserializer only sets the error code.
    // Setting it needs the SDK's internal attributes API, which is fine inside a test.
    private fun serviceError(code: String) =
        AwsServiceException("The security token is invalid").also {
            it.sdkErrorMetadata.attributes[ServiceErrorMetadata.ErrorCode] = code
        }

    @Test
    fun `untyped EC2 token errors are detected from the error code`() {
        assertTrue(serviceError("AuthFailure").isAuthError())
        assertTrue(serviceError("ExpiredToken").isAuthError())
        assertFalse(serviceError("AuthFailure").isNotAuthorized())
    }

    @Test
    fun `missing IAM permission is not an auth error, so it never signs the user out`() {
        assertFalse(serviceError("UnauthorizedOperation").isAuthError())
        assertFalse(serviceError("AccessDenied").isAuthError())
    }
}
