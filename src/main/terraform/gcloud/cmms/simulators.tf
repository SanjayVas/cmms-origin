# Copyright 2023 The Cross-Media Measurement Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

module "simulators_cluster" {
  source = "../modules/cluster"

  name       = local.simulators_cluster_name
  location   = local.cluster_location
  secret_key = module.common.cluster_secret_key
}

data "google_container_cluster" "simulators" {
  name     = local.simulators_cluster_name
  location = local.cluster_location

  # Defer reading of cluster resource until it exists.
  depends_on = [module.simulators_cluster]
}

module "simulators_default_node_pool" {
  source = "../modules/node-pool"

  name            = "default"
  cluster         = data.google_container_cluster.simulators
  service_account = module.common.cluster_service_account
  machine_type    = "e2-standard-2"
  max_node_count  = 2
}

module "simulators_spot_node_pool" {
  source = "../modules/node-pool"

  name            = "spot"
  cluster         = data.google_container_cluster.simulators
  service_account = module.common.cluster_service_account
  machine_type    = "c2-standard-4"
  max_node_count  = 3
  spot            = true
}

module "simulators" {
  source = "../modules/simulators"

  # TODO(hashicorp/terraform-provider-google#5693): Use data source once available.
  bigquery_table = {
    dataset_id = var.bigquery_dataset_id
    id         = var.bigquery_table_id
  }
}
