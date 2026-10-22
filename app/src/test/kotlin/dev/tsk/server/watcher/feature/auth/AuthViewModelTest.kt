package dev.tsk.server.watcher.feature.auth

import aws.sdk.kotlin.services.cognitoidentityprovider.model.CodeMismatchException
import aws.sdk.kotlin.services.cognitoidentityprovider.model.NotAuthorizedException
import dev.tsk.server.watcher.core.data.aws.AuthChallenge
import dev.tsk.server.watcher.core.data.aws.AuthResult
import dev.tsk.server.watcher.core.data.aws.CognitoTokenManager
import dev.tsk.server.watcher.core.data.aws.MfaMethod
import dev.tsk.server.watcher.core.data.aws.TotpSetup
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val tokenManager = mockk<CognitoTokenManager>()
    private val challenge = AuthChallenge(session = "session-1", username = "me@example.com")

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AuthViewModel(tokenManager)

    @Test
    fun `wrong MFA code keeps the user on the MFA step and shows an error`() {
        coEvery { tokenManager.login("me@example.com", "pw") } returns
            Result.success(AuthResult.MfaRequired(challenge, MfaMethod.TOTP))
        coEvery { tokenManager.submitMfa(challenge, MfaMethod.TOTP, "123456") } returns
            Result.failure(CodeMismatchException { message = "Invalid code received for user" })
        val vm = viewModel()

        vm.login("me@example.com", "pw")
        vm.submitMfa("123456")

        val state = vm.uiState.value
        assertEquals(AuthStep.Mfa(challenge, MfaMethod.TOTP), state.step)
        assertFalse(state.isLoading)
        assertEquals("That code is incorrect. Check your authenticator app and try again.", state.error)
    }

    @Test
    fun `a correct MFA code after a wrong one completes sign-in`() {
        coEvery { tokenManager.login(any(), any()) } returns
            Result.success(AuthResult.MfaRequired(challenge, MfaMethod.TOTP))
        coEvery { tokenManager.submitMfa(challenge, MfaMethod.TOTP, "111111") } returns
            Result.failure(CodeMismatchException {})
        coEvery { tokenManager.submitMfa(challenge, MfaMethod.TOTP, "222222") } returns
            Result.success(AuthResult.LoggedIn)
        val vm = viewModel()

        vm.login("me@example.com", "pw")
        vm.submitMfa("111111")
        vm.consumeError()
        vm.submitMfa("222222")

        assertEquals(AuthStep.Done, vm.uiState.value.step)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `expired challenge session sends the user back to sign in with an explanation`() {
        coEvery { tokenManager.login(any(), any()) } returns
            Result.success(AuthResult.MfaRequired(challenge, MfaMethod.TOTP))
        coEvery { tokenManager.submitMfa(any(), any(), any()) } returns
            Result.failure(NotAuthorizedException { message = "Invalid session for the user, session is expired." })
        val vm = viewModel()

        vm.login("me@example.com", "pw")
        vm.submitMfa("123456")

        assertEquals(AuthStep.Credentials, vm.uiState.value.step)
        assertEquals(SESSION_TIMED_OUT, vm.uiState.value.error)
    }

    @Test
    fun `wrong password at the first step says so and stays on the login form`() {
        coEvery { tokenManager.login(any(), any()) } returns
            Result.failure(NotAuthorizedException { message = "Incorrect username or password." })
        val vm = viewModel()

        vm.login("me@example.com", "bad")

        assertEquals(AuthStep.Credentials, vm.uiState.value.step)
        assertEquals("Incorrect email or password.", vm.uiState.value.error)
    }

    @Test
    fun `temporary password account is guided through choosing a new password`() {
        coEvery { tokenManager.login(any(), any()) } returns
            Result.success(AuthResult.NewPasswordRequired(challenge))
        coEvery { tokenManager.completeNewPassword(challenge, "Better-Passw0rd") } returns
            Result.success(AuthResult.LoggedIn)
        val vm = viewModel()

        vm.login("me@example.com", "TempP@ss123")
        assertEquals(AuthStep.NewPassword(challenge), vm.uiState.value.step)

        vm.setNewPassword("Better-Passw0rd")
        assertEquals(AuthStep.Done, vm.uiState.value.step)
    }

    @Test
    fun `new password can lead on to MFA`() {
        coEvery { tokenManager.login(any(), any()) } returns
            Result.success(AuthResult.NewPasswordRequired(challenge))
        val next = challenge.copy(session = "session-2")
        coEvery { tokenManager.completeNewPassword(challenge, "Better-Passw0rd") } returns
            Result.success(AuthResult.MfaRequired(next, MfaMethod.TOTP))
        val vm = viewModel()

        vm.login("me@example.com", "TempP@ss123")
        vm.setNewPassword("Better-Passw0rd")

        assertEquals(AuthStep.Mfa(next, MfaMethod.TOTP), vm.uiState.value.step)
    }

    @Test
    fun `SMS challenge is routed to the MFA step with the SMS method`() {
        coEvery { tokenManager.login(any(), any()) } returns
            Result.success(AuthResult.MfaRequired(challenge, MfaMethod.SMS))
        val vm = viewModel()

        vm.login("me@example.com", "pw")

        assertEquals(AuthStep.Mfa(challenge, MfaMethod.SMS), vm.uiState.value.step)
    }

    @Test
    fun `authenticator enrolment during sign-in generates a secret then completes`() {
        val setup = TotpSetup(secret = "JBSWY3DPEHPK3PXP", session = "session-setup")
        coEvery { tokenManager.login(any(), any()) } returns
            Result.success(AuthResult.MfaSetupRequired(challenge))
        coEvery { tokenManager.startTotpSetup(challenge) } returns Result.success(setup)
        coEvery { tokenManager.finishTotpSetup(challenge, "session-setup", "654321") } returns
            Result.success(AuthResult.LoggedIn)
        val vm = viewModel()

        vm.login("me@example.com", "pw")
        val step = vm.uiState.value.step as AuthStep.MfaSetup
        assertEquals(setup, step.setup)
        assertFalse(vm.uiState.value.isLoading)

        vm.submitTotpSetup("654321")
        assertEquals(AuthStep.Done, vm.uiState.value.step)
        coVerify(exactly = 1) { tokenManager.finishTotpSetup(challenge, "session-setup", "654321") }
    }

    @Test
    fun `failed secret generation can be retried`() {
        val setup = TotpSetup(secret = "JBSWY3DPEHPK3PXP", session = "session-setup")
        coEvery { tokenManager.login(any(), any()) } returns
            Result.success(AuthResult.MfaSetupRequired(challenge))
        coEvery { tokenManager.startTotpSetup(challenge) } returnsMany listOf(
            Result.failure(java.io.IOException("offline")),
            Result.success(setup),
        )
        val vm = viewModel()

        vm.login("me@example.com", "pw")
        assertNull((vm.uiState.value.step as AuthStep.MfaSetup).setup)
        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)

        vm.retryTotpSetup()
        assertEquals(setup, (vm.uiState.value.step as AuthStep.MfaSetup).setup)
        assertTrue(vm.uiState.value.error == null)
    }
}
