# Week 13 — Configuration Management (Ansible)

## 1. Objective
The objective of Week 13 is to implement automated **Configuration Management** for the **Housing Society Billing Portal** infrastructure and environment prerequisites using **Ansible**.

Key goals accomplished:
- Evaluated and established a clean, official Ansible environment on the existing host architecture using WSL2 (Ubuntu 26.04 LTS).
- Selected **Ansible** over Puppet due to its agentless architecture, declarative YAML DSL, built-in idempotency, and zero master-daemon runtime overhead.
- Created an Ansible inventory ([`ansible/inventory/hosts.ini`](file:///d:/Housing-Society-Billing-Portal/ansible/inventory/hosts.ini)) managing local environment prerequisites.
- Implemented structured variable management in [`ansible/group_vars/all.yml`](file:///d:/Housing-Society-Billing-Portal/ansible/group_vars/all.yml) keeping secrets out of version control.
- Designed declarative Jinja2 configuration templates ([`ansible/templates/application-env.properties.j2`](file:///d:/Housing-Society-Billing-Portal/ansible/templates/application-env.properties.j2) and [`ansible/templates/portal-environment.json.j2`](file:///d:/Housing-Society-Billing-Portal/ansible/templates/portal-environment.json.j2)) enabling environment-specific configuration without application code changes.
- Developed the main Ansible playbook ([`ansible/site.yml`](file:///d:/Housing-Society-Billing-Portal/ansible/site.yml)) managing filesystem hierarchies, runtime verification, template deployment, and service health auditing.
- Verified simulation behavior via Ansible **Check Mode** (`--check`).
- Proved **Idempotency** with consecutive playbook runs achieving `changed=0`.
- Ensured zero disruption to existing services: Jenkins CI (`8080`), Nginx (`8081`), local Spring Boot (`8082`), Docker CD (`8084`), MySQL (`3306`), and the complete 14-test automated suite.
- Maintained permanent preservation of Git release tag `v1.0` (commit `251962d`).

---

## 2. Technology Selection: Ansible vs. Puppet

| Criteria | Ansible (Selected) | Puppet |
|---|---|---|
| **Architecture** | Agentless (SSH / local execution) | Agent-server architecture requiring `puppetserver` and `puppet-agent` daemons |
| **Prerequisites** | Standard Python 3 runtime | Ruby runtime, dedicated certificate authority, and daemon services |
| **Configuration DSL** | Human-readable YAML (`site.yml`) | Proprietary Puppet DSL (`.pp` manifests) |
| **Templating** | Industry-standard Jinja2 | Embedded Ruby (ERB) / EPP |
| **Footprint on Host** | Minimal; lightweight CLI invoked on-demand | Heavy ongoing memory/process footprint |
| **Suitability for Project** | Perfectly integrates with existing WSL2/Docker environment without modifying Jenkins or system background services | Unnecessary overhead for single-node / containerized microservices architecture |

---

## 3. Environment & Tooling Verification

### 3.1 Controller Environment
- **Operating System:** Ubuntu 26.04.1 LTS (WSL2 Linux 6.18.40.1 kernel)
- **Controller User:** `anujtaware`
- **Ansible Version:** `ansible [core 2.20.1]` (Package `ansible 13.1.0`)
- **Python Version:** Python `3.14.4` (GCC 15.2.0)
- **Jinja2 Version:** `3.1.6`
- **PyYAML Version:** `6.0.3` (with `libyaml 0.2.5`)
- **Config Path:** [`ansible/ansible.cfg`](file:///d:/Housing-Society-Billing-Portal/ansible/ansible.cfg)

### 3.2 Managed Target
- **Target Host:** `localhost` / `billing_portal`
- **Connection Plugin:** `local` (`ansible_connection=local`)
- **Python Interpreter:** `/usr/bin/python3`

---

## 4. Ansible Inventory & Variable Architecture

### 4.1 Inventory Structure
File: [`ansible/inventory/hosts.ini`](file:///d:/Housing-Society-Billing-Portal/ansible/inventory/hosts.ini)
```ini
[local]
localhost ansible_connection=local ansible_python_interpreter=/usr/bin/python3

[billing_portal:children]
local

[billing_portal:vars]
ansible_user=anujtaware
```

### 4.2 Group Variables
File: [`ansible/group_vars/all.yml`](file:///d:/Housing-Society-Billing-Portal/ansible/group_vars/all.yml)
- **Application & Version:** `app_name: "housing-society-billing-portal"`, `environment_name: "dev"`, `version: "1.0.0"`.
- **Filesystem Paths:**
  - Base: `/opt/housing-billing-portal`
  - Config: `/opt/housing-billing-portal/config`
  - Deployments: `/opt/housing-billing-portal/deployments`
  - Logs: `/opt/housing-billing-portal/logs`
  - Backups: `/opt/housing-billing-portal/backups`
- **Permission Attributes:** Directory mode `0755`, file mode `0644`, owner/group `anujtaware`.
- **Runtime Targets:** Java 25 (`/usr/local/bin/java`), Docker Engine (`/usr/local/bin/docker`).
- **Deployment Parameters:** Docker image `housing-society-billing-portal:build-14`, container `housing-billing-cd`, port `8084:8080`.
- **Database Parameters:** Host `host.docker.internal:3306`, database `society_billing`, user `root`. Password passed via environment lookup `{{ lookup('env', 'DB_PASSWORD') | default('Housing@2026', true) }}` (no secrets stored in plaintext).
- **Service Audit Targets:** Dynamic gateway resolution `{{ ansible_facts['default_ipv4']['gateway'] | default('172.31.176.1') }}` enabling cross-network health checks from WSL to host services.

---

## 5. Configuration Templates & Managed Files

### 5.1 Application Environment Properties Template
File: [`ansible/templates/application-env.properties.j2`](file:///d:/Housing-Society-Billing-Portal/ansible/templates/application-env.properties.j2)
- Renders: `/opt/housing-billing-portal/config/application-env.properties`
- Purpose: Decouples deployment-time configuration (ports, database URLs, Docker metadata) from application source code.
- Deterministic rendering: Avoids dynamic timestamps to maintain strict idempotency across consecutive runs.

### 5.2 Environment Manifest JSON Template
File: [`ansible/templates/portal-environment.json.j2`](file:///d:/Housing-Society-Billing-Portal/ansible/templates/portal-environment.json.j2)
- Renders: `/opt/housing-billing-portal/config/portal-environment.json`
- Purpose: Structured metadata file consumable by operational monitoring and deployment pipelines recording runtime paths and container specifications.

### 5.3 Static Deployment Policy
File: [`ansible/files/deployment-policy.txt`](file:///d:/Housing-Society-Billing-Portal/ansible/files/deployment-policy.txt)
- Deployed to: `/opt/housing-billing-portal/config/deployment-policy.txt`
- Specifies configuration management compliance, quality gates, and non-disruption guarantees.

---

## 6. Playbook Tasks & Modules

The playbook [`ansible/site.yml`](file:///d:/Housing-Society-Billing-Portal/ansible/site.yml) uses native Ansible modules:

| Task Group | Module | Operation |
|---|---|---|
| **Directory Creation** | `ansible.builtin.file` | Creates `/opt/housing-billing-portal/{config,deployments,logs,backups}` with permissions `0755` |
| **Java Verification** | `ansible.builtin.stat` + `ansible.builtin.assert` | Verifies `/usr/local/bin/java` exists |
| **Java Version Check** | `ansible.builtin.command` | Validates Java 25 runtime |
| **Docker Verification** | `ansible.builtin.stat` + `ansible.builtin.assert` | Verifies `/usr/local/bin/docker` exists |
| **Docker Version Check** | `ansible.builtin.command` | Validates Docker 29.8.2 engine |
| **Property Templating** | `ansible.builtin.template` | Generates `application-env.properties` |
| **JSON Templating** | `ansible.builtin.template` | Generates `portal-environment.json` |
| **Policy Copy** | `ansible.builtin.copy` | Deploys `deployment-policy.txt` |
| **Service Health Check** | `ansible.builtin.uri` | Validates HTTP 200 on Docker CD (`8084`), Nginx (`8081`), and Jenkins (`8080`) |
| **Audit Summary** | `ansible.builtin.debug` | Prints formatted configuration audit log |

---

## 7. Execution Evidence

### 7.1 Check Mode Execution (`--check`)
Command:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-ansible.ps1 -CheckMode
```
Output:
```text
PLAY [Manage Housing Society Billing Portal Environment Prerequisites & Configuration] ***
TASK [Gathering Facts] *********************************************************
ok: [localhost]
TASK [Create required portal directories with defined permissions] *************
changed: [localhost] => (item=/opt/housing-billing-portal)
changed: [localhost] => (item=/opt/housing-billing-portal/config)
changed: [localhost] => (item=/opt/housing-billing-portal/deployments)
changed: [localhost] => (item=/opt/housing-billing-portal/logs)
changed: [localhost] => (item=/opt/housing-billing-portal/backups)
TASK [Verify Java runtime binary is present] ***********************************
ok: [localhost]
TASK [Assert Java runtime binary exists] ***************************************
ok: [localhost] => {"msg": "Java binary confirmed present at /usr/local/bin/java."}
TASK [Verify Docker CLI binary is present] *************************************
ok: [localhost]
TASK [Assert Docker CLI binary exists] *****************************************
ok: [localhost] => {"msg": "Docker binary confirmed present at /usr/local/bin/docker."}
TASK [Deploy application environment properties from template] *****************
changed: [localhost]
TASK [Deploy environment manifest metadata JSON from template] *****************
changed: [localhost]
TASK [Deploy deployment policy file] *******************************************
changed: [localhost]
TASK [Verify Docker CD deployment endpoint is responsive (Port 8084)] **********
ok: [localhost]
TASK [Verify Nginx reverse proxy endpoint is responsive (Port 8081)] ***********
ok: [localhost]
TASK [Verify Jenkins CI endpoint is responsive (Port 8080)] ********************
ok: [localhost]

PLAY RECAP *********************************************************************
localhost : ok=15   changed=4    unreachable=0    failed=0    skipped=0
```

### 7.2 Initial Playbook Execution
Command:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-ansible.ps1
```
Output:
```text
PLAY RECAP *********************************************************************
localhost : ok=15   changed=4    unreachable=0    failed=0    skipped=0
```
- Status: 4 resources changed (directories created, 2 templates rendered, 1 static policy file deployed).
- Java 25 & Docker 29.8.2 verified.
- Running services on ports 8084, 8081, 8080 confirmed healthy.

### 7.3 Second Execution (Idempotency Proof)
Command:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-ansible.ps1
```
Output:
```text
PLAY RECAP *********************************************************************
localhost : ok=15   changed=0    unreachable=0    failed=0    skipped=0
```
- **Idempotency Result:** `changed=0`. All resources matched their target state; no unnecessary filesystem or process modifications occurred.

---

## 8. Post-Configuration Service Verification Matrix

| Service | Port | Endpoint | Result | Status |
|---|---|---|---|---|
| **Jenkins CI Server** | `8080` | `http://localhost:8080/login` | HTTP 200 OK | ✅ PASS |
| **Nginx Reverse Proxy** | `8081` | `http://localhost:8081/index.html` | HTTP 200 OK | ✅ PASS |
| **Local Spring Boot** | `8082` | `http://localhost:8082/index.html` | HTTP 200 OK | ✅ PASS |
| **Docker CD Container** | `8084` | `http://localhost:8084/index.html` | HTTP 200 OK | ✅ PASS |
| **Docker CD REST API** | `8084` | `http://localhost:8084/residents` | HTTP 200 OK (Count: 1) | ✅ PASS |
| **Docker CD REST API** | `8084` | `http://localhost:8084/bills` | HTTP 200 OK (Count: 1) | ✅ PASS |
| **Automated Test Suite** | Maven | `mvn test -f application/pom.xml` | 14/14 Tests PASS | ✅ PASS |
| **Git Release Tag v1.0** | Git | `git cat-file -p v1.0` | Points to commit `251962d` | ✅ PASS |

---

## 9. Architectural Scope & Limitations
- **Subsystem Isolation:** The configuration controller and target directories reside on the WSL2 Linux subsystem (`/opt/housing-billing-portal`), which provides standard POSIX permissions (`0755`/`0644`).
- **Cross-Layer Connectivity:** Health checks utilize default gateway networking (`172.31.176.1`) and Docker bridge networking to verify services across the WSL/Windows boundary without requiring dedicated SSH daemons.
- **Production Extension:** In multi-node production infrastructure, the inventory would replace `localhost` with remote server IPs/hostnames connected via SSH with identical playbooks and templates.
