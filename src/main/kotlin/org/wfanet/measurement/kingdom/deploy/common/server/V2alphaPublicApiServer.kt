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

package org.wfanet.measurement.kingdom.deploy.common.server

import io.grpc.ServerServiceDefinition
import java.io.File
import kotlin.properties.Delegates
import org.wfanet.measurement.api.v2alpha.AkidPrincipalLookup
import org.wfanet.measurement.api.v2alpha.ProtocolConfig.NoiseMechanism
import org.wfanet.measurement.api.v2alpha.withPrincipalsFromX509AuthorityKeyIdentifiers
import org.wfanet.measurement.common.commandLineMain
import org.wfanet.measurement.common.crypto.SigningCerts
import org.wfanet.measurement.common.grpc.CommonServer
import org.wfanet.measurement.common.grpc.buildMutualTlsChannel
import org.wfanet.measurement.common.grpc.withDefaultDeadline
import org.wfanet.measurement.common.grpc.withVerboseLogging
import org.wfanet.measurement.common.identity.DuchyInfo
import org.wfanet.measurement.common.identity.DuchyInfoFlags
import org.wfanet.measurement.internal.kingdom.AccountsGrpcKt.AccountsCoroutineStub as InternalAccountsCoroutineStub
import org.wfanet.measurement.internal.kingdom.ApiKeysGrpcKt.ApiKeysCoroutineStub as InternalApiKeysCoroutineStub
import org.wfanet.measurement.internal.kingdom.CertificatesGrpcKt.CertificatesCoroutineStub as InternalCertificatesCoroutineStub
import org.wfanet.measurement.internal.kingdom.DataProvidersGrpcKt.DataProvidersCoroutineStub as InternalDataProvidersCoroutineStub
import org.wfanet.measurement.internal.kingdom.EventGroupMetadataDescriptorsGrpcKt.EventGroupMetadataDescriptorsCoroutineStub as InternalEventGroupMetadataDescriptorsCoroutineStub
import org.wfanet.measurement.internal.kingdom.EventGroupsGrpcKt.EventGroupsCoroutineStub as InternalEventGroupsCoroutineStub
import org.wfanet.measurement.internal.kingdom.ExchangeStepAttemptsGrpcKt.ExchangeStepAttemptsCoroutineStub as InternalExchangeStepAttemptsCoroutineStub
import org.wfanet.measurement.internal.kingdom.ExchangeStepsGrpcKt.ExchangeStepsCoroutineStub as InternalExchangeStepsCoroutineStub
import org.wfanet.measurement.internal.kingdom.ExchangesGrpcKt.ExchangesCoroutineStub as InternalExchangesCoroutineStub
import org.wfanet.measurement.internal.kingdom.MeasurementConsumersGrpcKt.MeasurementConsumersCoroutineStub as InternalMeasurementConsumersCoroutineStub
import org.wfanet.measurement.internal.kingdom.MeasurementsGrpcKt.MeasurementsCoroutineStub as InternalMeasurementsCoroutineStub
import org.wfanet.measurement.internal.kingdom.ModelLinesGrpcKt.ModelLinesCoroutineStub as InternalModelLinesCoroutineStub
import org.wfanet.measurement.internal.kingdom.ModelOutagesGrpcKt.ModelOutagesCoroutineStub as InternalModelOutagesCoroutineStub
import org.wfanet.measurement.internal.kingdom.ModelReleasesGrpcKt.ModelReleasesCoroutineStub as InternalModelReleasesCoroutineStub
import org.wfanet.measurement.internal.kingdom.ModelRolloutsGrpcKt.ModelRolloutsCoroutineStub as InternalModelRolloutsCoroutineStub
import org.wfanet.measurement.internal.kingdom.ModelShardsGrpcKt.ModelShardsCoroutineStub as InternalModelShardsCoroutineStub
import org.wfanet.measurement.internal.kingdom.ModelSuitesGrpcKt.ModelSuitesCoroutineStub as InternalModelSuitesCoroutineStub
import org.wfanet.measurement.internal.kingdom.PopulationsGrpcKt.PopulationsCoroutineStub as InternalPopulationsCoroutineStub
import org.wfanet.measurement.internal.kingdom.PublicKeysGrpcKt.PublicKeysCoroutineStub as InternalPublicKeysCoroutineStub
import org.wfanet.measurement.internal.kingdom.RecurringExchangesGrpcKt.RecurringExchangesCoroutineStub as InternalRecurringExchangesCoroutineStub
import org.wfanet.measurement.internal.kingdom.RequisitionsGrpcKt.RequisitionsCoroutineStub as InternalRequisitionsCoroutineStub
import org.wfanet.measurement.kingdom.deploy.common.Llv2ProtocolConfig
import org.wfanet.measurement.kingdom.deploy.common.Llv2ProtocolConfigFlags
import org.wfanet.measurement.kingdom.deploy.common.RoLlv2ProtocolConfig
import org.wfanet.measurement.kingdom.deploy.common.RoLlv2ProtocolConfigFlags
import org.wfanet.measurement.kingdom.service.api.v2alpha.AccountsService
import org.wfanet.measurement.kingdom.service.api.v2alpha.ApiKeysService
import org.wfanet.measurement.kingdom.service.api.v2alpha.CertificatesService
import org.wfanet.measurement.kingdom.service.api.v2alpha.DataProvidersService
import org.wfanet.measurement.kingdom.service.api.v2alpha.EventGroupMetadataDescriptorsService
import org.wfanet.measurement.kingdom.service.api.v2alpha.EventGroupsService
import org.wfanet.measurement.kingdom.service.api.v2alpha.ExchangeStepAttemptsService
import org.wfanet.measurement.kingdom.service.api.v2alpha.ExchangeStepsService
import org.wfanet.measurement.kingdom.service.api.v2alpha.ExchangesService
import org.wfanet.measurement.kingdom.service.api.v2alpha.MeasurementConsumersService
import org.wfanet.measurement.kingdom.service.api.v2alpha.MeasurementsService
import org.wfanet.measurement.kingdom.service.api.v2alpha.ModelLinesService
import org.wfanet.measurement.kingdom.service.api.v2alpha.ModelOutagesService
import org.wfanet.measurement.kingdom.service.api.v2alpha.ModelReleasesService
import org.wfanet.measurement.kingdom.service.api.v2alpha.ModelRolloutsService
import org.wfanet.measurement.kingdom.service.api.v2alpha.ModelShardsService
import org.wfanet.measurement.kingdom.service.api.v2alpha.ModelSuitesService
import org.wfanet.measurement.kingdom.service.api.v2alpha.PopulationsService
import org.wfanet.measurement.kingdom.service.api.v2alpha.PublicKeysService
import org.wfanet.measurement.kingdom.service.api.v2alpha.RequisitionsService
import org.wfanet.measurement.kingdom.service.api.v2alpha.withAccountAuthenticationServerInterceptor
import org.wfanet.measurement.kingdom.service.api.v2alpha.withApiKeyAuthenticationServerInterceptor
import picocli.CommandLine

private const val SERVER_NAME = "V2alphaPublicApiServer"

@CommandLine.Command(
  name = SERVER_NAME,
  description = ["Server daemon for Kingdom v2alpha public API services."],
  mixinStandardHelpOptions = true,
  showDefaultValues = true,
)
private fun run(
  @CommandLine.Mixin kingdomApiServerFlags: KingdomApiServerFlags,
  @CommandLine.Mixin commonServerFlags: CommonServer.Flags,
  @CommandLine.Mixin llv2ProtocolConfigFlags: Llv2ProtocolConfigFlags,
  @CommandLine.Mixin roLlv2ProtocolConfigFlags: RoLlv2ProtocolConfigFlags,
  @CommandLine.Mixin v2alphaFlags: V2alphaFlags,
  @CommandLine.Mixin duchyInfoFlags: DuchyInfoFlags,
) {
  Llv2ProtocolConfig.initializeFromFlags(llv2ProtocolConfigFlags)
  RoLlv2ProtocolConfig.initializeFromFlags(roLlv2ProtocolConfigFlags)
  DuchyInfo.initializeFromFlags(duchyInfoFlags)

  val clientCerts =
    SigningCerts.fromPemFiles(
      certificateFile = commonServerFlags.tlsFlags.certFile,
      privateKeyFile = commonServerFlags.tlsFlags.privateKeyFile,
      trustedCertCollectionFile = commonServerFlags.tlsFlags.certCollectionFile,
    )
  val channel =
    buildMutualTlsChannel(
        kingdomApiServerFlags.internalApiFlags.target,
        clientCerts,
        kingdomApiServerFlags.internalApiFlags.certHost,
      )
      .withVerboseLogging(kingdomApiServerFlags.debugVerboseGrpcClientLogging)
      .withDefaultDeadline(kingdomApiServerFlags.internalApiFlags.defaultDeadlineDuration)

  val principalLookup = AkidPrincipalLookup(v2alphaFlags.authorityKeyIdentifierToPrincipalMapFile)

  val internalAccountsCoroutineStub = InternalAccountsCoroutineStub(channel)
  val internalApiKeysCoroutineStub = InternalApiKeysCoroutineStub(channel)
  val internalRecurringExchangesCoroutineStub = InternalRecurringExchangesCoroutineStub(channel)
  val internalExchangeStepsCoroutineStub = InternalExchangeStepsCoroutineStub(channel)

  // TODO: do we need something similar to .withDuchyIdentities() for EDP and MC?
  val services: List<ServerServiceDefinition> =
    listOf(
      AccountsService(internalAccountsCoroutineStub, v2alphaFlags.redirectUri)
        .withAccountAuthenticationServerInterceptor(
          internalAccountsCoroutineStub,
          v2alphaFlags.redirectUri,
        ),
      ApiKeysService(InternalApiKeysCoroutineStub(channel))
        .withAccountAuthenticationServerInterceptor(
          internalAccountsCoroutineStub,
          v2alphaFlags.redirectUri,
        ),
      CertificatesService(InternalCertificatesCoroutineStub(channel))
        .withPrincipalsFromX509AuthorityKeyIdentifiers(principalLookup)
        .withApiKeyAuthenticationServerInterceptor(internalApiKeysCoroutineStub),
      DataProvidersService(InternalDataProvidersCoroutineStub(channel))
        .withPrincipalsFromX509AuthorityKeyIdentifiers(principalLookup)
        .withApiKeyAuthenticationServerInterceptor(internalApiKeysCoroutineStub),
      EventGroupsService(InternalEventGroupsCoroutineStub(channel))
        .withPrincipalsFromX509AuthorityKeyIdentifiers(principalLookup)
        .withApiKeyAuthenticationServerInterceptor(internalApiKeysCoroutineStub),
      EventGroupMetadataDescriptorsService(
          InternalEventGroupMetadataDescriptorsCoroutineStub(channel)
        )
        .withPrincipalsFromX509AuthorityKeyIdentifiers(principalLookup)
        .withApiKeyAuthenticationServerInterceptor(internalApiKeysCoroutineStub),
      MeasurementsService(
          InternalMeasurementsCoroutineStub(channel),
          v2alphaFlags.directNoiseMechanisms,
          v2alphaFlags.reachOnlyLlV2Enabled,
        )
        .withPrincipalsFromX509AuthorityKeyIdentifiers(principalLookup)
        .withApiKeyAuthenticationServerInterceptor(internalApiKeysCoroutineStub),
      MeasurementConsumersService(InternalMeasurementConsumersCoroutineStub(channel))
        .withPrincipalsFromX509AuthorityKeyIdentifiers(principalLookup)
        .withAccountAuthenticationServerInterceptor(
          internalAccountsCoroutineStub,
          v2alphaFlags.redirectUri,
        )
        .withApiKeyAuthenticationServerInterceptor(internalApiKeysCoroutineStub),
      PublicKeysService(InternalPublicKeysCoroutineStub(channel))
        .withPrincipalsFromX509AuthorityKeyIdentifiers(principalLookup)
        .withApiKeyAuthenticationServerInterceptor(internalApiKeysCoroutineStub),
      RequisitionsService(InternalRequisitionsCoroutineStub(channel))
        .withPrincipalsFromX509AuthorityKeyIdentifiers(principalLookup)
        .withApiKeyAuthenticationServerInterceptor(internalApiKeysCoroutineStub),
      ExchangesService(
          internalRecurringExchangesCoroutineStub,
          InternalExchangesCoroutineStub(channel),
        )
        .withPrincipalsFromX509AuthorityKeyIdentifiers(principalLookup),
      ExchangeStepsService(
          internalRecurringExchangesCoroutineStub,
          internalExchangeStepsCoroutineStub,
        )
        .withPrincipalsFromX509AuthorityKeyIdentifiers(principalLookup),
      ExchangeStepAttemptsService(
          InternalExchangeStepAttemptsCoroutineStub(channel),
          internalExchangeStepsCoroutineStub,
        )
        .withPrincipalsFromX509AuthorityKeyIdentifiers(principalLookup),
      ModelLinesService(InternalModelLinesCoroutineStub(channel))
        .withPrincipalsFromX509AuthorityKeyIdentifiers(principalLookup),
      ModelShardsService(InternalModelShardsCoroutineStub(channel))
        .withPrincipalsFromX509AuthorityKeyIdentifiers(principalLookup),
      ModelSuitesService(InternalModelSuitesCoroutineStub(channel))
        .withPrincipalsFromX509AuthorityKeyIdentifiers(principalLookup)
        .withApiKeyAuthenticationServerInterceptor(internalApiKeysCoroutineStub),
      ModelReleasesService(InternalModelReleasesCoroutineStub(channel))
        .withPrincipalsFromX509AuthorityKeyIdentifiers(principalLookup),
      ModelOutagesService(InternalModelOutagesCoroutineStub(channel))
        .withPrincipalsFromX509AuthorityKeyIdentifiers(principalLookup),
      ModelRolloutsService(InternalModelRolloutsCoroutineStub(channel))
        .withPrincipalsFromX509AuthorityKeyIdentifiers(principalLookup),
      PopulationsService(InternalPopulationsCoroutineStub(channel))
        .withPrincipalsFromX509AuthorityKeyIdentifiers(principalLookup),
    )
  CommonServer.fromFlags(commonServerFlags, SERVER_NAME, services).start().blockUntilShutdown()
}

fun main(args: Array<String>) = commandLineMain(::run, args)

/** Flags specific to the V2alpha API version. */
private class V2alphaFlags {

  @CommandLine.Option(
    names = ["--authority-key-identifier-to-principal-map-file"],
    description = ["File path to a AuthorityKeyToPrincipalMap textproto"],
    required = true,
  )
  lateinit var authorityKeyIdentifierToPrincipalMapFile: File
    private set

  @CommandLine.Option(
    names = ["--open-id-redirect-uri"],
    description = ["The redirect uri for OpenID Provider responses."],
    required = true,
  )
  lateinit var redirectUri: String
    private set

  lateinit var directNoiseMechanisms: List<NoiseMechanism>
    private set

  @CommandLine.Spec
  lateinit var spec: CommandLine.Model.CommandSpec // injected by picocli
    private set

  @set:CommandLine.Option(
    names = ["--enable-ro-llv2-protocol"],
    description = ["Whether to enable the Reach-Only Liquid Legions v2 protocol"],
    negatable = true,
    required = false,
    defaultValue = "false",
  )
  var reachOnlyLlV2Enabled by Delegates.notNull<Boolean>()
    private set

  @CommandLine.Option(
    names = ["--direct-noise-mechanism"],
    description =
      [
        "Noise mechanisms that can be used in direct computation. It can be specified multiple " +
          "times."
      ],
    required = true,
  )
  fun setDirectNoiseMechanisms(noiseMechanisms: List<NoiseMechanism>) {
    for (noiseMechanism in noiseMechanisms) {
      when (noiseMechanism) {
        NoiseMechanism.NONE,
        NoiseMechanism.CONTINUOUS_LAPLACE,
        NoiseMechanism.CONTINUOUS_GAUSSIAN -> {}
        NoiseMechanism.GEOMETRIC,
        // TODO(@riemanli): support DISCRETE_GAUSSIAN after having a clear definition of it.
        NoiseMechanism.DISCRETE_GAUSSIAN -> {
          throw CommandLine.ParameterException(
            spec.commandLine(),
            String.format(
              "Invalid noise mechanism $noiseMechanism for option '--direct-noise-mechanism'. " +
                "Discrete mechanisms are not supported for direct computations."
            ),
          )
        }
        NoiseMechanism.NOISE_MECHANISM_UNSPECIFIED,
        NoiseMechanism.UNRECOGNIZED -> {
          throw CommandLine.ParameterException(
            spec.commandLine(),
            String.format(
              "Invalid noise mechanism $noiseMechanism for option '--direct-noise-mechanism'."
            ),
          )
        }
      }
    }
    directNoiseMechanisms = noiseMechanisms
  }
}
