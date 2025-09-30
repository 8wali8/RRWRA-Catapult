# Output values for GCP GKE Infrastructure

# Cluster Information
output "cluster_name" {
  description = "Name of the GKE cluster"
  value       = google_container_cluster.primary.name
}

output "cluster_endpoint" {
  description = "Endpoint for the GKE cluster"
  value       = google_container_cluster.primary.endpoint
  sensitive   = true
}

output "cluster_ca_certificate" {
  description = "Cluster CA certificate"
  value       = google_container_cluster.primary.master_auth[0].cluster_ca_certificate
  sensitive   = true
}

output "cluster_location" {
  description = "Location of the GKE cluster"
  value       = google_container_cluster.primary.location
}

output "cluster_zone" {
  description = "Zone of the GKE cluster"
  value       = google_container_cluster.primary.zone
}

output "cluster_region" {
  description = "Region of the GKE cluster"
  value       = var.region
}

# Network Information
output "network_name" {
  description = "Name of the VPC network"
  value       = google_compute_network.vpc.name
}

output "network_self_link" {
  description = "Self link of the VPC network"
  value       = google_compute_network.vpc.self_link
}

output "subnet_name" {
  description = "Name of the subnet"
  value       = google_compute_subnetwork.subnet.name
}

output "subnet_self_link" {
  description = "Self link of the subnet"
  value       = google_compute_subnetwork.subnet.self_link
}

output "subnet_cidr" {
  description = "CIDR block of the subnet"
  value       = google_compute_subnetwork.subnet.ip_cidr_range
}

output "pods_cidr" {
  description = "CIDR block for pods"
  value       = var.pods_cidr
}

output "services_cidr" {
  description = "CIDR block for services"
  value       = var.services_cidr
}

# Service Account Information
output "service_account_email" {
  description = "Email of the GKE service account"
  value       = google_service_account.gke_service_account.email
}

output "service_account_name" {
  description = "Name of the GKE service account"
  value       = google_service_account.gke_service_account.name
}

# Node Pool Information
output "infrastructure_node_pool_name" {
  description = "Name of the infrastructure node pool"
  value       = google_container_node_pool.infrastructure.name
}

output "microservices_node_pool_name" {
  description = "Name of the microservices node pool"
  value       = google_container_node_pool.microservices.name
}

output "ml_node_pool_name" {
  description = "Name of the ML node pool"
  value       = google_container_node_pool.ml_engine.name
}

output "database_node_pool_name" {
  description = "Name of the database node pool"
  value       = google_container_node_pool.database.name
}

# Kubernetes Configuration Commands
output "kubectl_config_command" {
  description = "Command to configure kubectl"
  value       = "gcloud container clusters get-credentials ${google_container_cluster.primary.name} --location ${google_container_cluster.primary.location} --project ${var.project_id}"
}

output "helm_install_command" {
  description = "Command to install StreamSense using Helm"
  value       = "helm upgrade --install streamsense ./helm/streamsense --namespace streamsense --create-namespace"
}

# Cluster Features
output "workload_identity_enabled" {
  description = "Whether Workload Identity is enabled"
  value       = length(google_container_cluster.primary.workload_identity_config) > 0
}

output "network_policy_enabled" {
  description = "Whether Network Policy is enabled"
  value       = google_container_cluster.primary.network_policy[0].enabled
}

output "private_cluster_enabled" {
  description = "Whether private cluster is enabled"
  value       = google_container_cluster.primary.private_cluster_config[0].enable_private_nodes
}

output "cluster_autoscaling_enabled" {
  description = "Whether cluster autoscaling is enabled"
  value       = google_container_cluster.primary.cluster_autoscaling[0].enabled
}

# Monitoring and Logging
output "logging_service" {
  description = "Logging service configured for the cluster"
  value       = google_container_cluster.primary.logging_service
}

output "monitoring_service" {
  description = "Monitoring service configured for the cluster"
  value       = google_container_cluster.primary.monitoring_service
}

# Security Information
output "master_ipv4_cidr_block" {
  description = "CIDR block for the master nodes"
  value       = google_container_cluster.primary.private_cluster_config[0].master_ipv4_cidr_block
}

output "cluster_ipv4_cidr" {
  description = "CIDR block for cluster pods"
  value       = google_container_cluster.primary.cluster_ipv4_cidr
}

output "services_ipv4_cidr" {
  description = "CIDR block for cluster services"
  value       = google_container_cluster.primary.services_ipv4_cidr
}

# Load Balancer Information
output "ingress_ip_name" {
  description = "Name for the ingress IP address"
  value       = "${local.cluster_name}-ingress-ip"
}

# Project Information
output "project_id" {
  description = "GCP Project ID"
  value       = var.project_id
}

output "project_name" {
  description = "Project name"
  value       = var.project_name
}

output "environment" {
  description = "Environment"
  value       = var.environment
}

# Terraform State Information
output "terraform_state_bucket" {
  description = "GCS bucket for Terraform state"
  value       = "streamsense-terraform-state"
}

# DNS Configuration (for setting up external DNS)
output "dns_zone_name" {
  description = "Recommended DNS zone name"
  value       = "${var.project_name}-${var.environment}"
}

# Certificate Management
output "cert_manager_namespace" {
  description = "Namespace for cert-manager"
  value       = "cert-manager"
}

# Istio Service Mesh (if using)
output "istio_namespace" {
  description = "Namespace for Istio"
  value       = "istio-system"
}

# Application Namespace
output "app_namespace" {
  description = "Namespace for StreamSense application"
  value       = "streamsense"
}

# Node Pool Configurations Summary
output "node_pools_summary" {
  description = "Summary of node pools configuration"
  value = {
    infrastructure = {
      name         = google_container_node_pool.infrastructure.name
      machine_type = "e2-standard-4"
      min_nodes    = 2
      max_nodes    = 4
      disk_size    = 50
      taints       = ["infrastructure=true:NoSchedule"]
    }
    microservices = {
      name         = google_container_node_pool.microservices.name
      machine_type = "e2-standard-8"
      min_nodes    = 3
      max_nodes    = 10
      disk_size    = 100
      taints       = []
    }
    ml_engine = {
      name         = google_container_node_pool.ml_engine.name
      machine_type = "n1-standard-4"
      min_nodes    = 1
      max_nodes    = 5
      disk_size    = 200
      gpu_type     = "nvidia-tesla-t4"
      gpu_count    = 1
      taints       = ["nvidia.com/gpu=true:NoSchedule"]
    }
    database = {
      name         = google_container_node_pool.database.name
      machine_type = "n2-highmem-4"
      min_nodes    = 3
      max_nodes    = 6
      disk_size    = 500
      taints       = ["database=true:NoSchedule"]
    }
  }
}

# Deployment Instructions
output "deployment_instructions" {
  description = "Step-by-step deployment instructions"
  value = <<-EOT
# StreamSense GKE Deployment Instructions

## 1. Configure kubectl
${local.kubectl_config_command}

## 2. Verify cluster access
kubectl cluster-info
kubectl get nodes

## 3. Install required operators
kubectl apply -f https://github.com/jetstack/cert-manager/releases/download/v1.13.2/cert-manager.yaml
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.8.2/deploy/static/provider/cloud/deploy.yaml

## 4. Deploy StreamSense
${local.helm_install_command}

## 5. Check deployment status
kubectl get pods -n streamsense
kubectl get services -n streamsense
kubectl get ingress -n streamsense

## 6. Access the application
kubectl port-forward -n streamsense svc/api-gateway 8080:8080
# Application will be available at http://localhost:8080
EOT
}

# Local values for output interpolation
locals {
  kubectl_config_command = "gcloud container clusters get-credentials ${google_container_cluster.primary.name} --location ${google_container_cluster.primary.location} --project ${var.project_id}"
  helm_install_command   = "helm upgrade --install streamsense ./helm/streamsense --namespace streamsense --create-namespace"
}