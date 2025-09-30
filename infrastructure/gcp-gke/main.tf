# StreamSense Google GKE Infrastructure
# This Terraform configuration creates production-ready GKE infrastructure

terraform {
  required_version = ">= 1.0"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 4.84"
    }
    google-beta = {
      source  = "hashicorp/google-beta"
      version = "~> 4.84"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.23"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.11"
    }
  }
  
  backend "gcs" {
    bucket = "streamsense-terraform-state"
    prefix = "gke/terraform.tfstate"
  }
}

# Configure Google Provider
provider "google" {
  project = var.project_id
  region  = var.region
  zone    = var.zone
}

provider "google-beta" {
  project = var.project_id
  region  = var.region
  zone    = var.zone
}

# Data sources
data "google_client_config" "default" {}

# Local values
locals {
  cluster_name = "${var.project_name}-${var.environment}"
  
  network_name    = "${local.cluster_name}-vpc"
  subnet_name     = "${local.cluster_name}-subnet"
  secondary_ranges = {
    pods     = "${local.cluster_name}-pods"
    services = "${local.cluster_name}-services"
  }
}

# VPC Network
resource "google_compute_network" "vpc" {
  name                    = local.network_name
  auto_create_subnetworks = false
  mtu                     = 1460
  
  project = var.project_id
}

# Subnet
resource "google_compute_subnetwork" "subnet" {
  name          = local.subnet_name
  ip_cidr_range = var.subnet_cidr
  region        = var.region
  network       = google_compute_network.vpc.id
  
  # Secondary IP ranges for pods and services
  secondary_ip_range {
    range_name    = local.secondary_ranges.pods
    ip_cidr_range = var.pods_cidr
  }
  
  secondary_ip_range {
    range_name    = local.secondary_ranges.services
    ip_cidr_range = var.services_cidr
  }
  
  # Enable private Google access
  private_ip_google_access = true
  
  project = var.project_id
}

# Cloud Router for NAT
resource "google_compute_router" "router" {
  name    = "${local.cluster_name}-router"
  region  = var.region
  network = google_compute_network.vpc.id
  
  project = var.project_id
}

# Cloud NAT
resource "google_compute_router_nat" "nat" {
  name   = "${local.cluster_name}-nat"
  router = google_compute_router.router.name
  region = var.region
  
  nat_ip_allocate_option             = "AUTO_ONLY"
  source_subnetwork_ip_ranges_to_nat = "ALL_SUBNETWORKS_ALL_IP_RANGES"
  
  log_config {
    enable = true
    filter = "ERRORS_ONLY"
  }
  
  project = var.project_id
}

# Firewall rules
resource "google_compute_firewall" "allow_internal" {
  name    = "${local.cluster_name}-allow-internal"
  network = google_compute_network.vpc.name
  
  allow {
    protocol = "tcp"
    ports    = ["0-65535"]
  }
  
  allow {
    protocol = "udp"
    ports    = ["0-65535"]
  }
  
  allow {
    protocol = "icmp"
  }
  
  source_ranges = [var.subnet_cidr, var.pods_cidr, var.services_cidr]
  
  project = var.project_id
}

resource "google_compute_firewall" "allow_ssh" {
  name    = "${local.cluster_name}-allow-ssh"
  network = google_compute_network.vpc.name
  
  allow {
    protocol = "tcp"
    ports    = ["22"]
  }
  
  source_ranges = ["0.0.0.0/0"]
  target_tags   = ["ssh"]
  
  project = var.project_id
}

# Service Account for GKE nodes
resource "google_service_account" "gke_service_account" {
  account_id   = "${local.cluster_name}-gke-sa"
  display_name = "GKE Service Account for ${local.cluster_name}"
  project      = var.project_id
}

resource "google_project_iam_member" "gke_service_account" {
  for_each = toset([
    "roles/logging.logWriter",
    "roles/monitoring.metricWriter",
    "roles/monitoring.viewer",
    "roles/stackdriver.resourceMetadata.writer",
    "roles/storage.objectViewer"
  ])
  
  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.gke_service_account.email}"
}

# GKE Cluster
resource "google_container_cluster" "primary" {
  name     = local.cluster_name
  location = var.regional_cluster ? var.region : var.zone
  project  = var.project_id
  
  # Remove default node pool
  remove_default_node_pool = true
  initial_node_count       = 1
  
  # Network configuration
  network    = google_compute_network.vpc.self_link
  subnetwork = google_compute_subnetwork.subnet.self_link
  
  # IP allocation policy
  ip_allocation_policy {
    cluster_secondary_range_name  = local.secondary_ranges.pods
    services_secondary_range_name = local.secondary_ranges.services
  }
  
  # Network policy
  network_policy {
    enabled = true
  }
  
  # Private cluster configuration
  private_cluster_config {
    enable_private_nodes    = true
    enable_private_endpoint = false
    master_ipv4_cidr_block  = var.master_cidr
    
    master_global_access_config {
      enabled = true
    }
  }
  
  # Master authorized networks
  master_authorized_networks_config {
    dynamic "cidr_blocks" {
      for_each = var.authorized_networks
      content {
        cidr_block   = cidr_blocks.value.cidr_block
        display_name = cidr_blocks.value.display_name
      }
    }
  }
  
  # Addons
  addons_config {
    http_load_balancing {
      disabled = false
    }
    
    horizontal_pod_autoscaling {
      disabled = false
    }
    
    network_policy_config {
      disabled = false
    }
    
    gce_persistent_disk_csi_driver_config {
      enabled = true
    }
  }
  
  # Workload Identity
  workload_identity_config {
    workload_pool = "${var.project_id}.svc.id.goog"
  }
  
  # Binary authorization
  enable_binary_authorization = false
  
  # Shielded nodes
  enable_shielded_nodes = true
  
  # Logging and monitoring
  logging_service    = "logging.googleapis.com/kubernetes"
  monitoring_service = "monitoring.googleapis.com/kubernetes"
  
  # Resource usage export
  resource_usage_export_config {
    enable_network_egress_metering       = true
    enable_resource_consumption_metering = true
    
    bigquery_destination {
      dataset_id = "gke_cluster_resource_usage"
    }
  }
  
  # Maintenance policy
  maintenance_policy {
    recurring_window {
      start_time = "2024-01-01T02:00:00Z"
      end_time   = "2024-01-01T06:00:00Z"
      recurrence = "FREQ=WEEKLY;BYDAY=SU"
    }
  }
  
  # Cluster autoscaling
  cluster_autoscaling {
    enabled = true
    
    resource_limits {
      resource_type = "cpu"
      minimum       = 4
      maximum       = 100
    }
    
    resource_limits {
      resource_type = "memory"
      minimum       = 16
      maximum       = 400
    }
    
    auto_provisioning_defaults {
      service_account = google_service_account.gke_service_account.email
      oauth_scopes = [
        "https://www.googleapis.com/auth/cloud-platform"
      ]
    }
  }
  
  # Release channel
  release_channel {
    channel = "STABLE"
  }
  
  depends_on = [
    google_project_iam_member.gke_service_account
  ]
}

# Node Pools
resource "google_container_node_pool" "infrastructure" {
  name       = "infrastructure"
  location   = google_container_cluster.primary.location
  cluster    = google_container_cluster.primary.name
  project    = var.project_id
  
  node_count = 2
  
  autoscaling {
    min_node_count = 2
    max_node_count = 4
  }
  
  node_config {
    preemptible  = false
    machine_type = "e2-standard-4"
    disk_size_gb = 50
    disk_type    = "pd-ssd"
    
    service_account = google_service_account.gke_service_account.email
    oauth_scopes = [
      "https://www.googleapis.com/auth/cloud-platform"
    ]
    
    labels = {
      environment = var.environment
      node_pool   = "infrastructure"
    }
    
    taint {
      key    = "infrastructure"
      value  = "true"
      effect = "NO_SCHEDULE"
    }
    
    tags = ["infrastructure"]
    
    shielded_instance_config {
      enable_secure_boot          = true
      enable_integrity_monitoring = true
    }
    
    workload_metadata_config {
      mode = "GKE_METADATA"
    }
  }
  
  management {
    auto_repair  = true
    auto_upgrade = true
  }
  
  upgrade_settings {
    max_surge       = 1
    max_unavailable = 0
  }
}

resource "google_container_node_pool" "microservices" {
  name       = "microservices"
  location   = google_container_cluster.primary.location
  cluster    = google_container_cluster.primary.name
  project    = var.project_id
  
  node_count = 3
  
  autoscaling {
    min_node_count = 3
    max_node_count = 10
  }
  
  node_config {
    preemptible  = false
    machine_type = "e2-standard-8"
    disk_size_gb = 100
    disk_type    = "pd-ssd"
    
    service_account = google_service_account.gke_service_account.email
    oauth_scopes = [
      "https://www.googleapis.com/auth/cloud-platform"
    ]
    
    labels = {
      environment = var.environment
      node_pool   = "microservices"
    }
    
    tags = ["microservices"]
    
    shielded_instance_config {
      enable_secure_boot          = true
      enable_integrity_monitoring = true
    }
    
    workload_metadata_config {
      mode = "GKE_METADATA"
    }
  }
  
  management {
    auto_repair  = true
    auto_upgrade = true
  }
  
  upgrade_settings {
    max_surge       = 1
    max_unavailable = 0
  }
}

resource "google_container_node_pool" "ml_engine" {
  name       = "ml-engine"
  location   = google_container_cluster.primary.location
  cluster    = google_container_cluster.primary.name
  project    = var.project_id
  
  node_count = 1
  
  autoscaling {
    min_node_count = 1
    max_node_count = 5
  }
  
  node_config {
    preemptible  = false
    machine_type = "n1-standard-4"
    disk_size_gb = 200
    disk_type    = "pd-ssd"
    
    # GPU configuration
    guest_accelerator {
      type  = "nvidia-tesla-t4"
      count = 1
    }
    
    service_account = google_service_account.gke_service_account.email
    oauth_scopes = [
      "https://www.googleapis.com/auth/cloud-platform"
    ]
    
    labels = {
      environment   = var.environment
      node_pool     = "ml-engine"
      workload_type = "gpu"
    }
    
    taint {
      key    = "nvidia.com/gpu"
      value  = "true"
      effect = "NO_SCHEDULE"
    }
    
    tags = ["ml-engine"]
    
    shielded_instance_config {
      enable_secure_boot          = true
      enable_integrity_monitoring = true
    }
    
    workload_metadata_config {
      mode = "GKE_METADATA"
    }
  }
  
  management {
    auto_repair  = true
    auto_upgrade = true
  }
  
  upgrade_settings {
    max_surge       = 1
    max_unavailable = 0
  }
}

resource "google_container_node_pool" "database" {
  name       = "database"
  location   = google_container_cluster.primary.location
  cluster    = google_container_cluster.primary.name
  project    = var.project_id
  
  node_count = 3
  
  autoscaling {
    min_node_count = 3
    max_node_count = 6
  }
  
  node_config {
    preemptible  = false
    machine_type = "n2-highmem-4"
    disk_size_gb = 500
    disk_type    = "pd-ssd"
    
    service_account = google_service_account.gke_service_account.email
    oauth_scopes = [
      "https://www.googleapis.com/auth/cloud-platform"
    ]
    
    labels = {
      environment   = var.environment
      node_pool     = "database"
      workload_type = "database"
    }
    
    taint {
      key    = "database"
      value  = "true"
      effect = "NO_SCHEDULE"
    }
    
    tags = ["database"]
    
    shielded_instance_config {
      enable_secure_boot          = true
      enable_integrity_monitoring = true
    }
    
    workload_metadata_config {
      mode = "GKE_METADATA"
    }
  }
  
  management {
    auto_repair  = true
    auto_upgrade = true
  }
  
  upgrade_settings {
    max_surge       = 1
    max_unavailable = 0
  }
}