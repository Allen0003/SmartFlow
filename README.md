# 🛡️ SmartFlow: Enterprise Event-Driven AI Agent & Financial Automation Platform

## 🎯 Overview
SmartFlow is a high-concurrency, event-driven, multi-modal AI Agent and financial automation platform designed for modern enterprises and financial institutions. 

Unlike typical synchronous, blocking AI chatbots built on Python, SmartFlow leverages Java 21 Virtual Threads, Apache Kafka, and the LangChain4j framework to treat high-latency, unpredictable LLM calls as asynchronous nodes within a distributed microservices architecture. By utilizing the Transactional Outbox Pattern and Resilience4j, the platform guarantees financial-grade eventual consistency, high availability, and system resilience under heavy concurrent loads.

---

## ✨ Enterprise-Grade Features

### 1. Asynchronous Event-Driven AI Orchestration
* **Non-Blocking High Concurrency:** Powered by Java 21 Virtual Threads (Project Loom) to handle prolonged LLM I/O wait times with minimal operating system thread overhead, avoiding thread pool exhaustion.
* **Eventual Consistency:** Implements the Transactional Outbox Pattern. When an AI task is initiated, core business data and outbox events are committed atomically within the same local database transaction, then dispatched asynchronously to Kafka to eliminate data loss.
* **Dynamic Tool Calling:** Uses LangChain4j's `@Tool` annotations to seamlessly expose existing enterprise Java services (e.g., balance inquiries, account validation) as functional tools for autonomous AI Agent decision-making.

### 2. Financial-Grade Resilience & Gateway Management
* **Smart Circuit Breaking & Fallbacks:** Integrated with Resilience4j. When external LLM APIs hit rate limits (HTTP 429) or timeouts, the gateway automatically triggers a circuit breaker and fails over within milliseconds to a local private model (e.g., Llama 3 via Ollama).
* **Distributed Chat Memory Sharing:** Features a customized `ChatMemoryProvider` that backs up Agent conversation contexts and session states into a Redis Cluster. This prevents memory loss caused by load balancing shifts across multiple Kubernetes pods.

### 3. Enterprise Governance & Security
* **Dynamic PII Masking:** Built-in high-performance filters inside the Gateway Service mask sensitive personal identifiable information (e.g., SSN, credit card numbers, phone numbers) before prompts reach external LLM providers, and reverse-unmask them upon response.
* **Decision Trace Auditing:** Intercepts the Agent’s complete Chain of Thought (CoT), tool invocations, and precise token usage asynchronously, streaming audit logs straight into a dedicated Kafka topic (`agent-audit`) for compliance tracking.

### 4. Cloud-Native & LLMOps Readiness
* **K8s Horizontal Pod Autoscaling (HPA):** Configured with custom K8s metrics to horizontally scale `Smart-Agent-Worker` pods dynamically based on Kafka consumer lag and real-time API latency.
* **Native Compilation:** Supports GraalVM Native Image compilation, reducing Java application startup times to sub-100ms and cutting memory footprint by 70% to meet dynamic serverless auto-scaling demands.

---

## 🛠️ Tech Stack

* **Language:** Java 21 (Virtual Threads Enabled)
* **Framework:** Spring Boot 3.3.x, Spring Cloud Gateway, LangChain4j 0.3x
* **Middleware:** Apache Kafka, Redis Cluster
* **Database:** PostgreSQL (Outbox configuration)
* **Resilience:** Resilience4j (Circuit Breaker, Retry, RateLimiter)
* **Containerization & Orchestration:** Docker, Kubernetes (CKAD-compliant manifests)
* **Observability:** Prometheus, Grafana

---

## 🚀 Project Structure

```text
smartflow/
├── smart-gateway-service/   # API Gateway: Auth, PII masking, and security edge filters
├── smart-core-service/      # Core Business: Handles incoming requests and writes Outbox events
├── smart-agent-worker/      # AI Core: Consumes Kafka messages, drives LangChain4j Agents & Tools
│   ├── src/main/java/com/smartflow/agent/
│   │   ├── config/          # LangChain4j & Redis Memory configurations
│   │   ├── consumer/        # Kafka asynchronous event consumers
│   │   ├── agent/           # LangChain4j AiServices declarative definitions
│   │   └── tools/           # Java business toolkits bound to the AI Agent
└── k8s/                     # Kubernetes deployment manifests (Deployment, HPA, ConfigMap)
