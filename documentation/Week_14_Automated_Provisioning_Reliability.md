# Week 14 — Automated Provisioning and Reliability

## 1. Objective
The objective of Week 14 is to achieve **Automated Provisioning and Reliability** for the **Housing Society Billing Portal** infrastructure by extending the existing Ansible configuration management baseline.

Key goals accomplished:
- Extended the Ansible automation suite with a dedicated provisioning and self-healing reliability playbook ([`ansible/provision.yml`](file:///d:/Housing-Society-Billing-Portal/ansible/provision.yml)).
- Built comprehensive infrastructure reliability health checks across all operational layers: Jenkins CI (`8080`), Nginx Reverse Proxy (`8081`), Local Spring Boot (`8082`), Docker CD Deployment (`8084`), MySQL connectivity, and REST APIs (`/residents`, `/bills`).
- Implemented an automated self-healing remediation mechanism: detects missing, stopped, or unhealthy containers and safely recreates/starts the Docker CD container without manual intervention.
- Verified simulation safety using Ansible **Check Mode** (`--check`).
- Proved **Idempotency**: consecutive runs without failure exhibit zero changes (`changed=0`).
- Demonstrated real-world **Failure & Self-Healing Recovery**: deliberately halted the Docker CD container, executed the reliability playbook, and verified automated detection, cleanup, container redeployment, health polling, and recovery.
- Re-verified post-recovery idempotency (`changed=0`).
- Verified all 14 automated tests (9 unit/integration tests + 5 Selenium UI tests) passing 100%.
- Preserved Git release tag `v1.0` permanently pointing to commit `251962d`.

---

## 2. Infrastructure & Architectural Baseline

| Component | Host / Port | Role | Verification Method |
|---|---|---|---|
| **Jenkins CI Server** | `http://localhost:8080` | Continuous Integration & Declarative Pipeline | `ansible.builtin.uri` (`/login` ➔ HTTP 200) |
| **Nginx Reverse Proxy** | `http://localhost:8081` | Week 8 Production-grade Web Reverse Proxy | `ansible.builtin.uri` (`/index.html` ➔ HTTP 200) |
| **Local Spring Boot** | `http://localhost:8082` | Backend Application Server | `ansible.builtin.uri` (`/index.html` ➔ HTTP 200) |
| **Docker CD Deployment**| `http://localhost:8084` | Week 12 Containerized CD (`housing-billing-cd`) | `ansible.builtin.uri` (`/index.html` ➔ HTTP 200) |
| **Database Server** | `localhost:3306` | MySQL 8.0 Database (`housing_billing`) | Docker container networking via `host.docker.internal:3306` |
| **REST API /residents** | Port 8084 | Resident Entity CRUD Operations | `ansible.builtin.uri` (HTTP 200, parses JSON payload) |
| **REST API /bills** | Port 8084 | Bill Entity & Receipt Operations | `ansible.builtin.uri` (HTTP 200, parses JSON payload) |
| **Ansible Controller** | WSL2 (Ubuntu 26.04) | Automated Provisioning & Reliability Engine | Ansible Core `2.20.1`, Python `3.14.4` |

---

## 3. Provisioning & Reliability Playbook Design

### 3.1 Playbook Architecture ([`ansible/provision.yml`](file:///d:/Housing-Society-Billing-Portal/ansible/provision.yml))
The playbook orchestrates automated environment provisioning, prerequisite assertion, self-healing container remediation, and deep health auditing:

```
+---------------------------------------------------------------------------------------------------+
|                           ansible/provision.yml (Target: localhost/WSL2)                          |
+---------------------------------------------------------------------------------------------------+
|  1. Filesystem Provisioning      (Ensures /opt/housing-billing-portal/{config,deployments,logs})   |
|  2. Runtime Prerequisite Checks  (Asserts Java 25 & Docker Engine 29.8.2 binaries & versions)     |
|  3. Configuration Provisioning   (Templates application-env.properties & portal-environment.json)|
|  4. Container State Inspection   (docker inspect -f '{{.State.Running}}' housing-billing-cd)      |
|  5. Conditional Remediation      (WHEN not running: rm stale container -> docker run -> wait 8084)|
|  6. Infrastructure Health Checks (URIs: Docker 8084, /residents, /bills, 8082, 8081, 8080)        |
|  7. Reliability Audit Report     (Emits structured operational status summary)                    |
+---------------------------------------------------------------------------------------------------+
```

### 3.2 Self-Healing Remediation Logic
```yaml
    - name: Inspect Docker CD container status
      ansible.builtin.command: "{{ docker_binary_path }} inspect -f '{{ '{{' }}.State.Running{{ '}}' }}' {{ docker_container_name }}"
      register: container_inspect
      failed_when: false
      changed_when: false
      check_mode: false

    - name: Set container operational facts
      ansible.builtin.set_fact:
        container_exists: "{{ container_inspect.rc == 0 }}"
        container_is_running: "{{ container_inspect.rc == 0 and 'true' in container_inspect.stdout.lower() }}"

    - name: Execute automated container recovery when container is stopped or missing
      when: not container_is_running
      block:
        - name: Remove stopped/stale container if present
          ansible.builtin.command: "{{ docker_binary_path }} rm -f {{ docker_container_name }}"
          when: container_exists
          changed_when: true

        - name: Launch recovered Docker CD container
          ansible.builtin.command: >-
            {{ docker_binary_path }} run -d
            --name {{ docker_container_name }}
            -p {{ docker_host_port }}:{{ docker_container_port }}
            -e DB_HOST={{ db_host }}
            -e DB_PORT={{ db_port }}
            -e DB_NAME={{ db_name }}
            -e DB_USER={{ db_user }}
            -e DB_PASSWORD={{ db_password }}
            {{ docker_image }}
          changed_when: true

        - name: Wait for recovered container application endpoint to become ready
          ansible.builtin.uri:
            url: "{{ docker_cd_url }}"
            status_code: 200
          register: recovery_health_poll
          until: recovery_health_poll.status == 200
          retries: 30
          delay: 2
```
- **Safety Guarantee:** When the container is healthy and running, the remediation block is entirely skipped. Services are never blindly restarted.
- **Healing Guarantee:** If the container exits or is stopped, Ansible detects the failure state, cleans up the stopped container, initiates a fresh instance with identical parameters, and actively polls the endpoint until HTTP 200 is confirmed.

---

## 4. Execution Evidence & Verification

### 4.1 Check Mode Simulation (`--check`)
Command:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-ansible.ps1 -Playbook provision.yml -CheckMode
```
Recap:
```text
PLAY RECAP *********************************************************************
localhost                  : ok=21   changed=0    unreachable=0    failed=0    skipped=3
```
- Directories and configuration templates verified matching target state.
- Container inspected: `Exists=True, Running=True` ➔ Remediation tasks skipped.
- All 6 reliability health check endpoints verified responding HTTP 200.

### 4.2 Initial Provisioning Execution
Command:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-ansible.ps1 -Playbook provision.yml
```
Recap:
```text
PLAY RECAP *********************************************************************
localhost                  : ok=21   changed=0    unreachable=0    failed=0    skipped=3
```
- Confirmed stable, zero-change baseline on healthy infrastructure.

### 4.3 Second Execution (Pre-Failure Idempotency)
Command:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-ansible.ps1 -Playbook provision.yml
```
Recap:
```text
PLAY RECAP *********************************************************************
localhost                  : ok=21   changed=0    unreachable=0    failed=0    skipped=3
```
- **Idempotency Result:** `changed=0`. No redundant filesystem actions, zero process interruptions.

---

## 5. Failure & Self-Healing Recovery Demonstration

To demonstrate reliability and self-healing automation, a failure was deliberately injected:

1. **Failure Injection:**
   ```powershell
   docker stop housing-billing-cd
   ```
2. **Failure Verification:**
   - Attempted HTTP query against `http://localhost:8084/index.html`.
   - Result: `Connection Refused` / Port 8084 down.
3. **Automated Remediation Execution:**
   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts/run-ansible.ps1 -Playbook provision.yml
   ```
   Console Output Highlights:
   ```text
   TASK [Display detected container status] ***************************************
   ok: [localhost] => {"msg": "Container 'housing-billing-cd' status: Exists=True, Running=False"}

   TASK [Remove stopped/stale container if present] *******************************
   changed: [localhost]

   TASK [Launch recovered Docker CD container] ************************************
   changed: [localhost]

   TASK [Wait for recovered container application endpoint to become ready] *******
   FAILED - RETRYING: [localhost]: ... (27 retries left).
   ok: [localhost]

   TASK [Reliability Check - Verify Docker CD deployment endpoint (Port 8084)] ****
   ok: [localhost]

   TASK [Reliability Check - Verify Docker CD /residents database REST API] *******
   ok: [localhost]

   TASK [Reliability Check - Verify Docker CD /bills database REST API] ***********
   ok: [localhost]

   PLAY RECAP *********************************************************************
   localhost                  : ok=24   changed=4    unreachable=0    failed=0    skipped=0
   ```
4. **Post-Recovery Health Verification:**
   - `http://localhost:8084/index.html` ➔ `HTTP 200 OK`
   - `http://localhost:8084/residents` ➔ `HTTP 200 OK` (Count: 1)
   - `http://localhost:8084/bills` ➔ `HTTP 200 OK` (Count: 1)
   - Container `housing-billing-cd` status: `Up 24 seconds`
5. **Post-Recovery Idempotency Proof:**
   - Re-executed `powershell -ExecutionPolicy Bypass -File scripts/run-ansible.ps1 -Playbook provision.yml`.
   - Result:
     ```text
     PLAY RECAP *********************************************************************
     localhost                  : ok=21   changed=0    unreachable=0    failed=0    skipped=3
     ```
   - Confirmed: Following recovery, subsequent runs return strictly `changed=0`.

---

## 6. Full Service Verification Matrix

| Verification Target | Expected | Observed | Status |
|---|---|---|---|
| **Jenkins CI Server** | Port `8080` (`/login`) HTTP 200 | HTTP 200 OK | ✅ PASS |
| **Nginx Reverse Proxy** | Port `8081` (`/index.html`) HTTP 200 | HTTP 200 OK | ✅ PASS |
| **Local Spring Boot** | Port `8082` (`/index.html`) HTTP 200 | HTTP 200 OK | ✅ PASS |
| **Docker CD Application** | Port `8084` (`/index.html`) HTTP 200 | HTTP 200 OK | ✅ PASS |
| **REST API /residents** | Port `8084` (`/residents`) HTTP 200 | HTTP 200 OK (Count: 1) | ✅ PASS |
| **REST API /bills** | Port `8084` (`/bills`) HTTP 200 | HTTP 200 OK (Count: 1) | ✅ PASS |
| **MySQL Connectivity** | Active DB connection | Queries executing, records returned | ✅ PASS |
| **Docker Container Status** | `housing-billing-cd` running | Running (Port `8084:8080`) | ✅ PASS |
| **Automated Tests** | 14/14 tests pass | 14/14 PASS (0 failures, 0 errors) | ✅ PASS |
| **Self-Healing Remediation** | Automatic recovery on container stop | Tested, verified, recovered | ✅ PASS |
| **Playbook Idempotency** | Consecutive runs `changed=0` | `changed=0` verified | ✅ PASS |
| **Git Release Tag v1.0** | Commit `251962d` | `251962dc90e85b789d8676cc34f6587e6109b601` | ✅ PASS |

---

## 7. Repository & Git Status
- **Files Created / Modified:**
  - [`ansible/provision.yml`](file:///d:/Housing-Society-Billing-Portal/ansible/provision.yml) (Automated provisioning & reliability playbook)
  - [`ansible/group_vars/all.yml`](file:///d:/Housing-Society-Billing-Portal/ansible/group_vars/all.yml) (Updated with local Spring Boot and REST API endpoints, corrected DB name)
  - [`scripts/run-ansible.ps1`](file:///d:/Housing-Society-Billing-Portal/scripts/run-ansible.ps1) (Updated to support `-Playbook` parameter defaulting to `provision.yml`)
  - [`documentation/Week_14_Automated_Provisioning_Reliability.md`](file:///d:/Housing-Society-Billing-Portal/documentation/Week_14_Automated_Provisioning_Reliability.md) (Comprehensive Week 14 documentation)
- **Git Branch:** `feature/week14-reliability`
- **Git Tag `v1.0`:** Untouched at commit `251962dc90e85b789d8676cc34f6587e6109b601`.
- **Working Tree:** Clean.
