# Variables for GCP GKE Infrastructure

variable "project_id" {
  description = "The GCP project ID"
  type        = string
}

variable "project_name" {
  description = "The name of the project"
  type        = string
  default     = "streamsense"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "prod"
}

variable "region" {
  description = "The GCP region"
  type        = string
  default     = "us-central1"
}

variable "zone" {
  description = "The GCP zone"
  type        = string
  default     = "us-central1-a"
}

variable "regional_cluster" {
  description = "Create a regional cluster (true) or zonal cluster (false)"
  type        = bool
  default     = true
}

# Network Configuration
variable "subnet_cidr" {
  description = "CIDR block for the subnet"
  type        = string
  default     = "10.0.0.0/16"
}

variable "pods_cidr" {
  description = "CIDR block for pods"
  type        = string
  default     = "10.1.0.0/16"
}

variable "services_cidr" {
  description = "CIDR block for services"
  type        = string
  default     = "10.2.0.0/16"
}

variable "master_cidr" {
  description = "CIDR block for the master nodes"
  type        = string
  default     = "10.3.0.0/28"
}

variable "authorized_networks" {
  description = "List of authorized networks that can access the cluster"
  type = list(object({
    cidr_block   = string
    display_name = string
  }))
  default = [
    {
      cidr_block   = "0.0.0.0/0"
      display_name = "All networks"
    }
  ]
}

# Node Pool Configuration
variable "infrastructure_node_count" {
  description = "Number of nodes in the infrastructure node pool"
  type        = number
  default     = 2
}

variable "infrastructure_machine_type" {
  description = "Machine type for infrastructure nodes"
  type        = string
  default     = "e2-standard-4"
}

variable "microservices_node_count" {
  description = "Number of nodes in the microservices node pool"
  type        = number
  default     = 3
}

variable "microservices_machine_type" {
  description = "Machine type for microservices nodes"
  type        = string
  default     = "e2-standard-8"
}

variable "ml_node_count" {
  description = "Number of nodes in the ML node pool"
  type        = number
  default     = 1
}

variable "ml_machine_type" {
  description = "Machine type for ML nodes"
  type        = string
  default     = "n1-standard-4"
}

variable "database_node_count" {
  description = "Number of nodes in the database node pool"
  type        = number
  default     = 3
}

variable "database_machine_type" {
  description = "Machine type for database nodes"
  type        = string
  default     = "n2-highmem-4"
}

# Autoscaling Configuration
variable "enable_cluster_autoscaling" {
  description = "Enable cluster autoscaling"
  type        = bool
  default     = true
}

variable "max_cpu_cores" {
  description = "Maximum CPU cores for cluster autoscaling"
  type        = number
  default     = 100
}

variable "max_memory_gb" {
  description = "Maximum memory GB for cluster autoscaling"
  type        = number
  default     = 400
}

# Security Configuration
variable "enable_network_policy" {
  description = "Enable network policy for the cluster"
  type        = bool
  default     = true
}

variable "enable_private_nodes" {
  description = "Enable private nodes"
  type        = bool
  default     = true
}

variable "enable_shielded_nodes" {
  description = "Enable shielded nodes"
  type        = bool
  default     = true
}

variable "enable_workload_identity" {
  description = "Enable Workload Identity"
  type        = bool
  default     = true
}

# Monitoring and Logging
variable "enable_monitoring" {
  description = "Enable monitoring"
  type        = bool
  default     = true
}

variable "enable_logging" {
  description = "Enable logging"
  type        = bool
  default     = true
}

variable "logging_service" {
  description = "Logging service to use"
  type        = string
  default     = "logging.googleapis.com/kubernetes"
}

variable "monitoring_service" {
  description = "Monitoring service to use"
  type        = string
  default     = "monitoring.googleapis.com/kubernetes"
}

# Release Channel
variable "release_channel" {
  description = "GKE release channel"
  type        = string
  default     = "STABLE"
  validation {
    condition = contains(["RAPID", "REGULAR", "STABLE"], var.release_channel)
    error_message = "Release channel must be RAPID, REGULAR, or STABLE."
  }
}

# Maintenance Window
variable "maintenance_start_time" {
  description = "Start time for maintenance window (RFC3339 format)"
  type        = string
  default     = "2024-01-01T02:00:00Z"
}

variable "maintenance_end_time" {
  description = "End time for maintenance window (RFC3339 format)"
  type        = string
  default     = "2024-01-01T06:00:00Z"
}

variable "maintenance_recurrence" {
  description = "Maintenance window recurrence"
  type        = string
  default     = "FREQ=WEEKLY;BYDAY=SU"
}

# Disk Configuration
variable "infrastructure_disk_size" {
  description = "Disk size for infrastructure nodes (GB)"
  type        = number
  default     = 50
}

variable "microservices_disk_size" {
  description = "Disk size for microservices nodes (GB)"
  type        = number
  default     = 100
}

variable "ml_disk_size" {
  description = "Disk size for ML nodes (GB)"
  type        = number
  default     = 200
}

variable "database_disk_size" {
  description = "Disk size for database nodes (GB)"
  type        = number
  default     = 500
}

variable "disk_type" {
  description = "Disk type for nodes"
  type        = string
  default     = "pd-ssd"
  validation {
    condition = contains(["pd-standard", "pd-ssd", "pd-balanced"], var.disk_type)
    error_message = "Disk type must be pd-standard, pd-ssd, or pd-balanced."
  }
}

# GPU Configuration
variable "enable_gpu_nodes" {
  description = "Enable GPU nodes for ML workloads"
  type        = bool
  default     = true
}

variable "gpu_type" {
  description = "GPU type for ML nodes"
  type        = string
  default     = "nvidia-tesla-t4"
}

variable "gpu_count" {
  description = "Number of GPUs per ML node"
  type        = number
  default     = 1
}

# BigQuery Configuration
variable "enable_resource_usage_export" {
  description = "Enable resource usage export to BigQuery"
  type        = bool
  default     = true
}

variable "bigquery_dataset_id" {
  description = "BigQuery dataset ID for resource usage export"
  type        = string
  default     = "gke_cluster_resource_usage"
}

# Binary Authorization
variable "enable_binary_authorization" {
  description = "Enable binary authorization"
  type        = bool
  default     = false
}

# Resource Labels
variable "labels" {
  description = "Labels to apply to resources"
  type        = map(string)
  default = {
    managed_by = "terraform"
    project    = "streamsense"
    component  = "gke-cluster"
  }
}

# Service Account Configuration
variable "create_service_account" {
  description = "Create a service account for the cluster"
  type        = bool
  default     = true
}

variable "service_account_name" {
  description = "Name of the service account"
  type        = string
  default     = ""
}

# Preemptible Nodes
variable "use_preemptible_nodes" {
  description = "Use preemptible nodes for cost savings"
  type        = bool
  default     = false
}

# Node Pool Management
variable "auto_repair" {
  description = "Enable auto repair for node pools"
  type        = bool
  default     = true
}

variable "auto_upgrade" {
  description = "Enable auto upgrade for node pools"
  type        = bool
  default     = true
}

variable "max_surge" {
  description = "Maximum surge during node pool upgrades"
  type        = number
  default     = 1
}

variable "max_unavailable" {
  description = "Maximum unavailable nodes during upgrades"
  type        = number
  default     = 0
}

# Network Tags
variable "node_tags" {
  description = "Network tags for nodes"
  type        = list(string)
  default     = ["gke-node"]
}

# Additional OAuth Scopes
variable "oauth_scopes" {
  description = "OAuth scopes for nodes"
  type        = list(string)
  default = [
    "https://www.googleapis.com/auth/cloud-platform"
  ]
}