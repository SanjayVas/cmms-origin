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

package k8s

import (
	"strings"
)

listObject: {
	apiVersion: "v1"
	kind:       "List"
	items:      objects
}

objects: [ for objectSet in objectSets for object in objectSet {object}]

#AppName: "halo-cmms"

#GrpcServicePort: 8443

#ResourceConfig: {
	replicas:              int
	resourceRequestCpu:    string
	resourceLimitCpu:      string
	resourceRequestMemory: string
	resourceLimitMemory:   string
}

#Target: {
	name:   string
	_caps:  strings.Replace(strings.ToUpper(name), "-", "_", -1)
	target: "$(" + _caps + "_SERVICE_HOST):$(" + _caps + "_SERVICE_PORT)"
}

#SecretMount: {
	name:       string
	secretName: string
	mountPath:  string | *"/var/run/secrets/files"
}

#ConfigMapMount: {
	name:          string
	configMapName: string | *name
	mountPath:     string | *"/etc/\(#AppName)/\(name)"
}

#GrpcService: {
	_name:      string
	_system:    string
	_type:      *"ClusterIP" | "LoadBalancer"
	apiVersion: "v1"
	kind:       "Service"
	metadata: {
		name: _name
		annotations: {
			system:                             _system
			"cloud.google.com/app-protocols":   '{"grpc-port":"HTTP2"}'
			"kubernetes.io/ingress.allow-http": "false"
		}
		labels: {
			"app.kubernetes.io/name":      _name
			"app.kubernetes.io/part-of":   #AppName
			"app.kubernetes.io/component": _system
		}
	}
	spec: {
		selector: app: _name + "-app"
		type: _type
		ports: [{
			name:       "grpc-port"
			port:       #GrpcServicePort
			protocol:   "TCP"
			targetPort: #GrpcServicePort
		}]
	}
}

#PodSpec: PodSpec={
	_secretMounts: [...#SecretMount]
	_configMapMounts: [...#ConfigMapMount]
	_container: #Container & {
		_secretMounts:    PodSpec._secretMounts
		_configMapMounts: PodSpec._configMapMounts
	}
	_dependencies: [...string]

	restartPolicy: "Always" | "Never" | "OnFailure"
	containers: [_container]
	volumes: [ for secretVolume in _secretMounts {
		name: secretVolume.name
		secret: secretName: secretVolume.secretName
	}] + [ for configVolume in _configMapMounts {
		name: configVolume.name
		configMap: name: configVolume.configMapName
	}]
	serviceAccountName?: string
	nodeSelector?: [_=string]: string
	initContainers: [ for ds in _dependencies {
		name:  "init-\(ds)"
		image: "gcr.io/google-containers/busybox:1.27"
		command: ["sh", "-c", "until nslookup \(ds); do echo waiting for \(ds); sleep 2; done"]
	}]
	...
}

#Container: {
	_secretMounts: [...#SecretMount]
	_configMapMounts: [...#ConfigMapMount]

	imagePullPolicy: "IfNotPresent" | "Never" | "Always"
	volumeMounts: [ for mount in _configMapMounts + _secretMounts {
		name:      mount.name
		mountPath: mount.mountPath
		readOnly:  true
	}]
	readinessProbe?: {
		exec: command: [...string]
		periodSeconds: uint32
	}
	...
}

#Deployment: Deployment={
	_name:       string
	_replicas:   int
	_secretName: string
	_image:      string
	_args: [...string]
	_ports:           [{containerPort: #GrpcServicePort}] | *[]
	_restartPolicy:   string | *"Always"
	_imagePullPolicy: string | *"Never"
	_system:          string
	_jvm_flags:       string | *""
	_dependencies: [...string]
	_resourceRequestCpu:    string
	_resourceLimitCpu:      string
	_resourceRequestMemory: string
	_resourceLimitMemory:   string
	_configMapMounts: [...#ConfigMapMount]
	_podSpec: #PodSpec & {
		_secretMounts: [{
			name:       _name + "-files"
			secretName: _secretName
		}]
		_configMapMounts: Deployment._configMapMounts
		_dependencies: Deployment._dependencies
	}

	apiVersion: "apps/v1"
	kind:       "Deployment"
	metadata: {
		name: _name + "-deployment"
		labels: {
			app:                           _name + "-app"
			"app.kubernetes.io/name":      _name
			"app.kubernetes.io/part-of":   #AppName
			"app.kubernetes.io/component": _system
		}
		annotations: system: _system
	}
	spec: {
		replicas: _replicas
		selector: matchLabels: app: _name + "-app"
		template: {
			metadata: labels: app: _name + "-app"
			spec: _podSpec & {
				containers: [{
					name:  _name + "-container"
					image: _image
					resources: requests: {
						memory: _resourceRequestMemory
						cpu:    _resourceRequestCpu
					}
					resources: limits: {
						memory: _resourceLimitMemory
						cpu:    _resourceLimitCpu
					}
					imagePullPolicy: _imagePullPolicy
					args:            _args
					ports:           _ports
					env: [{
						name:  "JAVA_TOOL_OPTIONS"
						value: _jvm_flags
					}]
				}]
				restartPolicy: _restartPolicy
			}
		}
	}
}

#ServerDeployment: #Deployment & {
	_ports: [{containerPort: #GrpcServicePort}]
	spec: template: spec: containers: [{
		readinessProbe: {
			exec: command: [
				"/app/grpc_health_probe/file/grpc-health-probe",
				"--addr=:\(#GrpcServicePort)",
				"--tls=true",
				"--tls-ca-cert=/var/run/secrets/files/all_root_certs.pem",
				"--tls-client-cert=/var/run/secrets/files/health_probe_tls.pem",
				"--tls-client-key=/var/run/secrets/files/health_probe_tls.key",
			]
			periodSeconds: 60
		}}]
}

#Job: Job={
	_name:            string
	_secretName?:     string
	_image:           string
	_imagePullPolicy: string | *"Always"
	_args: [...string]
	_dependencies: [...string]
	_resourceRequestCpu:    string | *"100m"
	_resourceLimitCpu:      string | *"200m"
	_resourceRequestMemory: string | *"256Mi"
	_resourceLimitMemory:   string | *"512Mi"
	_jobSpec: {
		backoffLimit?: uint
	}
	_podSpec: #PodSpec & {
		if _secretName != _|_ {
			_secretMounts: [{
				name:       _name + "-files"
				secretName: _secretName
			}]
		}
		_dependencies: Job._dependencies

		restartPolicy: string | *"OnFailure"
	}

	apiVersion: "batch/v1"
	kind:       "Job"
	metadata: {
		name: _name + "-job"
		labels: {
			"app.kubernetes.io/name":    _name
			"app.kubernetes.io/part-of": #AppName
		}
	}
	spec: _jobSpec & {
		template: {
			metadata: labels: app: _name + "-app"
			spec: _podSpec & {
				containers: [{
					name:            _name + "-container"
					image:           _image
					imagePullPolicy: _imagePullPolicy
					args:            _args
					resources: requests: {
						memory: _resourceRequestMemory
						cpu:    _resourceRequestCpu
					}
					resources: limits: {
						memory: _resourceLimitMemory
						cpu:    _resourceLimitCpu
					}
				}]
			}
		}
	}
}

#NetworkPolicyPort: {
	port?:     uint32 | string
	protocol?: "TCP" | "UDP" | "SCTP"
}

#EgressRule: {
	to: [...]
	ports: [...#NetworkPolicyPort]
}

#IngressRule: {
	from: [...]
	ports: [...#NetworkPolicyPort]
}

// NetworkPolicy allows for selectively enabling traffic between pods
// https://kubernetes.io/docs/concepts/services-networking/network-policies/#networkpolicy-resource
//
// This structure allows configuring a NetworkPolicy that selects on a pod name and it
// will allow all traffic from pods matching _sourceMatchLabels to pods matching _destinationMatchLabels
//
#NetworkPolicy: {
	_name:      string
	_app_label: string
	_sourceMatchLabels: [...string]
	_destinationMatchLabels: [...string]
	_ingresses: [Name=_]: #IngressRule
	_egresses: [Name=_]:  #EgressRule

	_ingresses: {
		if len(_sourceMatchLabels) > 0 {
			pods: {
				from: [ for appLabel in _sourceMatchLabels {
					podSelector: matchLabels: app: appLabel
				}]
			}
		}
	}
	_egresses: {
		if len(_destinationMatchLabels) > 0 {
			pods: {
				to: [ for appLabel in _destinationMatchLabels {
					podSelector: matchLabels: app: appLabel
				}]
				ports: [{
					protocol: "TCP"
					port:     #GrpcServicePort
				}]
			}
		}
		dns: {
			to: [{
				namespaceSelector: {} // Allow DNS only inside the cluster
				podSelector: matchLabels: "k8s-app": "kube-dns"
			}]
			ports: [{
				protocol: "UDP"
				port:     53
			}, {
				protocol: "TCP"
				port:     53
			}]
		}
	}

	apiVersion: "networking.k8s.io/v1"
	kind:       "NetworkPolicy"
	metadata: {
		name: _name + "-network-policy"
		labels: {
			"app.kubernetes.io/part-of": #AppName
		}
	}
	spec: {
		podSelector: matchLabels: app: _app_label
		policyTypes: ["Ingress", "Egress"]
		ingress: [ for _, ingress in _ingresses {ingress}]
		egress: [ for _, egress in _egresses {egress}]
	}
}

// This policy will deny ingress and egress traffic at all unconfigured pods.
default_deny_ingress_and_egress: [{
	apiVersion: "networking.k8s.io/v1"
	kind:       "NetworkPolicy"
	metadata: {
		name: "default-deny-ingress-and-egress"
		labels: {
			"app.kubernetes.io/part-of": #AppName
		}
	}
	spec: {
		podSelector: {}
		policyTypes: ["Ingress", "Egress"]
	}
}]

// A simple fanout Ingress base definition
#Ingress: {
	_name:         string
	_host:         string
	_ingressClass: string
	_services: [...{name: string, port: int, path: string}]
	_pathType:  string
	apiVersion: "networking.k8s.io/v1"
	kind:       "Ingress"
	metadata: {
		name: _name + "-ingress"
		annotations: {
			"kubernetes.io/ingress.class": _ingressClass
		}
		labels: {
			"app.kubernetes.io/part-of": #AppName
		}
	}
	spec: {
		rules: [{
			host: _host
			http: paths: [ for s in _services {
				path:     s.path
				pathType: _pathType
				backend: service: {
					name: s.name
					port: number: s.port
				}
			}]
		}]
	}
}
