# Input Variables for StreamSense EKS Infrastructure

variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
  default     = "us-west-2"
}

variable "project_name" {
  description = "Name of the project"
  type        = string
  default     = "streamsense"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "prod"
  
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be one of: dev, staging, prod."
  }
}

variable "kubernetes_version" {
  description = "Kubernetes version for EKS cluster"
  type        = string
  default     = "1.28"
}

variable "node_group_instance_types" {
  description = "EC2 instance types for EKS node groups"
  type = object({
    infrastructure = list(string)
    microservices  = list(string)
    ml_engine      = list(string)
    database       = list(string)
  })
  default = {
    infrastructure = ["m5.large", "m5.xlarge"]
    microservices  = ["m5.xlarge", "m5.2xlarge"]
    ml_engine      = ["g4dn.xlarge", "g4dn.2xlarge"]
    database       = ["r5.xlarge", "r5.2xlarge"]
  }
}

variable "node_group_scaling_config" {
  description = "Scaling configuration for node groups"
  type = object({
    infrastructure = object({
      min_size     = number
      max_size     = number
      desired_size = number
    })
    microservices = object({
      min_size     = number
      max_size     = number
      desired_size = number
    })
    ml_engine = object({
      min_size     = number
      max_size     = number
      desired_size = number
    })
    database = object({
      min_size     = number
      max_size     = number
      desired_size = number
    })
  })
  default = {
    infrastructure = {
      min_size     = 2
      max_size     = 4
      desired_size = 2
    }
    microservices = {
      min_size     = 3
      max_size     = 10
      desired_size = 5
    }
    ml_engine = {
      min_size     = 1
      max_size     = 5
      desired_size = 2
    }
    database = {
      min_size     = 3
      max_size     = 6
      desired_size = 3
    }
  }
}

variable "enable_cluster_autoscaler" {
  description = "Enable cluster autoscaler"
  type        = bool
  default     = true
}

variable "enable_aws_load_balancer_controller" {
  description = "Enable AWS Load Balancer Controller"
  type        = bool
  default     = true
}

variable "enable_metrics_server" {
  description = "Enable metrics server"
  type        = bool
  default     = true
}

variable "enable_prometheus_monitoring" {
  description = "Enable Prometheus monitoring stack"
  type        = bool
  default     = true
}

variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "List of availability zones"
  type        = list(string)
  default     = ["us-west-2a", "us-west-2b", "us-west-2c"]
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for private subnets"
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets"
  type        = list(string)
  default     = ["10.0.4.0/24", "10.0.5.0/24", "10.0.6.0/24"]
}

variable "cluster_endpoint_public_access" {
  description = "Enable public access to cluster endpoint"
  type        = bool
  default     = true
}

variable "cluster_endpoint_private_access" {
  description = "Enable private access to cluster endpoint"
  type        = bool
  default     = true
}

variable "cluster_endpoint_public_access_cidrs" {
  description = "List of CIDR blocks that can access the cluster endpoint publicly"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "map_roles" {
  description = "Additional IAM roles to add to the aws-auth configmap"
  type = list(object({
    rolearn  = string
    username = string
    groups   = list(string)
  }))
  default = []
}

variable "map_users" {
  description = "Additional IAM users to add to the aws-auth configmap"
  type = list(object({
    userarn  = string
    username = string
    groups   = list(string)
  }))
  default = []
}

variable "tags" {
  description = "Additional tags for all resources"
  type        = map(string)
  default     = {}
}