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
import org.wfanet.panelmatch.client.launcher.ExchangeStepValidator.ValidatedExchangeStep

/** Executes an [ExchangeStep]. This may be locally or remotely. */
interface JobLauncher {
  /**
   * Initiates work on [step].
   *
   * This may return before the work is complete.
   *
   * This could run [step] in-process or enqueue/start work remotely, e.g. via an RPC call.
   */
  suspend fun execute(step: ValidatedExchangeStep, attemptKey: CanonicalExchangeStepAttemptKey)
}
