// Copyright 2021 The Cross-Media Measurement Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.wfanet.panelmatch.client.launcher

import org.wfanet.measurement.api.v2alpha.CanonicalExchangeStepAttemptKey
import org.wfanet.measurement.api.v2alpha.ExchangeStep
import org.wfanet.measurement.api.v2alpha.ExchangeStepAttempt
import org.wfanet.panelmatch.client.launcher.InvalidExchangeStepException.FailureType.PERMANENT
import org.wfanet.panelmatch.client.launcher.InvalidExchangeStepException.FailureType.TRANSIENT

/** Finds an [ExchangeStep], validates it, and starts executing the work. */
class ExchangeStepLauncher(
  private val apiClient: ApiClient,
  private val validator: ExchangeStepValidator,
  private val jobLauncher: JobLauncher,
) {

  /**
   * Finds a single ready Exchange Step and starts executing. If an Exchange Step is found,
   * validates it, and starts executing. If not found simply returns.
   */
  suspend fun findAndRunExchangeStep() {
    val (exchangeStep, attemptKey) = apiClient.claimExchangeStep() ?: return

    try {
      val validatedExchangeStep = validator.validate(exchangeStep)
      jobLauncher.execute(validatedExchangeStep, attemptKey)
    } catch (e: Exception) {
      invalidateAttempt(attemptKey, e)
    }
  }

  private suspend fun invalidateAttempt(
    attemptKey: CanonicalExchangeStepAttemptKey,
    exception: Exception,
  ) {
    val state =
      when (exception) {
        is InvalidExchangeStepException ->
          when (exception.type) {
            PERMANENT -> ExchangeStepAttempt.State.FAILED_STEP
            TRANSIENT -> ExchangeStepAttempt.State.FAILED
          }
        else -> ExchangeStepAttempt.State.FAILED
      }

    // TODO: log an error or retry a few times if this fails.
    // TODO: add API-level support for some type of justification about what went wrong.
    apiClient.finishExchangeStepAttempt(attemptKey, state, listOfNotNull(exception.message))
  }
}
