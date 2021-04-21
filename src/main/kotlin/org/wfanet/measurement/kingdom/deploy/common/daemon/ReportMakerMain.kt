// Copyright 2020 The Cross-Media Measurement Authors
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

package org.wfanet.measurement.kingdom.deploy.common.daemon

import org.wfanet.measurement.common.commandLineMain
import org.wfanet.measurement.kingdom.daemon.runReportMaker
import picocli.CommandLine

@CommandLine.Command(
  name = "report_maker",
  mixinStandardHelpOptions = true,
  showDefaultValues = true
)
private fun run(
  @CommandLine.Mixin flags: DaemonFlags,
  @CommandLine.Option(
    names = ["--combined-public-key-id"],
    description = ["ID of the CombinedPublicKey resource to associate with created reports."],
    required = true
  )
  combinedPublicKeyId: String
) {
  runDaemon(flags) { runReportMaker(combinedPublicKeyId) }
}

fun main(args: Array<String>) = commandLineMain(::run, args)
