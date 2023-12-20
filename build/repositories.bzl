# Copyright 2021 The Cross-Media Measurement Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
Adds external repos necessary for wfa_measurement_system.
"""

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_file")
load("//build/wfa:repositories.bzl", "wfa_repo_archive")

MEASUREMENT_SYSTEM_REPO = "https://github.com/world-federation-of-advertisers/cross-media-measurement"

def wfa_measurement_system_repositories():
    """Imports all direct dependencies for wfa_measurement_system."""

    wfa_repo_archive(
        name = "wfa_common_jvm",
        repo = "common-jvm",
        sha256 = "00b66cb6f7bb8b23a0fc4063b0d454a150b1babd4aa49f6898fd51b7cdd086cd",
        version = "0.71.2",
    )

    wfa_repo_archive(
        name = "wfa_common_cpp",
        repo = "common-cpp",
        sha256 = "43398c7b44f692ef4a189afb674f27fa22e407797074a91d6db79908e6775162",
        version = "0.10.3",
    )

    wfa_repo_archive(
        name = "wfa_measurement_proto",
        repo = "cross-media-measurement-api",
        sha256 = "28e5f3e6699518e8a5b5454f5e567fae84f81d2475577f5b824a456cccdf913f",
        version = "0.54.0",
    )

    wfa_repo_archive(
        name = "wfa_rules_swig",
        commit = "653d1bdcec85a9373df69920f35961150cf4b1b6",
        repo = "rules_swig",
        sha256 = "34c15134d7293fc38df6ed254b55ee912c7479c396178b7f6499b7e5351aeeec",
    )

    wfa_repo_archive(
        name = "any_sketch",
        repo = "any-sketch",
        sha256 = "6abb78badf089502261cdb7cc2bc9e3397781e1f35287ace099da59f972bb014",
        version = "0.4.5",
    )

    wfa_repo_archive(
        name = "any_sketch_java",
        repo = "any-sketch-java",
        sha256 = "282195c2df2a53999c99ba86ead543865c8fff77cca24820870f44cff4531d9b",
        version = "0.4.2",
    )

    wfa_repo_archive(
        name = "wfa_rules_cue",
        repo = "rules_cue",
        sha256 = "0261b7797fa9083183536667958b1094fc732725fc48fca5cb68e6f731cdce2f",
        version = "0.3.0",
    )

    wfa_repo_archive(
        name = "wfa_consent_signaling_client",
        repo = "consent-signaling-client",
        sha256 = "28fab8c5facc265678dc54fe7a8ef59ca51d98b02a9f34df993731fe5c5b87e4",
        version = "0.19.0",
    )

    wfa_repo_archive(
        name = "wfa_virtual_people_common",
        repo = "virtual-people-common",
        sha256 = "89f22bc07ba8d8271f58454d603028ce09c01654ac92537076e45bb726c0ca60",
        version = "0.3.0",
    )

    http_archive(
        name = "private_membership",
        sha256 = "b1e0e7f74f4da09a6011c6fa91d7b968cdff6bb571712490dae427704b2af14c",
        strip_prefix = "private-membership-84e45669f7357bffcdafbc1b0cc26e72512808ce",
        url = "https://github.com/google/private-membership/archive/84e45669f7357bffcdafbc1b0cc26e72512808ce.zip",
    )

    http_archive(
        name = "io_bazel_rules_go",
        sha256 = "d6ab6b57e48c09523e93050f13698f708428cfd5e619252e369d377af6597707",
        urls = [
            "https://mirror.bazel.build/github.com/bazelbuild/rules_go/releases/download/v0.43.0/rules_go-v0.43.0.zip",
            "https://github.com/bazelbuild/rules_go/releases/download/v0.43.0/rules_go-v0.43.0.zip",
        ],
    )

    http_archive(
        name = "bazel_gazelle",
        sha256 = "b7387f72efb59f876e4daae42f1d3912d0d45563eac7cb23d1de0b094ab588cf",
        urls = [
            "https://mirror.bazel.build/github.com/bazelbuild/bazel-gazelle/releases/download/v0.34.0/bazel-gazelle-v0.34.0.tar.gz",
            "https://github.com/bazelbuild/bazel-gazelle/releases/download/v0.34.0/bazel-gazelle-v0.34.0.tar.gz",
        ],
    )

    http_file(
        name = "protoc_gen_grpc_gateway",
        sha256 = "3c21171d880f44e50223a3415d81cc3e359ac40360a58aabc3a6d7325d75ebc9",
        urls = ["https://github.com/grpc-ecosystem/grpc-gateway/releases/download/v2.18.1/protoc-gen-grpc-gateway-v2.18.1-linux-x86_64"],
        executable = True,
        downloaded_file_path = "protoc-gen-grpc-gateway",
    )

    http_archive(
        name = "grpc_ecosystem_grpc_gateway",
        sha256 = "440e2cb28f5bb84169f2956c822f7c53740b17d96bc51e2f378eced1c0f63219",
        strip_prefix = "grpc-gateway-2.18.1",
        urls = ["https://github.com/grpc-ecosystem/grpc-gateway/archive/refs/tags/v2.18.1.tar.gz"],
    )

    http_archive(
        name = "aspect_bazel_lib",
        sha256 = "4d6010ca5e3bb4d7045b071205afa8db06ec11eb24de3f023d74d77cca765f66",
        strip_prefix = "bazel-lib-1.39.0",
        url = "https://github.com/aspect-build/bazel-lib/releases/download/v1.39.0/bazel-lib-v1.39.0.tar.gz",
    )

    http_archive(
        name = "aspect_rules_js",
        sha256 = "dcd1567d4a93a8634ec0b888b371a60b93c18d980f77dace02eb176531a71fcf",
        strip_prefix = "rules_js-1.26.0",
        url = "https://github.com/aspect-build/rules_js/releases/download/v1.26.0/rules_js-v1.26.0.tar.gz",
    )

    http_archive(
        name = "aspect_rules_ts",
        sha256 = "ace5b609603d9b5b875d56c9c07182357c4ee495030f40dcefb10d443ba8c208",
        strip_prefix = "rules_ts-1.4.0",
        url = "https://github.com/aspect-build/rules_ts/releases/download/v1.4.0/rules_ts-v1.4.0.tar.gz",
    )

    http_archive(
        name = "aspect_rules_jest",
        sha256 = "d3bb833f74b8ad054e6bff5e41606ff10a62880cc99e4d480f4bdfa70add1ba7",
        strip_prefix = "rules_jest-0.18.4",
        url = "https://github.com/aspect-build/rules_jest/releases/download/v0.18.4/rules_jest-v0.18.4.tar.gz",
    )

    http_archive(
        name = "aspect_rules_webpack",
        sha256 = "78d05d9e87ee804accca80a4fec98a66f146b6058e915eae3d97190397ad12df",
        strip_prefix = "rules_webpack-0.12.0",
        url = "https://github.com/aspect-build/rules_webpack/releases/download/v0.12.0/rules_webpack-v0.12.0.tar.gz",
    )
