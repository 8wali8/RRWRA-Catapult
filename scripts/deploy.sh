#!/bin/bash

# StreamSense Enterprise Deployment Script
# Deploys to both AWS EKS and GCP GKE with comprehensive validation

set -euo pipefail

# Configuration
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENVIRONMENT="${ENVIRONMENT:-prod}"
CLOUD_PROVIDER="${CLOUD_PROVIDER:-both}"  # aws, gcp, or both
HELM_TIMEOUT="${HELM_TIMEOUT:-600s}"
VALIDATION_TIMEOUT="${VALIDATION_TIMEOUT:-300}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."
    
    local missing_tools=()
    
    # Check required tools
    command -v kubectl >/dev/null 2>&1 || missing_tools+=("kubectl")
    command -v helm >/dev/null 2>&1 || missing_tools+=("helm")
    command -v terraform >/dev/null 2>&1 || missing_tools+=("terraform")
    
    if [[ "$CLOUD_PROVIDER" == "aws" || "$CLOUD_PROVIDER" == "both" ]]; then
        command -v aws >/dev/null 2>&1 || missing_tools+=("aws-cli")
    fi
    
    if [[ "$CLOUD_PROVIDER" == "gcp" || "$CLOUD_PROVIDER" == "both" ]]; then
        command -v gcloud >/dev/null 2>&1 || missing_tools+=("gcloud")
    fi
    
    if [ ${#missing_tools[@]} -ne 0 ]; then
        log_error "Missing required tools: ${missing_tools[*]}"
        exit 1
    fi
    
    log_success "All prerequisites satisfied"
}

# Deploy to AWS EKS
deploy_aws_eks() {
    log_info "Deploying to AWS EKS..."
    
    cd "$PROJECT_ROOT/infrastructure/aws-eks"
    
    # Initialize and apply Terraform
    log_info "Initializing Terraform for AWS EKS..."
    terraform init
    
    log_info "Planning Terraform deployment..."
    terraform plan -out=tfplan
    
    log_info "Applying Terraform configuration..."
    terraform apply tfplan
    
    # Configure kubectl
    log_info "Configuring kubectl for EKS..."
    local cluster_name
    cluster_name=$(terraform output -raw cluster_name)
    local region
    region=$(terraform output -raw region)
    
    aws eks update-kubeconfig --region "$region" --name "$cluster_name"
    
    # Verify cluster access
    log_info "Verifying cluster access..."
    kubectl cluster-info
    kubectl get nodes
    
    log_success "AWS EKS infrastructure deployed successfully"
}

# Deploy to GCP GKE
deploy_gcp_gke() {
    log_info "Deploying to GCP GKE..."
    
    cd "$PROJECT_ROOT/infrastructure/gcp-gke"
    
    # Check for required GCP project ID
    if [ -z "${GOOGLE_PROJECT_ID:-}" ]; then
        log_error "GOOGLE_PROJECT_ID environment variable is required for GCP deployment"
        exit 1
    fi
    
    # Initialize and apply Terraform
    log_info "Initializing Terraform for GCP GKE..."
    terraform init
    
    log_info "Planning Terraform deployment..."
    terraform plan -var="project_id=$GOOGLE_PROJECT_ID" -out=tfplan
    
    log_info "Applying Terraform configuration..."
    terraform apply tfplan
    
    # Configure kubectl
    log_info "Configuring kubectl for GKE..."
    local cluster_name
    cluster_name=$(terraform output -raw cluster_name)
    local region
    region=$(terraform output -raw cluster_region)
    
    gcloud container clusters get-credentials "$cluster_name" --region "$region" --project "$GOOGLE_PROJECT_ID"
    
    # Verify cluster access
    log_info "Verifying cluster access..."
    kubectl cluster-info
    kubectl get nodes
    
    log_success "GCP GKE infrastructure deployed successfully"
}

# Install required Kubernetes operators
install_operators() {
    log_info "Installing required Kubernetes operators..."
    
    # Install cert-manager
    log_info "Installing cert-manager..."
    kubectl apply -f https://github.com/jetstack/cert-manager/releases/download/v1.13.2/cert-manager.yaml
    kubectl wait --for=condition=ready pod -l app=cert-manager -n cert-manager --timeout=300s
    
    # Install NGINX Ingress Controller
    log_info "Installing NGINX Ingress Controller..."
    kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.8.2/deploy/static/provider/cloud/deploy.yaml
    kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=ingress-nginx -n ingress-nginx --timeout=300s
    
    # Install Prometheus Operator (if not using managed monitoring)
    log_info "Installing Prometheus Operator..."
    helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
    helm repo update
    helm upgrade --install prometheus-operator prometheus-community/kube-prometheus-stack \
        --namespace monitoring \
        --create-namespace \
        --set prometheusOperator.createCustomResource=true \
        --timeout="$HELM_TIMEOUT"
    
    log_success "Operators installed successfully"
}

# Deploy StreamSense application
deploy_application() {
    log_info "Deploying StreamSense application..."
    
    cd "$PROJECT_ROOT"
    
    # Update Helm dependencies
    log_info "Updating Helm dependencies..."
    cd helm/streamsense
    helm dependency update
    
    # Deploy the application
    log_info "Installing/upgrading StreamSense..."
    helm upgrade --install streamsense . \
        --namespace streamsense \
        --create-namespace \
        --set environment="$ENVIRONMENT" \
        --set image.tag="${IMAGE_TAG:-latest}" \
        --timeout="$HELM_TIMEOUT" \
        --wait
    
    log_success "StreamSense application deployed successfully"
}

# Validate deployment
validate_deployment() {
    log_info "Validating deployment..."
    
    local start_time=$(date +%s)
    local timeout=$VALIDATION_TIMEOUT
    
    # Wait for all pods to be ready
    log_info "Waiting for pods to be ready..."
    kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=streamsense -n streamsense --timeout="${timeout}s"
    
    # Check service endpoints
    log_info "Checking service endpoints..."
    local services=(
        "api-gateway:8080"
        "eureka:8761"
        "chat-service:8081"
        "sentiment-service:8082"
        "video-service:8083"
        "graphql-service:8084"
        "recommendation-service:8085"
        "ml-engine:5000"
        "zipkin:9411"
    )
    
    for service_port in "${services[@]}"; do
        local service="${service_port%:*}"
        local port="${service_port#*:}"
        local full_service="streamsense-$service"
        
        log_info "Checking $full_service..."
        kubectl get service "$full_service" -n streamsense
    done
    
    # Health check endpoints
    log_info "Performing health checks..."
    local api_gateway_ip
    api_gateway_ip=$(kubectl get service streamsense-api-gateway -n streamsense -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
    
    if [ -n "$api_gateway_ip" ]; then
        log_info "API Gateway available at: http://$api_gateway_ip:8080"
        
        # Test health endpoint
        if curl -f "http://$api_gateway_ip:8080/actuator/health" >/dev/null 2>&1; then
            log_success "API Gateway health check passed"
        else
            log_warning "API Gateway health check failed"
        fi
    else
        log_warning "API Gateway external IP not yet available"
    fi
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    log_success "Deployment validation completed in ${duration}s"
}

# Generate deployment report
generate_report() {
    log_info "Generating deployment report..."
    
    local report_file="$PROJECT_ROOT/deployment-report-$(date +%Y%m%d-%H%M%S).md"
    
    cat > "$report_file" << EOF
# StreamSense Deployment Report

**Date:** $(date)
**Environment:** $ENVIRONMENT
**Cloud Provider:** $CLOUD_PROVIDER

## Infrastructure Status

### Kubernetes Cluster
\`\`\`
$(kubectl cluster-info)
\`\`\`

### Node Status
\`\`\`
$(kubectl get nodes)
\`\`\`

## Application Status

### Pods
\`\`\`
$(kubectl get pods -n streamsense)
\`\`\`

### Services
\`\`\`
$(kubectl get services -n streamsense)
\`\`\`

### Ingress
\`\`\`
$(kubectl get ingress -n streamsense)
\`\`\`

## Access Information

- **API Gateway:** http://$(kubectl get service streamsense-api-gateway -n streamsense -o jsonpath='{.status.loadBalancer.ingress[0].ip}'):8080
- **Eureka Dashboard:** http://$(kubectl get service streamsense-eureka -n streamsense -o jsonpath='{.status.loadBalancer.ingress[0].ip}'):8761
- **Zipkin Tracing:** http://$(kubectl get service streamsense-zipkin -n streamsense -o jsonpath='{.status.loadBalancer.ingress[0].ip}'):9411

## Next Steps

1. Configure DNS records for your domain
2. Set up SSL certificates
3. Configure monitoring alerts
4. Run load tests to validate performance

EOF

    log_success "Deployment report generated: $report_file"
}

# Cleanup function
cleanup() {
    log_info "Cleaning up temporary files..."
    rm -f "$PROJECT_ROOT"/infrastructure/*/tfplan
}

# Main deployment function
main() {
    log_info "Starting StreamSense enterprise deployment..."
    log_info "Environment: $ENVIRONMENT"
    log_info "Cloud Provider: $CLOUD_PROVIDER"
    
    trap cleanup EXIT
    
    check_prerequisites
    
    # Deploy infrastructure
    case "$CLOUD_PROVIDER" in
        aws)
            deploy_aws_eks
            ;;
        gcp)
            deploy_gcp_gke
            ;;
        both)
            deploy_aws_eks
            # Switch context for GCP deployment
            deploy_gcp_gke
            ;;
        *)
            log_error "Invalid cloud provider: $CLOUD_PROVIDER. Use 'aws', 'gcp', or 'both'"
            exit 1
            ;;
    esac
    
    # Install operators and deploy application
    install_operators
    deploy_application
    validate_deployment
    generate_report
    
    log_success "🚀 StreamSense enterprise deployment completed successfully!"
    log_info "View the deployment report for access information and next steps."
}

# Script entry point
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi