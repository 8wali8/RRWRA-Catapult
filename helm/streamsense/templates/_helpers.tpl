{{/*
Expand the name of the chart.
*/}}
{{- define "streamsense.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "streamsense.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "streamsense.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "streamsense.labels" -}}
helm.sh/chart: {{ include "streamsense.chart" . }}
{{ include "streamsense.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "streamsense.selectorLabels" -}}
app.kubernetes.io/name: {{ include "streamsense.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "streamsense.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "streamsense.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Create microservice deployment template
*/}}
{{- define "streamsense.microservice.deployment" -}}
{{- $service := .service }}
{{- $values := .values }}
{{- with .context }}
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "streamsense.fullname" . }}-{{ $service.name }}
  namespace: {{ .Values.namespace.name }}
  labels:
    {{- include "streamsense.labels" . | nindent 4 }}
    app.kubernetes.io/component: {{ $service.name }}
    tier: {{ $service.tier | default "microservice" }}
spec:
  replicas: {{ $service.replicaCount }}
  selector:
    matchLabels:
      {{- include "streamsense.selectorLabels" . | nindent 6 }}
      app.kubernetes.io/component: {{ $service.name }}
  template:
    metadata:
      labels:
        {{- include "streamsense.selectorLabels" . | nindent 8 }}
        app.kubernetes.io/component: {{ $service.name }}
        tier: {{ $service.tier | default "microservice" }}
    spec:
      serviceAccountName: {{ include "streamsense.serviceAccountName" . }}
      containers:
      - name: {{ $service.name }}
        image: "{{ $service.image.repository }}:{{ $service.image.tag | default .Chart.AppVersion }}"
        imagePullPolicy: {{ .Values.image.pullPolicy }}
        ports:
        - name: http
          containerPort: {{ $service.service.port }}
          protocol: TCP
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "k8s"
        - name: EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE
          value: "http://{{ include "streamsense.fullname" . }}-eureka.{{ .Values.namespace.name }}.svc.cluster.local:8761/eureka/"
        {{- if $service.env }}
        {{- toYaml $service.env | nindent 8 }}
        {{- end }}
        resources:
          {{- toYaml $service.resources | nindent 10 }}
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: http
          initialDelaySeconds: 120
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: http
          initialDelaySeconds: 30
          periodSeconds: 10
      {{- with .Values.nodeSelector }}
      nodeSelector:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with .Values.affinity }}
      affinity:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with .Values.tolerations }}
      tolerations:
        {{- toYaml . | nindent 8 }}
      {{- end }}
{{- end }}
{{- end }}

{{/*
Create microservice service template
*/}}
{{- define "streamsense.microservice.service" -}}
{{- $service := .service }}
{{- with .context }}
apiVersion: v1
kind: Service
metadata:
  name: {{ include "streamsense.fullname" . }}-{{ $service.name }}
  namespace: {{ .Values.namespace.name }}
  labels:
    {{- include "streamsense.labels" . | nindent 4 }}
    app.kubernetes.io/component: {{ $service.name }}
spec:
  type: {{ $service.service.type }}
  ports:
    - port: {{ $service.service.port }}
      targetPort: http
      protocol: TCP
      name: http
  selector:
    {{- include "streamsense.selectorLabels" . | nindent 4 }}
    app.kubernetes.io/component: {{ $service.name }}
{{- end }}
{{- end }}

{{/*
Generate Kafka bootstrap servers
*/}}
{{- define "streamsense.kafka.bootstrapServers" -}}
{{- if .Values.kafka.enabled }}
{{- printf "%s-kafka.%s.svc.cluster.local:9092" (include "streamsense.fullname" .) .Values.namespace.name }}
{{- else }}
{{- .Values.externalKafka.bootstrapServers }}
{{- end }}
{{- end }}

{{/*
Generate PostgreSQL connection string
*/}}
{{- define "streamsense.postgresql.connectionString" -}}
{{- if .Values.postgresql.enabled }}
{{- printf "jdbc:postgresql://%s-postgresql.%s.svc.cluster.local:5432/%s" (include "streamsense.fullname" .) .Values.namespace.name .Values.postgresql.auth.database }}
{{- else }}
{{- .Values.externalPostgresql.connectionString }}
{{- end }}
{{- end }}

{{/*
Generate Redis host
*/}}
{{- define "streamsense.redis.host" -}}
{{- if .Values.redis.enabled }}
{{- printf "%s-redis-master.%s.svc.cluster.local" (include "streamsense.fullname" .) .Values.namespace.name }}
{{- else }}
{{- .Values.externalRedis.host }}
{{- end }}
{{- end }}